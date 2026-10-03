package com.jiyibi.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.CategoryKind
import com.jiyibi.app.core.domain.model.RecurringRule
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.CategoryRepository
import com.jiyibi.app.core.domain.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 周期性记账列表页 UI 状态。 */
data class RecurringListUiState(
    val rules: List<RecurringRule> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class RecurringListViewModel @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<RecurringListUiState> = combine(
        recurringRepository.observeAll(),
        accountRepository.observeAll(),
        categoryRepository.observeByKind(CategoryKind.EXPENSE.name),
        categoryRepository.observeByKind(CategoryKind.INCOME.name),
    ) { rules, accounts, expense, income ->
        RecurringListUiState(
            rules = rules,
            accounts = accounts,
            expenseCategories = expense,
            incomeCategories = income,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringListUiState(),
    )

    /** 新增或更新一条规则，完成后回调 onDone。 */
    fun saveRule(rule: RecurringRule, onDone: () -> Unit) {
        viewModelScope.launch { recurringRepository.upsert(rule); onDone() }
    }

    /** 切换规则的启用/暂停状态。 */
    fun toggleEnabled(id: Long, current: RecurringRule) {
        viewModelScope.launch {
            recurringRepository.upsert(current.copy(enabled = !current.enabled))
        }
    }

    /** 切换自动记账开关（无需打开编辑对话框）。 */
    fun toggleAutoRecord(current: RecurringRule) {
        viewModelScope.launch {
            recurringRepository.upsert(current.copy(autoRecord = !current.autoRecord))
        }
    }

    /** 删除一条规则。 */
    fun deleteRule(id: Long) {
        viewModelScope.launch { recurringRepository.delete(id) }
    }
}
