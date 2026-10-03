package com.jiyibi.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiyibi.app.core.common.TimeRange
import com.jiyibi.app.core.common.TransactionDayGroup
import com.jiyibi.app.core.common.TransactionRowUi
import com.jiyibi.app.core.common.buildDayGroups
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.BudgetRepository
import com.jiyibi.app.core.domain.repository.CategoryRepository
import com.jiyibi.app.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一天的毫秒数，用于按日聚合与单日范围查询 */
private const val MILLIS_PER_DAY = 86_400_000L

/** 首页「最近交易」列表最多展示的条数 */
private const val RECENT_LIMIT = 10

/**
 * 当月日历热力图单元格。
 *
 * 标注 [Immutable]：Compose 编译器对含有 `List` 字段的类会判定为 unstable，
 * 进而使接收它的 Composable 无法 skip。本类字段全部为基本类型、且集合从不原地修改，
 * 故显式声明不可变，让相关 Composable 可被跳过。
 *
 * @property day    当月几号（1~31）
 * @property date   当天 0 点 epoch millis（点击时作为查询区间的起点）
 * @property amount 当天支出总额（分）
 * @property level  颜色深浅档位，0~4：0 无支出，1~4 由低到高
 */
@Immutable
data class HeatmapCell(
    val day: Int,
    val date: Long,
    val amount: Long,
    val level: Int,
)

/**
 * 热力图渲染数据：单元格列表 + 当月支出峰值日。
 *
 * 把 `List<HeatmapCell>` 包进一个 [Immutable] 类，是为了让 [HomeScreen] 的
 * `HeatmapCard` 参数重新变为 stable（裸 `List` 参数是 unstable，会导致无法 skip）。
 *
 * @property cells   当月每一天的单元格
 * @property peakDay 当月支出最强的一天 (day, amount)，null 表示当月无支出
 */
@Immutable
data class HeatmapUiState(
    val cells: List<HeatmapCell> = emptyList(),
    val peakDay: Pair<Int, Long>? = null,
)

/**
 * 热力图弹窗详情：与主 [HomeUiState] 完全解耦的局部状态。
 *
 * 点击热力图格子只会让这个流变化，而不会重建主 [HomeUiState]，
 * 从而避免「点一下格子 → 整个首页重组」。
 *
 * @property selectedDay     当前选中的天（0 点 timestamp），null 表示未选中
 * @property dayTransactions 选中日的交易明细
 */
@Immutable
data class HeatmapDetail(
    val selectedDay: Long? = null,
    val dayTransactions: List<Transaction> = emptyList(),
)

/**
 * 首页 UI 状态：只包含列表主体渲染所需的字段。
 *
 * 热力图弹窗详情（[HeatmapDetail]）已拆出，不在此结构内。
 *
 * @property todayExpense       选定日期范围支出（分）；保留原字段名但语义已改为「选定范围支出」
 * @property monthExpense      本月支出（分），始终基于本月
 * @property monthIncome       本月收入（分），始终基于本月
 * @property monthBudget       月度预算额度（分），0 表示未设置
 * @property recentGroups      最近交易，**已按天分组**（最多 [RECENT_LIMIT] 条原始交易）
 * @property selectedDateRange 当前选定的日期范围 [start, end)
 * @property monthHeatmap      当月日历热力图渲染数据（单元格 + 峰值日）
 * @property isLoading         是否加载中
 */
@Immutable
data class HomeUiState(
    val todayExpense: Long = 0,
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val monthBudget: Long = 0,
    val recentGroups: List<TransactionDayGroup> = emptyList(),
    val selectedDateRange: Pair<Long, Long> = TimeRange.today(),
    val monthHeatmap: HeatmapUiState = HeatmapUiState(),
    val isLoading: Boolean = false,
)

/** 本月收支与预算额度：合并为单个流源，避免外层 combine 超过 5 个 typed 参数上限 */
private data class MonthSummary(
    val expense: Long = 0,
    val income: Long = 0,
    val budget: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    /** 当前选定的日期范围 [start, end)，默认今日 */
    private val selectedDateRange: MutableStateFlow<Pair<Long, Long>> =
        MutableStateFlow(TimeRange.today())

    /** 当前选中的热力图单元格对应的天（0 点 epoch millis），null 表示未选中 */
    private val selectedHeatmapDay: MutableStateFlow<Long?> = MutableStateFlow(null)

    /** 本月范围（始终固定为 TimeRange.thisMonth()，与用户选择的日期 Pill 无关） */
    private val month = TimeRange.thisMonth()

    // ---------------------------------------------------------------------
    // 数据源按「依赖关系」分层：与日期无关的留在外层只订阅一次，
    // 只有真正依赖所选日期的才放进 flatMapLatest，切换 Pill 时只重建一条订阅。
    // ---------------------------------------------------------------------

    /**
     * 第 1 层（与日期无关）：最近交易列表，关联账户名与分类。
     *
     * 只依赖交易 / 账户 / 分类三张表。账户与分类表几乎不变，
     * 因此不随日期 Pill 切换而重新查询与重建 map。
     */
    private val recentItems: StateFlow<List<TransactionRowUi>> = combine(
        transactionRepository.observeRecent(RECENT_LIMIT),
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
    ) { recent, accounts, categories ->
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        recent.map { tx ->
            TransactionRowUi(
                tx = tx,
                category = tx.categoryId?.let { categoryMap[it] },
                accountName = accountMap[tx.accountId]?.name ?: "未知账户",
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * 第 1.5 层：把最近交易按「天」分组，并算好每天的出/入合计。
     *
     * 分组只在这里做一次，而不是在 LazyColumn 的 item lambda 里——
     * 后者会在每次该 item 重组时重复聚合并产生中间集合。
     */
    private val recentGroups: StateFlow<List<TransactionDayGroup>> = recentItems
        .map { buildDayGroups(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 第 2 层（只依赖所选日期）：范围内支出合计。切换 Pill 时仅此一条订阅被重建。 */
    private val rangeExpense: StateFlow<Long> = selectedDateRange
        .flatMapLatest { (start, end) -> transactionRepository.observeTotalExpense(start, end) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0L,
        )

    /** 第 3 层（与日期无关）：本月收入 / 支出 / 月度总预算额度 */
    private val monthSummary: StateFlow<MonthSummary> = combine(
        transactionRepository.observeTotalExpense(month.first, month.second),
        transactionRepository.observeTotalIncome(month.first, month.second),
        budgetRepository.observeActive(System.currentTimeMillis()),
    ) { expense, income, budgets ->
        MonthSummary(
            expense = expense,
            income = income,
            // categoryId == null 表示月度总预算
            budget = budgets.firstOrNull { it.categoryId == null }?.amountLimit ?: 0L,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonthSummary(),
    )

    /**
     * 第 4 层（与日期无关）：当月日历热力图原始单元格。
     * 按日聚合 EXPENSE 类型交易，并按强度划分 5 档（0~4）。
     */
    private val monthHeatmapCells: StateFlow<List<HeatmapCell>> = flowOf(month)
        .flatMapLatest { (start, end) ->
            transactionRepository.observeRange(start, end).map { txs ->
                // 按日聚合：以「当天 0 点 timestamp」为 key（与下方循环中 dayStart 一致，避免时区错位）
                val cal = Calendar.getInstance()
                val byDay = txs.groupBy { tx ->
                    cal.timeInMillis = tx.date
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }.mapValues { (_, list) ->
                    list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                }
                val maxAmount = byDay.values.maxOrNull() ?: 1L
                val cells = mutableListOf<HeatmapCell>()
                cal.timeInMillis = start
                // end 为下月初 0 点（exclusive），用 < 避免把下月 1 号也画进当月热力图
                while (cal.timeInMillis < end) {
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    val dayStart = cal.timeInMillis
                    val amount = byDay[dayStart] ?: 0L
                    val level = when {
                        amount == 0L -> 0
                        amount < maxAmount * 0.25 -> 1
                        amount < maxAmount * 0.5 -> 2
                        amount < maxAmount * 0.75 -> 3
                        else -> 4
                    }
                    cells.add(HeatmapCell(day, dayStart, amount, level))
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                cells
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * 第 5 层：热力图渲染数据 = 单元格 + 峰值日。
     *
     * 峰值日在此处随单元格一起算好，避免原先写在 LazyColumn 的 item lambda 内、
     * 每次该 item 重组都重算一遍并产生中间集合。
     */
    private val heatmapUi: StateFlow<HeatmapUiState> = monthHeatmapCells
        .map { cells ->
            HeatmapUiState(
                cells = cells,
                peakDay = cells
                    .filter { it.amount > 0 }
                    .maxByOrNull { it.amount }
                    ?.let { it.day to it.amount },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HeatmapUiState(),
        )

    /** 选中日的交易明细：监听 [dayStart, dayStart+1day) 区间；未选中时为空列表 */
    private val dayTransactions: StateFlow<List<Transaction>> = selectedHeatmapDay
        .flatMapLatest { dayStart ->
            if (dayStart == null) {
                flowOf(emptyList())
            } else {
                transactionRepository.observeRange(dayStart, dayStart + MILLIS_PER_DAY)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * 热力图弹窗详情：独立于主 [uiState] 单独暴露。
     *
     * Screen 侧只把它交给 BottomSheet 消费，因此点击热力图格子不会
     * 触发首页主体（Hero 卡 / 收支卡 / 热力图 / 最近交易）重组。
     */
    val heatmapDetail: StateFlow<HeatmapDetail> = combine(
        selectedHeatmapDay,
        dayTransactions,
    ) { day, txs -> HeatmapDetail(selectedDay = day, dayTransactions = txs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HeatmapDetail(),
        )

    /** 主 UI 状态：5 路 combine（拆分后不再需要额外 bundled 包装层） */
    val uiState: StateFlow<HomeUiState> = combine(
        selectedDateRange,
        rangeExpense,
        recentGroups,
        monthSummary,
        heatmapUi,
    ) { range, expense, groups, monthStats, heatmap ->
        HomeUiState(
            todayExpense = expense,
            monthExpense = monthStats.expense,
            monthIncome = monthStats.income,
            monthBudget = monthStats.budget,
            recentGroups = groups,
            selectedDateRange = range,
            monthHeatmap = heatmap,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true),
    )

    /** 删除一笔交易（同时回滚该交易对账户余额的影响） */
    fun delete(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteAndRevertBalance(id)
        }
    }

    /** 设置日期范围 [start, end) */
    fun setDateRange(range: Pair<Long, Long>) {
        selectedDateRange.value = range
    }

    /** 选中热力图某天（0 点 timestamp）；传 null 关闭弹窗 */
    fun selectHeatmapDay(dayStart: Long?) {
        selectedHeatmapDay.value = dayStart
    }
}
