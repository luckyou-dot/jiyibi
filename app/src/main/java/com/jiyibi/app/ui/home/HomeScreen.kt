package com.jiyibi.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.common.TimeRange
import com.jiyibi.app.core.designsystem.component.AnimatedNumber
import com.jiyibi.app.core.designsystem.component.AnimatedProgressIndicator
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.EmptyState
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.SwipeToDeleteItem
import com.jiyibi.app.core.designsystem.component.TransactionDayCard
import com.jiyibi.app.core.designsystem.component.TransactionDayHeader
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.theme.BudgetAmber
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.designsystem.theme.gradientBrush
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.centsToYuan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一天的毫秒数，用于「昨天」与「自选日期」的范围推算 */
private const val MILLIS_PER_DAY = 86_400_000L

/** Hero 渐变区高度：从状态栏延伸至本月收入支出板块的正上方 */
private val HeroHeight = 324.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onOpenSearch: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 热力图弹窗详情单独订阅：点击格子只影响 BottomSheet，不触发首页主体重组
    val heatmapDetail by viewModel.heatmapDetail.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    // 计算 5 个 Pill 对应的日期范围，用于选中态判定与 SummaryCard 标题
    val todayRange = remember { TimeRange.today() }
    val yesterdayRange = remember {
        val start = todayRange.first - MILLIS_PER_DAY
        start to (start + MILLIS_PER_DAY)
    }
    val weekRange = remember { TimeRange.thisWeek() }
    val monthRange = remember { TimeRange.thisMonth() }

    val selectedRange = state.selectedDateRange
    val isToday = selectedRange == todayRange
    val isYesterday = selectedRange == yesterdayRange
    val isWeek = selectedRange == weekRange
    val isMonth = selectedRange == monthRange
    val isCustom = !isToday && !isYesterday && !isWeek && !isMonth

    val summaryLabel = when {
        isToday -> "今日支出"
        isYesterday -> "昨日支出"
        isWeek -> "本周支出"
        isMonth -> "本月支出"
        else -> "自选日期支出"
    }

    // 根 Box：底层固定渐变 Hero 背景 + 透明 Scaffold 叠加，使 TopAppBar 透明地浮于渐变之上
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部渐变 Hero 背景层：固定高度，位于屏幕顶部，不随滚动变化
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroHeight)
                .background(gradientBrush()),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "首页",
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAddTransaction,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("记一笔") },
                )
            },
        ) { padding ->
            // TODO: 待 Compose BOM 升级至 2024.09.00+ 后接入 PullToRefreshBox 实现下拉刷新
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.l,
                    end = Spacing.l,
                    top = Spacing.l,
                    // 避开右下角 ExtendedFloatingActionButton（高度约 56dp + 16dp 边距）
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // Hero 区：毛玻璃汇总卡（支出 + 预算进度）
                item { HeroSummaryCard(state, summaryLabel) }
                // 日期 Pill 切换条：半透明叠加在 Hero 底部
                item {
                    DatePillRow(
                        isToday = isToday,
                        isYesterday = isYesterday,
                        isWeek = isWeek,
                        isMonth = isMonth,
                        isCustom = isCustom,
                        onPickToday = { viewModel.setDateRange(TimeRange.today()) },
                        onPickYesterday = { viewModel.setDateRange(yesterdayRange) },
                        onPickWeek = { viewModel.setDateRange(TimeRange.thisWeek()) },
                        onPickMonth = { viewModel.setDateRange(TimeRange.thisMonth()) },
                        onPickCustom = { showDatePicker = true },
                    )
                }
                // 收支概览卡：本月收入 / 本月支出
                item { IncomeExpenseCard(state) }
                // 当月日历热力图：颜色深浅 = 当日支出强度
                // 峰值日已由 ViewModel 随单元格一起算好，此处不再重复聚合
                item {
                    HeatmapCard(
                        heatmap = state.monthHeatmap,
                        onCellClick = { viewModel.selectHeatmapDay(it) },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "最近交易",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenSearch) { Text("查看全部") }
                    }
                }
                if (state.recentGroups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "还没有记账记录",
                            subtitle = "点击右下角开始记第一笔",
                            actionText = "记一笔",
                            onAction = onAddTransaction,
                        )
                    }
                } else {
                    // 按天分组渲染：日头（日期 + 当天出入合计）+ 当天交易卡
                    state.recentGroups.forEach { group ->
                        item(key = "day-${group.dayStart}") {
                            TransactionDayHeader(group = group)
                        }
                        item(key = "card-${group.dayStart}") {
                            TransactionDayCard(
                                group = group,
                                onRowClick = onEditTransaction,
                            ) { row, content ->
                                SwipeToDeleteItem(
                                    onDelete = { viewModel.delete(row.tx.id) },
                                    backgroundCorner = Corner.small,
                                ) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 自选日期弹窗：M3 DatePickerDialog，确认后设定当日 [selected, selected+1day)
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { sel ->
                            viewModel.setDateRange(sel to (sel + MILLIS_PER_DAY))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 热力图单元格点击弹窗：显示当日交易明细
    // 只依赖 heatmapDetail，点击格子不会导致首页主体重组
    val selectedHeatmapDay = heatmapDetail.selectedDay
    val dayTransactions = heatmapDetail.dayTransactions
    if (selectedHeatmapDay != null) {
        val sheetDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
        ModalBottomSheet(onDismissRequest = { viewModel.selectHeatmapDay(null) }) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    "${sheetDateFormat.format(Date(selectedHeatmapDay))} 交易明细",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (dayTransactions.isEmpty()) {
                    Text("当日无交易", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dayTransactions.forEach { tx ->
                        val isIncome = tx.type == TransactionType.INCOME
                        val sign = if (isIncome) "+" else "-"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tx.note.ifBlank { if (isIncome) "收入" else "支出" },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "$sign¥${tx.amount.centsToYuan().toPlainString()}",
                                color = if (isIncome) IncomeGreen else ExpenseRed,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    // 避开系统导航栏/手势条
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 横向日期 Pill 切换条：今天 / 昨天 / 本周 / 本月 / 📅 自选。
 */
@Composable
private fun DatePillRow(
    isToday: Boolean,
    isYesterday: Boolean,
    isWeek: Boolean,
    isMonth: Boolean,
    isCustom: Boolean,
    onPickToday: () -> Unit,
    onPickYesterday: () -> Unit,
    onPickWeek: () -> Unit,
    onPickMonth: () -> Unit,
    onPickCustom: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item { DatePill("今天", selected = isToday, onClick = onPickToday) }
        item { DatePill("昨天", selected = isYesterday, onClick = onPickYesterday) }
        item { DatePill("本周", selected = isWeek, onClick = onPickWeek) }
        item { DatePill("本月", selected = isMonth, onClick = onPickMonth) }
        item { DatePill("📅 自选", selected = isCustom, onClick = onPickCustom) }
    }
}

/**
 * 单个日期 Pill：选中态主题色实底，未选用半透明白底。
 *
 * - 选中态：primary 实底 + 白色文字
 * - 未选用：半透明白底 + 白色文字（在彩色渐变背景上融合且可读）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
        contentColor = Color.White,
        onClick = onClick,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * Hero 区毛玻璃汇总卡：选定日期范围支出 + 本月预算进度条。
 *
 * 使用 [GlassCard] 叠加在渐变 Hero 之上，文字采用白色保证在渐变背景上的可读性。
 * 支出金额用 [AnimatedNumber] 滚动动效，预算进度用 [AnimatedProgressIndicator] 动效进度条。
 *
 * @param rangeLabel 汇总卡顶部标签文本，随日期 Pill 选择动态变化
 */
@Composable
private fun HeroSummaryCard(state: HomeUiState, rangeLabel: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.l),
    ) {
        // 行 1：选定日期范围支出标签
        Text(
            rangeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(Spacing.xs))
        // 行 2：支出金额（等宽大字 + 数字滚动动效）
        AnimatedNumber(
            targetValue = state.todayExpense.centsToYuan().toDouble(),
            prefix = "¥",
            style = MaterialTheme.typography.displayMedium,
            color = ExpenseRed,
        )
        Spacer(Modifier.height(Spacing.m))
        // 行 3：本月预算进度
        if (state.monthBudget > 0L) {
            val rawRatio = state.monthExpense.toFloat() / state.monthBudget
            val displayRatio = rawRatio.coerceIn(0f, 1f)
            // 颜色按阈值：<0.8 primary、≤1.0 BudgetAmber、>1 ExpenseRed
            val progressColor = when {
                rawRatio < 0.8f -> MaterialTheme.colorScheme.primary
                rawRatio <= 1.0f -> BudgetAmber
                else -> ExpenseRed
            }
            AnimatedProgressIndicator(
                progress = displayRatio,
                indicatorColor = progressColor,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "已用 ¥${state.monthExpense.centsToYuan().toPlainString()} / 预算 ¥${state.monthBudget.centsToYuan().toPlainString()}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            if (rawRatio > 1.0f) {
                Text(
                    "已超支",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseRed,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                "未设置预算",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 中部收支卡片：本月收入 / 本月支出两栏，数字用 [AnimatedNumber] 滚动动效。
 */
@Composable
private fun IncomeExpenseCard(state: HomeUiState) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row {
            // 左：本月收入
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "本月收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedNumber(
                    targetValue = state.monthIncome.centsToYuan().toDouble(),
                    prefix = "¥",
                    style = MaterialTheme.typography.titleLarge,
                    color = IncomeGreen,
                )
            }
            // 右：本月支出
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "本月支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedNumber(
                    targetValue = state.monthExpense.centsToYuan().toDouble(),
                    prefix = "¥",
                    style = MaterialTheme.typography.titleLarge,
                    color = ExpenseRed,
                )
            }
        }
    }
}

/**
 * 当月日历热力图：7 列网格，颜色深浅 = 当日支出强度（5 档主题色梯度）。
 *
 * 颜色档位从 surfaceVariant 渐进到 primary，使用主题色而非硬编码绿色。
 * - 单元格按当月天数顺序排列，每 7 个为一行（按周对齐）
 * - 末行不足 7 个时用 Spacer 占位保持网格对齐
 * - 点击单元格触发 [onCellClick]，由外部弹 BottomSheet 显示当日交易明细
 *
 * 参数使用 [HeatmapUiState] 而非裸 `List` + `Pair`：裸 `List` 参数会被 Compose
 * 判定为 unstable，导致本 Composable 无法 skip；包装类已标注 `@Immutable`。
 *
 * @param heatmap     热力图渲染数据（单元格 + 峰值日）
 * @param onCellClick 点击单元格回调，参数为当天 0 点 timestamp
 */
@Composable
private fun HeatmapCard(
    heatmap: HeatmapUiState,
    onCellClick: (Long) -> Unit,
) {
    val cells = heatmap.cells
    val peakDay = heatmap.peakDay
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "本月日历热力图",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "颜色深浅 = 支出强度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.s))
        // 7 列网格（按周排列），每行最多 7 个单元格
        val rows = remember(cells) { cells.chunked(7) }
        rows.forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rowCells.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatmapColor(cell.level))
                            .clickable { onCellClick(cell.date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${cell.day}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (cell.level >= 3) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                // 末行不足 7 个时补齐空格，保持网格对齐
                repeat(7 - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(3.dp))
        }
        peakDay?.let { (day, amount) ->
            Text(
                "$day 日累计支出最强 ¥${amount.centsToYuan().toPlainString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 热力图颜色档位（5 档主题色梯度）：
 *
 * 0 无支出 surfaceVariant → 1~3 primary 渐进透明 → 4 primary 满色。
 *
 * 标注 @Composable 是因为读取 MaterialTheme.colorScheme。
 */
@Composable
private fun heatmapColor(level: Int): Color = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceVariant
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    else -> MaterialTheme.colorScheme.primary
}

/**
 * 「最近交易」的行渲染已抽到共享组件 `TransactionRow` /
 * `TransactionDayCard`（见 `core/designsystem/component/TransactionList.kt`），
 * 首页与搜索页「查看全部」共用同一套实现，此处不再保留副本。
 */
