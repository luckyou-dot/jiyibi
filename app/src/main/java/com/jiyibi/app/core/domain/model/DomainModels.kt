package com.jiyibi.app.core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 把以「分」为单位的 Long 转成元（BigDecimal），用于 UI 显示与导出。
 */
fun Long.centsToYuan(): BigDecimal = BigDecimal.valueOf(this).movePointLeft(2)

/**
 * 单笔金额上限（分）：¥10 亿。
 *
 * 正常记账不可能碰到这个量级，超限只可能来自误输入（长按粘贴出一串 9）或脏数据导入。
 * 截断而不抛异常，理由见 [yuanToCents]。
 */
const val MAX_AMOUNT_CENTS = 100_000_000_000L

/**
 * 把用户输入的元转成「分」，**任何输入都不抛异常**。
 *
 * 实现上两个关键点：
 * 1. 用 `setScale(0, HALF_UP)` 而不是 `longValueExact()`：金额输入框只过滤了「数字和小数点」，
 *    挡不住三位小数（`12.345`）。`longValueExact()` 遇到小数余量会抛
 *    `ArithmeticException`，而这个函数被记一笔、账户余额、预算、借据、周期规则、
 *    搜索金额筛选等 7 处直接调用，抛出去就是点「保存」当场闪退。四舍五入到分是记账的常规语义。
 * 2. 溢出与非法输入兜底：`NaN` / `Infinity` 归零，超出 [MAX_AMOUNT_CENTS] 按上限截断，
 *    避免 `Double` 科学计数法（如 `1e21`）转成天文数字写进数据库。
 */
fun Double.yuanToCents(): Long {
    if (!isFinite()) return 0L
    val cents = BigDecimal.valueOf(this).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
    val limit = BigDecimal.valueOf(MAX_AMOUNT_CENTS)
    return when {
        cents > limit -> MAX_AMOUNT_CENTS
        cents < limit.negate() -> -MAX_AMOUNT_CENTS
        else -> cents.longValueExact()
    }
}

/** 一笔交易（领域模型，与 UI/DB 解耦）。 */
data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Long,                 // 分
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val date: Long,
    val recurringRuleId: Long? = null,
)

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val balance: Long,
    val color: Int,
    val sortOrder: Int,
    val archived: Boolean = false,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val icon: String,
    val color: Int,
    val builtin: Boolean,
)

data class Budget(
    val id: Long = 0,
    val periodStart: Long,
    val periodEnd: Long,
    val categoryId: Long?,
    val amountLimit: Long,
    val alertThreshold: Float = 0.8f,
)

data class RecurringRule(
    val id: Long = 0,
    val title: String,
    val amount: Long,
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long?,
    val frequency: RecurringFrequency,
    val interval: Int = 1,
    val nextRunAt: Long,
    val autoRecord: Boolean,
    val enabled: Boolean,
)

data class Debt(
    val id: Long = 0,
    val counterparty: String,
    val direction: DebtDirection,
    val amount: Long,
    val note: String,
    val dueDate: Long?,
    val settled: Boolean,
    val createdAt: Long,
    val settledAt: Long? = null,
)

/** 分类统计项，用于饼图。 */
data class CategoryStat(
    val categoryId: Long,
    val categoryName: String,
    val color: Int,
    val icon: String = "",
    val total: Long,
)

/** 时间趋势点，用于折线图。 */
data class TrendPoint(
    val timestamp: Long,
    val expense: Long,
    val income: Long,
)
