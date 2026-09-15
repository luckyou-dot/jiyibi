package com.jiyibi.app.core.common

import androidx.compose.runtime.Immutable
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import java.util.Calendar

/**
 * 交易列表行的统一展示模型。
 *
 * 首页「最近交易」与搜索页「查看全部」共用同一套行渲染，因此这里不区分来源，
 * 只承载渲染所需的最小字段。
 *
 * 放在 `core/common` 而非设计系统：它是**展示模型 + 纯分组逻辑**，
 * ViewModel 需要据此产出 UI 状态，不应让 ViewModel 反向依赖 UI 组件层。
 *
 * 标注 [Immutable]：Compose 对含 `List` 字段的类一律判 unstable，
 * 显式声明后接收它的 Composable 才能被 skip（见性能诊断报告 P1-1a）。
 *
 * @property tx          交易本体
 * @property category    关联分类（可能为 null → 未分类）
 * @property accountName 账户名，空串表示不展示
 */
@Immutable
data class TransactionRowUi(
    val tx: Transaction,
    val category: Category?,
    val accountName: String = "",
)

/**
 * 按「天」聚合后的一段交易。
 *
 * @property dayStart      当天 0 点 epoch millis（本地时区），同时作为分组 key
 * @property expenseTotal  当天支出合计（分），**不含转账**
 * @property incomeTotal   当天收入合计（分），**不含转账**
 * @property rows          当天交易，按时间倒序
 */
@Immutable
data class TransactionDayGroup(
    val dayStart: Long,
    val expenseTotal: Long,
    val incomeTotal: Long,
    val rows: List<TransactionRowUi>,
)

/**
 * 把交易按「天」分组，并算出每天的出入合计。
 *
 * 两个要点：
 * - 分组 key 用 [startOfDay]（Calendar 算本地 0 点），**不能**用
 *   `date / MILLIS_PER_DAY * MILLIS_PER_DAY`——那是按 UTC 切分，
 *   在东八区会把当天 0:00~8:00 的账错算到前一天。
 * - 出/入合计**排除转账**：转账只是账户间挪动，不构成真实收支，
 *   与参考图里「零钱提现」那天显示 `出 0.00 入 0.00` 的行为一致。
 *
 * @param rows 交易行，顺序不限（内部会按天与时间重新排序）
 */
fun buildDayGroups(rows: List<TransactionRowUi>): List<TransactionDayGroup> =
    rows.groupBy { startOfDay(it.tx.date) }
        .entries
        .sortedByDescending { it.key }
        .map { (dayStart, dayRows) ->
            val sorted = dayRows.sortedByDescending { it.tx.date }
            TransactionDayGroup(
                dayStart = dayStart,
                expenseTotal = sorted
                    .filter { it.tx.type == TransactionType.EXPENSE }
                    .sumOf { it.tx.amount },
                incomeTotal = sorted
                    .filter { it.tx.type == TransactionType.INCOME }
                    .sumOf { it.tx.amount },
                rows = sorted,
            )
        }

/** 取某时刻所在「天」的本地 0 点 */
fun startOfDay(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
