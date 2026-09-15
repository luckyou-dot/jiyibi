package com.jiyibi.app.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiyibi.app.core.data.repository.AccountPreferencesRepository
import com.jiyibi.app.core.data.repository.AutoRecordPreferencesRepository
import com.jiyibi.app.core.data.repository.MerchantCategoryRepository
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.CategoryKind
import com.jiyibi.app.core.domain.model.Debt
import com.jiyibi.app.core.domain.model.DebtDirection
import com.jiyibi.app.core.domain.model.RecurringFrequency
import com.jiyibi.app.core.domain.model.RecurringRule
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.CategoryRepository
import com.jiyibi.app.core.domain.repository.DebtRepository
import com.jiyibi.app.core.domain.repository.RecurringRepository
import com.jiyibi.app.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val debtRepository: DebtRepository,
    private val recurringRepository: RecurringRepository,
    private val ocrHandler: OcrResultHandler,
    private val accountPreferencesRepository: AccountPreferencesRepository,
    private val autoRecordPreferences: AutoRecordPreferencesRepository,
    private val merchantCategoryRepository: MerchantCategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 暴露 OCR 处理器给 Screen 调用（拍照识别金额 + 分类猜测） */
    val receiptOcr: OcrResultHandler get() = ocrHandler

    /** 从 nav arg 读取交易 id，-1 表示新建 */
    val transactionId: Long = savedStateHandle.get<Long>("transactionId") ?: -1L

    /** 从 nav arg 读取预填 JSON 字符串（URL-encoded），空字符串表示无预填 */
    val prefill: String = savedStateHandle.get<String>("prefill") ?: ""

    /** 是否为编辑模式（id > 0） */
    val isEditMode: Boolean get() = transactionId > 0L

    /** 全部账户列表 */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 默认支出账户 id（未设置或失效时为 null，由 Screen 回退到列表第一个） */
    val defaultExpenseAccountId: StateFlow<Long?> = accountPreferencesRepository.defaultExpenseAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** 默认收入账户 id（未设置或失效时为 null，由 Screen 回退到列表第一个） */
    val defaultIncomeAccountId: StateFlow<Long?> = accountPreferencesRepository.defaultIncomeAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** 支出分类列表 */
    val expenseCategories: StateFlow<List<Category>> = categoryRepository
        .observeByKind(CategoryKind.EXPENSE.name)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 收入分类列表 */
    val incomeCategories: StateFlow<List<Category>> = categoryRepository
        .observeByKind(CategoryKind.INCOME.name)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 用户历史用过的标签：聚合所有交易的 tags 字段（去重、按使用次数降序） */
    val existingTags: StateFlow<List<String>> = transactionRepository
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

    /** 编辑模式下从 repo 加载待编辑交易；新建模式下为 null */
    val editingTransaction: StateFlow<Transaction?> = if (transactionId > 0L) {
        transactionRepository.observeRange(0L, Long.MAX_VALUE)
            .map { list -> list.firstOrNull { it.id == transactionId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
    } else {
        MutableStateFlow(null).asStateFlow()
    }

    /** 预填金额（单位：分），仅 applyPrefill 触发时非 null */
    private val _prefillAmount = MutableStateFlow<Long?>(null)
    val prefillAmount: StateFlow<Long?> = _prefillAmount.asStateFlow()

    /** 预填备注，仅 applyPrefill 触发时非 null */
    private val _prefillNote = MutableStateFlow<String?>(null)
    val prefillNote: StateFlow<String?> = _prefillNote.asStateFlow()

    /**
     * 应用一键补记预填数据。
     *
     * 编辑已有交易（transactionId != -1L）时不覆盖现有数据，直接返回。
     */
    fun applyPrefill(amount: Long, note: String) {
        if (transactionId != -1L) return
        _prefillAmount.value = amount
        _prefillNote.value = note
    }

    /** 保存交易（新建或更新）+ 同步账户余额
     *  - 新建：支出扣减、收入增加
     *  - 编辑：先撤销旧交易影响，再应用新交易影响
     */
    fun save(transaction: Transaction, onDone: () -> Unit) {
        viewModelScope.launch {
            // 编辑模式：撤销旧交易对账户余额的影响
            if (transactionId > 0L) {
                val old = editingTransaction.value
                if (old != null) {
                    reverseAccountEffect(old)
                    learnCategoryIfCorrected(old, transaction)
                }
            }
            // 保存交易
            transactionRepository.upsert(transaction)
            // 应用新交易对账户余额的影响
            applyAccountEffect(transaction)
            onDone()
        }
    }

    /**
     * 学习分类纠正：当用户修改了**自动记账产生的**交易分类时，把
     * 「商户名 → 新分类 id」记入学习表，下次同商户出现时直接采用。
     *
     * 三个约束：
     * 1. 只对**自动记账队列里的**交易学习。手动记账的备注是用户自由文本，
     *    拿它当商户 key 会让学习表被一次性文案充满，没有泛化价值。
     * 2. 只在分类**确实变化**时写入，避免每次编辑备注都覆盖一遍学习表。
     * 3. 备注也被改了的话，说明用户认为原来的商户名识别有误，此时不学习
     *    （否则会把错误商户名固化下来）。分类清空（设为未分类）也会学习，
     *    由 [MerchantCategoryRepository.learn] 写成墓碑记录。
     */
    private suspend fun learnCategoryIfCorrected(old: Transaction, new: Transaction) {
        if (old.categoryId == new.categoryId) return
        if (old.note != new.note) return

        // 只对自动记账产生的交易学习：复核队列里能查到才说明来源是通知
        val isAutoRecorded = autoRecordPreferences.recentIds.first().contains(old.id)
        if (!isAutoRecorded) return

        merchantCategoryRepository.learn(old.note, new.categoryId)
    }

    /** 应用交易对账户余额的影响 */
    private suspend fun applyAccountEffect(tx: Transaction) {
        when (tx.type) {
            TransactionType.EXPENSE -> accountRepository.adjustBalance(tx.accountId, -tx.amount)
            TransactionType.INCOME -> accountRepository.adjustBalance(tx.accountId, tx.amount)
            TransactionType.TRANSFER -> {
                accountRepository.adjustBalance(tx.accountId, -tx.amount)
                tx.toAccountId?.let { toId ->
                    accountRepository.adjustBalance(toId, tx.amount)
                }
            }
        }
    }

    /** 撤销交易对账户余额的影响（用于编辑模式先撤销再应用） */
    private suspend fun reverseAccountEffect(tx: Transaction) {
        when (tx.type) {
            TransactionType.EXPENSE -> accountRepository.adjustBalance(tx.accountId, tx.amount)
            TransactionType.INCOME -> accountRepository.adjustBalance(tx.accountId, -tx.amount)
            TransactionType.TRANSFER -> {
                accountRepository.adjustBalance(tx.accountId, tx.amount)
                tx.toAccountId?.let { toId ->
                    accountRepository.adjustBalance(toId, -tx.amount)
                }
            }
        }
    }

    /** 删除交易（仅编辑模式可用）+ 撤销账户余额 */
    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            // 撤销旧交易对账户余额的影响
            val old = editingTransaction.value
            if (old != null) reverseAccountEffect(old)
            transactionRepository.delete(id)
            onDone()
        }
    }

    /** 同步创建一条周期性记账规则（开启周期记账时由保存流程调用） */
    fun saveRecurringRule(
        amount: Long,
        type: TransactionType,
        accountId: Long,
        categoryId: Long?,
        frequency: RecurringFrequency,
        nextRunAt: Long,
    ) {
        viewModelScope.launch {
            recurringRepository.upsert(
                RecurringRule(
                    title = "周期记账",
                    amount = amount,
                    type = type,
                    accountId = accountId,
                    categoryId = categoryId,
                    frequency = frequency,
                    interval = 1,
                    nextRunAt = nextRunAt,
                    autoRecord = true,
                    enabled = true,
                ),
            )
        }
    }

    /** 保存分账（AA）生成的借贷记录：每位非"我"的参与人对应一条 Debt */
    fun saveDebtRecord(counterparty: String, amount: Long, note: String) {
        viewModelScope.launch {
            debtRepository.upsert(
                Debt(
                    counterparty = counterparty,
                    direction = DebtDirection.OWED_TO_ME,
                    amount = amount,
                    note = note,
                    dueDate = null,
                    settled = false,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
