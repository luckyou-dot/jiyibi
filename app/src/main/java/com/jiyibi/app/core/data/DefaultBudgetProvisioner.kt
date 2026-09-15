package com.jiyibi.app.core.data

import com.jiyibi.app.core.common.TimeRange
import com.jiyibi.app.core.data.repository.BudgetPreferencesRepository
import com.jiyibi.app.core.domain.model.Budget
import com.jiyibi.app.core.domain.repository.BudgetRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** 本月总预算的默认额度：¥1200（金额以分存储） */
const val DEFAULT_TOTAL_BUDGET_CENTS = 120_000L

/** 默认提醒阈值：使用额达 80% 时提醒 */
private const val DEFAULT_ALERT_THRESHOLD = 0.8f

/**
 * 默认月度总预算供给器。
 *
 * 应用的月度总预算有统一默认值 ¥1200：当某个月份从未预置过默认预算、
 * 且该月当前没有任何总预算时，自动写入一条额度为 ¥1200 的月度总预算。
 *
 * 通过 [BudgetPreferencesRepository] 记录已预置的月份起点，因此：
 * - 同一个月只预置一次，用户改过的额度不会被后续启动覆盖；
 * - 用户主动删除默认预算后不会被强行补回，尊重用户意图；
 * - 跨月后 periodStart 变化，会自动为新月份重新预置默认预算。
 *
 * 由 `JiYiBiApp` 在启动时调用（保证全局一致，首页等页面立即可见），
 * 并在 `BudgetViewModel` 初始化时再兜底调用一次（覆盖长时间不重启的跨月场景）。
 */
@Singleton
class DefaultBudgetProvisioner @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val budgetPreferences: BudgetPreferencesRepository,
) {

    /** 确保当前月份存在月度总预算；已预置过或已有总预算时不做任何写入 */
    suspend fun ensureCurrentMonthTotalBudget() {
        val (start, end) = TimeRange.thisMonth()
        // 该月份已预置过默认预算则直接跳过
        if (budgetPreferences.seededPeriodStart() == start) return
        val existing = budgetRepository.observeActive(System.currentTimeMillis()).first()
        // 已有总预算（用户手动设置或历史遗留）时不覆盖，仅标记本月已预置
        if (existing.none { it.categoryId == null }) {
            budgetRepository.upsert(
                Budget(
                    id = 0L,
                    periodStart = start,
                    periodEnd = end,
                    categoryId = null,
                    amountLimit = DEFAULT_TOTAL_BUDGET_CENTS,
                    alertThreshold = DEFAULT_ALERT_THRESHOLD,
                ),
            )
        }
        budgetPreferences.markSeeded(start)
    }
}
