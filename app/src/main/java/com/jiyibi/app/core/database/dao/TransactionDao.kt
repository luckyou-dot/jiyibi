package com.jiyibi.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jiyibi.app.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清空所有交易记录，用于从备份恢复前清空。 */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /**
     * 按 id 批量取交易，供「自动记账」页展示最近自动记录。
     *
     * 用 suspend 一次性查询而非 Flow：调用方已持有 id 队列，列表内容变化由队列驱动。
     */
    @Query("SELECT * FROM transactions WHERE id IN (:ids) ORDER BY date DESC")
    suspend fun getByIds(ids: List<Long>): List<TransactionEntity>

    /**
     * 统计指定金额在时间窗内的交易条数，用于通知去重。
     *
     * 同一条通知可能被系统重复投递（通知更新 / 重发），此时金额与 postTime 都相同，
     * 命中即说明这笔已经记过，应跳过。
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE amount = :amount AND date BETWEEN :from AND :to")
    suspend fun countSameAmountInWindow(amount: Long, from: Long, to: Long): Int

    /** 指定时间范围内的交易，用于日/周/月/年统计与列表。 */
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    /** 最近 N 笔，首页展示。 */
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /** 关键词 + 日期范围 + 金额区间筛选。 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:keyword = '' OR note LIKE '%' || :keyword || '%')
          AND (:start IS NULL OR date >= :start)
          AND (:end IS NULL OR date <= :end)
          AND (:minAmount IS NULL OR amount >= :minAmount)
          AND (:maxAmount IS NULL OR amount <= :maxAmount)
        ORDER BY date DESC
        """
    )
    fun search(
        keyword: String,
        start: Long?,
        end: Long?,
        minAmount: Long?,
        maxAmount: Long?,
    ): Flow<List<TransactionEntity>>

    /** 某分类在某时间段的支出合计。 */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE type = 'EXPENSE' AND categoryId = :categoryId AND date BETWEEN :start AND :end"
    )
    fun observeCategoryExpense(categoryId: Long, start: Long, end: Long): Flow<Long>

    /** 某时间段总支出，用于首页与预算进度。 */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end"
    )
    fun observeTotalExpense(start: Long, end: Long): Flow<Long>

    /** 某时间段总收入，用于首页本月收入展示。 */
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE type = 'INCOME' AND date BETWEEN :start AND :end"
    )
    fun observeTotalIncome(start: Long, end: Long): Flow<Long>
}
