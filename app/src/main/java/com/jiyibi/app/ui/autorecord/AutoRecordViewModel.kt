package com.jiyibi.app.ui.autorecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiyibi.app.core.data.repository.AiConfig
import com.jiyibi.app.core.data.repository.AiPreferencesRepository
import com.jiyibi.app.core.data.repository.AutoRecordPreferencesRepository
import com.jiyibi.app.core.data.repository.MerchantCategoryRepository
import com.jiyibi.app.core.data.repository.UnmatchedNotification
import com.jiyibi.app.core.data.repository.UnmatchedNotificationRepository
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.CategoryRepository
import com.jiyibi.app.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「最近自动记录」列表项。
 *
 * @property tx          交易本体
 * @property category    关联分类（可能为 null → 未分类）
 * @property accountName 记账落到哪个账户
 */
data class AutoRecordItem(
    val tx: Transaction,
    val category: Category?,
    val accountName: String,
)

/**
 * 自动记账页 UI 状态。
 *
 * @property enabled  通知自动记账开关
 * @property records  最近自动记录（按时间倒序）
 * @property isLoading 首次加载中
 * @property learnedCount 已学习的「商户 → 分类」条目数，为 0 时说明用户还没纠正过任何分类
 * @property unmatched 最近未识别的支付通知（按时间倒序），规则漏掉新句式时在这里可见
 */
data class AutoRecordUiState(
    val enabled: Boolean = true,
    val records: List<AutoRecordItem> = emptyList(),
    val isLoading: Boolean = true,
    val learnedCount: Int = 0,
    val unmatched: List<UnmatchedNotification> = emptyList(),
)

/**
 * 自动记账页 ViewModel。
 *
 * 负责「开关」「最近自动记录」「未识别通知」三块数据；**通知使用权是否已授权**
 * 由界面层直接读系统设置判断（见 `AutoRecordScreen`），避免 ViewModel 持有 Context。
 */
@HiltViewModel
class AutoRecordViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val autoRecordPreferences: AutoRecordPreferencesRepository,
    private val merchantCategoryRepository: MerchantCategoryRepository,
    private val unmatchedNotificationRepository: UnmatchedNotificationRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
) : ViewModel() {

    /** AI 智能识别配置（独立于核心列表状态，避免 combine 层数继续膨胀） */
    val aiConfig: StateFlow<AiConfig> = aiPreferencesRepository.config.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiConfig(),
    )

    /** 保存 AI 配置（Base URL / Key / 模型 / 开关） */
    fun saveAiConfig(config: AiConfig) {
        viewModelScope.launch { aiPreferencesRepository.save(config) }
    }

    val uiState: StateFlow<AutoRecordUiState> = combine(
        // combine 最多 5 个 typed 流：核心 5 躲不开，未识别队列单独再合并一层
        combine(
            autoRecordPreferences.enabled,
            autoRecordPreferences.recentIds,
            accountRepository.observeAll(),
            categoryRepository.observeAll(),
            merchantCategoryRepository.mappings,
        ) { enabled, ids, accounts, categories, learned ->
            val accountMap = accounts.associateBy { it.id }
            val categoryMap = categories.associateBy { it.id }
            // 队列里可能存在已被删除（或备份恢复后失效）的 id，getByIds 查不到就自然消失
            val records = transactionRepository.getByIds(ids)
                .sortedByDescending { it.date }
                .map { tx ->
                    AutoRecordItem(
                        tx = tx,
                        category = tx.categoryId?.let { categoryMap[it] },
                        accountName = accountMap[tx.accountId]?.name ?: "未知账户",
                    )
                }
            AutoRecordUiState(
                enabled = enabled,
                records = records,
                isLoading = false,
                learnedCount = learned.size,
            )
        },
        unmatchedNotificationRepository.unmatched,
    ) { core, unmatched ->
        core.copy(unmatched = unmatched)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutoRecordUiState(),
    )

    /** 切换自动记账开关 */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { autoRecordPreferences.setEnabled(enabled) }
    }

    /** 清空未识别通知队列 */
    fun clearUnmatched() {
        viewModelScope.launch { unmatchedNotificationRepository.clear() }
    }

    /** 重置分类学习表：清空后同商户会重新回落到内置关键词表 */
    fun resetCategoryLearning() {
        viewModelScope.launch { merchantCategoryRepository.clear() }
    }

    /**
     * 删除一条自动记录。
     *
     * **必须同时撤销账户余额影响**：自动记账落库时调过 `adjustBalance`，
     * 这里若只删交易不回滚余额，账户余额就会与流水脱节。
     * 与手动删除的逻辑一致，见 `TransactionEditViewModel.reverseAccountEffect`。
     */
    fun delete(item: AutoRecordItem) {
        viewModelScope.launch {
            when (item.tx.type) {
                TransactionType.EXPENSE -> accountRepository.adjustBalance(item.tx.accountId, item.tx.amount)
                TransactionType.INCOME -> accountRepository.adjustBalance(item.tx.accountId, -item.tx.amount)

                // 转账动了两个账户，只回滚出账方会让入账方余额永久虚高
                TransactionType.TRANSFER -> {
                    accountRepository.adjustBalance(item.tx.accountId, item.tx.amount)
                    item.tx.toAccountId?.let { toId ->
                        accountRepository.adjustBalance(toId, -item.tx.amount)
                    }
                }
            }
            transactionRepository.delete(item.tx.id)
            autoRecordPreferences.removeRecentId(item.tx.id)
        }
    }

    /** 清空复核队列（不删除已写入的交易） */
    fun clearQueue() {
        viewModelScope.launch { autoRecordPreferences.clearRecent() }
    }
}
