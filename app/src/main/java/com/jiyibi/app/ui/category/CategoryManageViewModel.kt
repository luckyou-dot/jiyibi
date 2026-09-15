package com.jiyibi.app.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiyibi.app.core.data.repository.MerchantCategoryRepository
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.CategoryKind
import com.jiyibi.app.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 分类管理页 UI 状态。 */
data class CategoryManageUiState(
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val merchantCategoryRepository: MerchantCategoryRepository,
) : ViewModel() {
    val uiState: StateFlow<CategoryManageUiState> = combine(
        categoryRepository.observeByKind(CategoryKind.EXPENSE.name),
        categoryRepository.observeByKind(CategoryKind.INCOME.name),
    ) { expense, income ->
        CategoryManageUiState(expense, income, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManageUiState())

    fun saveCategory(
        id: Long?,
        name: String,
        kind: CategoryKind,
        icon: String,
        color: Int,
        builtin: Boolean,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            categoryRepository.upsert(
                Category(
                    id = id ?: 0L,
                    name = name,
                    kind = kind,
                    icon = icon,
                    color = color,
                    builtin = builtin,
                ),
            )
            onDone()
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryRepository.delete(id)
            // 清理学习表里指向该分类的条目：否则会留下悬空 id，
            // 虽然读取端能兜底（查不到分类就回退静态表），但主动清理更干净
            merchantCategoryRepository.forgetCategory(id)
        }
    }
}
