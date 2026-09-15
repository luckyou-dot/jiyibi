package com.jiyibi.app.core.data.repository

import com.jiyibi.app.core.data.toDomain
import com.jiyibi.app.core.data.toEntity
import com.jiyibi.app.core.database.dao.CategoryDao
import com.jiyibi.app.core.database.dao.TransactionDao
import com.jiyibi.app.core.domain.model.CategoryStat
import com.jiyibi.app.core.domain.model.TrendPoint
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val categoryDao: CategoryDao,
) : TransactionRepository {

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeRange(start: Long, end: Long): Flow<List<Transaction>> =
        dao.observeRange(start, end).map { list -> list.map { it.toDomain() } }

    override fun search(
        keyword: String,
        start: Long?,
        end: Long?,
        minAmount: Long?,
        maxAmount: Long?,
    ): Flow<List<Transaction>> =
        dao.search(keyword, start, end, minAmount, maxAmount)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(transaction: Transaction): Long {
        val now = System.currentTimeMillis()
        return dao.upsert(transaction.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun getByIds(ids: List<Long>): List<Transaction> {
        if (ids.isEmpty()) return emptyList()
        return dao.getByIds(ids).map { it.toDomain() }
    }

    override suspend fun hasSameAmountInWindow(amount: Long, from: Long, to: Long): Boolean =
        dao.countSameAmountInWindow(amount, from, to) > 0

    override fun observeTotalExpense(start: Long, end: Long): Flow<Long> =
        dao.observeTotalExpense(start, end)

    override fun observeTotalIncome(start: Long, end: Long): Flow<Long> =
        dao.observeTotalIncome(start, end)

    override fun observeCategoryStats(start: Long, end: Long): Flow<List<CategoryStat>> {
        // 关联分类表：用分类的 name/icon/color 而非交易备注
        return combine(
            dao.observeRange(start, end),
            categoryDao.observeAll(),
        ) { entities, categories ->
            val categoryMap = categories.associateBy { it.id }
            entities
                .filter { it.type == "EXPENSE" && it.categoryId != null }
                .groupBy { it.categoryId!! }
                .map { (catId, items) ->
                    val cat = categoryMap[catId]
                    CategoryStat(
                        categoryId = catId,
                        categoryName = cat?.name ?: "未分类",
                        color = cat?.color ?: 0,
                        icon = cat?.icon ?: "",
                        total = items.sumOf { it.amount },
                    )
                }
                .sortedByDescending { it.total }
        }
    }

    override fun observeTrend(
        start: Long,
        end: Long,
        periodMillis: Long,
    ): Flow<List<TrendPoint>> = dao.observeRange(start, end).map { list ->
        // 按日聚合：补齐区间内每一天，没交易的日子 expense=0、income=0
        val byBucket = list.groupBy { it.date / periodMillis * periodMillis }
            .mapValues { (bucket, items) ->
                TrendPoint(
                    timestamp = bucket,
                    expense = items.filter { it.type == "EXPENSE" }.sumOf { it.amount },
                    income = items.filter { it.type == "INCOME" }.sumOf { it.amount },
                )
            }
        // 枚举 [start, end) 内每一个采样桶，没数据的补一个 0 点
        val result = mutableListOf<TrendPoint>()
        var cur = start / periodMillis * periodMillis
        while (cur < end) {
            result.add(byBucket[cur] ?: TrendPoint(cur, 0L, 0L))
            cur += periodMillis
        }
        result
    }
}
