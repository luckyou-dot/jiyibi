package com.jiyibi.app.core.domain.repository

import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.Budget
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.CategoryStat
import com.jiyibi.app.core.domain.model.Debt
import com.jiyibi.app.core.domain.model.RecurringRule
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TrendPoint
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeRecent(limit: Int): Flow<List<Transaction>>
    fun observeRange(start: Long, end: Long): Flow<List<Transaction>>
    fun search(
        keyword: String,
        start: Long?,
        end: Long?,
        minAmount: Long?,
        maxAmount: Long?,
    ): Flow<List<Transaction>>

    suspend fun upsert(transaction: Transaction): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()

    /** 按 id 批量取交易（供「自动记账」页展示最近自动记录） */
    suspend fun getByIds(ids: List<Long>): List<Transaction>

    /**
     * 「同金额 + 同收支类型」在时间窗内是否已有交易。
     *
     * 用于自动记账去重：同一条通知会重复投递，同一次支付也会被通知与无障碍两条通道各报一次。
     */
    suspend fun hasSameAmountInWindow(amount: Long, type: String, from: Long, to: Long): Boolean

    fun observeTotalExpense(start: Long, end: Long): Flow<Long>
    fun observeTotalIncome(start: Long, end: Long): Flow<Long>
    fun observeCategoryStats(start: Long, end: Long): Flow<List<CategoryStat>>
    fun observeTrend(start: Long, end: Long, periodMillis: Long): Flow<List<TrendPoint>>
}

interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    fun observeTotalBalance(): Flow<Long>
    suspend fun upsert(account: Account): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
    suspend fun adjustBalance(id: Long, delta: Long)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeByKind(kind: String): Flow<List<Category>>
    suspend fun upsert(category: Category): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}

interface BudgetRepository {
    fun observeActive(now: Long): Flow<List<Budget>>
    suspend fun upsert(budget: Budget): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}

interface RecurringRepository {
    fun observeAll(): Flow<List<RecurringRule>>
    suspend fun upsert(rule: RecurringRule): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
    suspend fun getDue(now: Long): List<RecurringRule>
    suspend fun advanceNextRun(id: Long, nextRunAt: Long)
}

interface DebtRepository {
    fun observeUnsettled(): Flow<List<Debt>>
    fun observeAll(): Flow<List<Debt>>
    suspend fun upsert(debt: Debt): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
    suspend fun markSettled(id: Long, settled: Boolean)
}
