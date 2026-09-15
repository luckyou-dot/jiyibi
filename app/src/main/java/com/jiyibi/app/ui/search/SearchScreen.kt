package com.jiyibi.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.EmptyState
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.LoadingState
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.SwipeToDeleteItem
import com.jiyibi.app.core.designsystem.component.TransactionDayCard
import com.jiyibi.app.core.designsystem.component.TransactionDayHeader
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    // 金额输入框本地文本（搜索框值与 filters 同步；输入过程保留用户正在键入的字符串）
    var minAmountText by remember { mutableStateOf("") }
    var maxAmountText by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 1. 顶部搜索栏：返回按钮 + 毛玻璃圆角胶囊搜索框 + 筛选按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                // 毛玻璃圆角胶囊搜索框：GlassCard + 大圆角 Dp（RoundedCornerShape 会自动钳制为胶囊形）
                GlassCard(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 50.dp,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    OutlinedTextField(
                        value = state.filters.keyword,
                        onValueChange = viewModel::updateKeyword,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索备注/分类") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.filters.keyword.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateKeyword("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        // 透明边框与背景，让毛玻璃质感透出
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
                IconButton(onClick = { showFilters = !showFilters }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "筛选")
                }
            }

            // 2. 筛选面板（可折叠，默认隐藏）：UnifiedCard OUTLINED 包裹
            if (showFilters) {
                FilterPanel(
                    state = state,
                    minAmountText = minAmountText,
                    maxAmountText = maxAmountText,
                    onMinAmountChange = {
                        minAmountText = it
                        viewModel.updateAmountRange(it.toLongOrNull(), state.filters.maxAmount)
                    },
                    onMaxAmountChange = {
                        maxAmountText = it
                        viewModel.updateAmountRange(state.filters.minAmount, it.toLongOrNull())
                    },
                    onToggleCategory = viewModel::toggleCategory,
                    onToggleAccount = viewModel::toggleAccount,
                    onToggleTag = viewModel::toggleTag,
                    onToggleType = viewModel::toggleType,
                    onPickStart = { showStartPicker = true },
                    onPickEnd = { showEndPicker = true },
                    onClear = {
                        viewModel.clearFilters()
                        minAmountText = ""
                        maxAmountText = ""
                    },
                )
            }

            // 3. 结果列表（与首页「最近交易」同一套结构：按天分组 + 每天出入合计）
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.isLoading -> LoadingState()
                    state.resultGroups.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = "未找到匹配的交易",
                        subtitle = "试试调整关键词或筛选条件",
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.l),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        state.resultGroups.forEach { group ->
                            item(key = "day-${group.dayStart}") {
                                TransactionDayHeader(group = group)
                            }
                            item(key = "card-${group.dayStart}") {
                                TransactionDayCard(
                                    group = group,
                                    onRowClick = onEditTransaction,
                                ) { row, content ->
                                    SwipeToDeleteItem(
                                        onDelete = { viewModel.deleteTransaction(row.tx.id) },
                                        backgroundCorner = Corner.small,
                                    ) {
                                        content()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 起始日期选择对话框
    if (showStartPicker) {
        val startPickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.filters.startDate,
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        startPickerState.selectedDateMillis?.let {
                            viewModel.updateDateRange(it, state.filters.endDate)
                        }
                        showStartPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = startPickerState)
        }
    }

    // 结束日期选择对话框
    if (showEndPicker) {
        val endPickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.filters.endDate,
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        endPickerState.selectedDateMillis?.let {
                            viewModel.updateDateRange(state.filters.startDate, it)
                        }
                        showEndPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = endPickerState)
        }
    }
}

/**
 * 筛选面板：分类 / 账户 / 收支类型 / 标签 多选 + 日期范围 + 金额范围 + 清除按钮
 *
 * 外层使用 [UnifiedCard] 的 [UnifiedCardVariant.OUTLINED] 变体包裹，
 * 内部按段落纵向排列，紧凑布局以节省空间。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    state: SearchUiState,
    minAmountText: String,
    maxAmountText: String,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    onToggleCategory: (Long) -> Unit,
    onToggleAccount: (Long) -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleType: (TransactionType) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClear: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    UnifiedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        variant = UnifiedCardVariant.OUTLINED,
        cornerRadius = Corner.large,
        contentPadding = PaddingValues(Spacing.s),
    ) {
        // 收支类型多选：支出 / 收入 / 转账
        Text("收支类型", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = 5,
        ) {
            TransactionType.entries.forEach { type ->
                FilterChip(
                    selected = type in state.filters.types,
                    onClick = { onToggleType(type) },
                    label = {
                        Text(type.displayLabel(), style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }

        Spacer(Modifier.size(Spacing.s))

        // 分类多选：FlowRow + FilterChip（紧凑模式）
        Text("分类", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = 5,
        ) {
            state.categories.forEach { category ->
                FilterChip(
                    selected = category.id in state.filters.categoryIds,
                    onClick = { onToggleCategory(category.id) },
                    label = {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.size(Spacing.s))

        // 账户多选
        Text("账户", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = 5,
        ) {
            state.accounts.forEach { account ->
                FilterChip(
                    selected = account.id in state.filters.accountIds,
                    onClick = { onToggleAccount(account.id) },
                    label = {
                        Text(account.name, style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }

        // 标签多选（仅当存在历史标签时显示）
        if (state.existingTags.isNotEmpty()) {
            Spacer(Modifier.size(Spacing.s))
            Text("标签", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                maxItemsInEachRow = 5,
            ) {
                state.existingTags.forEach { tag ->
                    FilterChip(
                        selected = tag in state.filters.tags,
                        onClick = { onToggleTag(tag) },
                        label = {
                            Text(tag, style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.size(Spacing.s))

        // 日期范围：两个只读输入框 + CalendarMonth 图标 → DatePickerDialog
        Text("日期范围", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            OutlinedTextField(
                value = state.filters.startDate?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = { },
                readOnly = true,
                label = { Text("起始日期", style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    IconButton(onClick = onPickStart) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = "选择起始日期",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.filters.endDate?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = { },
                readOnly = true,
                label = { Text("结束日期", style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    IconButton(onClick = onPickEnd) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = "选择结束日期",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.size(Spacing.s))

        // 金额范围：Decimal 键盘 + ¥ 前缀
        Text("金额范围", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            OutlinedTextField(
                value = minAmountText,
                onValueChange = onMinAmountChange,
                label = { Text("最小金额", style = MaterialTheme.typography.labelSmall) },
                prefix = { Text("¥", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = maxAmountText,
                onValueChange = onMaxAmountChange,
                label = { Text("最大金额", style = MaterialTheme.typography.labelSmall) },
                prefix = { Text("¥", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }

        // 清除筛选
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClear) {
                Text("清除筛选", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 交易类型 → 中文标签（用于筛选面板展示） */
private fun TransactionType.displayLabel(): String = when (this) {
    TransactionType.EXPENSE -> "支出"
    TransactionType.INCOME -> "收入"
    TransactionType.TRANSFER -> "转账"
}

/**
 * 「查看全部」的交易行渲染已抽到共享组件 `TransactionRow` /
 * `TransactionDayCard`（见 `core/designsystem/component/TransactionList.kt`），
 * 与首页「最近交易」共用同一套实现，此处不再保留副本。
 */
