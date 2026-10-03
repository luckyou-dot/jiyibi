package com.jiyibi.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiyibi.app.core.common.TransactionDayGroup
import com.jiyibi.app.core.common.TransactionRowUi
import com.jiyibi.app.core.common.buildDayGroups
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.yuanToCents
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.CategoryRepository
import com.jiyibi.app.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 搜索过滤条件 */
data class SearchFilters(
    val keyword: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minAmount: Long? = null,   // 元
    val maxAmount: Long? = null,   // 元
    val categoryIds: Set<Long> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val tags: Set<String> = emptySet(),
    val types: Set<TransactionType> = emptySet(),
)

/**
 * 搜索页 UI 状态。
 *
 * @property resultGroups 搜索结果，**已按天分组**（与首页「最近交易」同一套结构），
 *                        每组带当天出/入合计
 */
data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val resultGroups: List<TransactionDayGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val existingTags: List<String> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(SearchFilters())

    /** 全部历史标签：聚合所有交易的 tags（去重、按使用次数降序），供标签筛选展示 */
    private val existingTags: StateFlow<List<String>> = transactionRepository
        .observeRange(0L, Long.MAX_VALUE)
        .map { txs ->
            txs.flatMap { it.tags }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = _filters
        .flatMapLatest { filters ->
            // search() 处理 keyword/日期/金额区间；categoryIds/accountIds/tags/types 在内存过滤
            combine(
                transactionRepository.search(
                    keyword = filters.keyword,
                    start = filters.startDate,
                    end = filters.endDate,
                    minAmount = filters.minAmount?.toDouble()?.yuanToCents(),
                    maxAmount = filters.maxAmount?.toDouble()?.yuanToCents(),
                ),
                categoryRepository.observeAll(),
                accountRepository.observeAll(),
                existingTags,
            ) { raw, categories, accounts, tags ->
                // 内存多维度过滤：分类 / 账户 / 标签 / 收支类型
                val filtered = raw
                    .filterBy(filters.categoryIds) { it.categoryId }
                    .filterBy(filters.accountIds) { it.accountId }
                    .filterByTags(filters.tags)
                    .filterByTypes(filters.types)

                // 关联分类与账户名后按天分组，交给与首页共用的列表组件渲染
                val categoryMap = categories.associateBy { it.id }
                val accountMap = accounts.associateBy { it.id }
                val rows = filtered.map { tx ->
                    TransactionRowUi(
                        tx = tx,
                        category = tx.categoryId?.let { categoryMap[it] },
                        accountName = accountMap[tx.accountId]?.name.orEmpty(),
                    )
                }
                SearchUiState(
                    filters = filters,
                    resultGroups = buildDayGroups(rows),
                    categories = categories,
                    accounts = accounts,
                    existingTags = tags,
                    isLoading = false,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(isLoading = true),
        )

    fun updateKeyword(v: String) {
        _filters.update { it.copy(keyword = v) }
    }

    fun updateDateRange(start: Long?, end: Long?) {
        _filters.update { it.copy(startDate = start, endDate = end) }
    }

    fun updateAmountRange(min: Long?, max: Long?) {
        _filters.update { it.copy(minAmount = min, maxAmount = max) }
    }

    fun toggleCategory(id: Long) {
        _filters.update { f -> f.copy(categoryIds = f.categoryIds.toggle(id)) }
    }

    fun toggleAccount(id: Long) {
        _filters.update { f -> f.copy(accountIds = f.accountIds.toggle(id)) }
    }

    fun toggleTag(tag: String) {
        _filters.update { f -> f.copy(tags = f.tags.toggle(tag)) }
    }

    fun toggleType(type: TransactionType) {
        _filters.update { f -> f.copy(types = f.types.toggle(type)) }
    }

    fun clearFilters() {
        _filters.value = SearchFilters()
    }

    /** 删除一笔交易（同时回滚该交易对账户余额的影响） */
    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.deleteAndRevertBalance(id) }
    }
}

/** Set 的 toggle 辅助：存在则移除，不存在则添加 */
private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item

/** 按 id 集合过滤：集合为空表示不限制 */
private inline fun <T> List<T>.filterBy(ids: Set<Long>, selector: (T) -> Long?): List<T> =
    if (ids.isEmpty()) this else filter { selector(it) in ids }

/** 按标签过滤：交易需包含所有选中标签（AND 关系） */
private fun List<Transaction>.filterByTags(tags: Set<String>): List<Transaction> =
    if (tags.isEmpty()) this else filter { tx -> tags.all { it in tx.tags } }

/** 按收支类型过滤 */
private fun List<Transaction>.filterByTypes(types: Set<TransactionType>): List<Transaction> =
    if (types.isEmpty()) this else filter { it.type in types }
