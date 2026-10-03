package com.jiyibi.app.ui.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.RecurringFrequency
import com.jiyibi.app.core.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记一笔页的各个分区组件。
 *
 * 从 `TransactionEditScreen.kt` 拆出：表单状态与保存编排留在 Screen（[TransactionEditScreen]），
 * 各分区只负责渲染，状态通过参数与回调进出。拆分的动机是原文件接近 900 行、
 * 视觉区块与业务编排混在一个函数体里，改一处要在大段缩进里找位置。
 *
 * 这些组件全部是 `internal`：只在编辑页内使用，不对外暴露。
 */

/** 金额 Hero 卡：收支分段按钮 + 大字号金额输入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AmountHeroCard(
    currentType: TransactionType,
    amountText: String,
    onTypeSelect: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    focusRequester: FocusRequester,
    noteFocusRequester: FocusRequester,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Corner.large,
        contentPadding = PaddingValues(Spacing.m),
    ) {
        // 1. 分段按钮:支出/收入(去掉转账)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEachIndexed { idx, type ->
                SegmentedButton(
                    selected = currentType == type,
                    onClick = { onTypeSelect(type) },
                    shape = SegmentedButtonDefaults.itemShape(idx, 2),
                ) {
                    Text(type.label())
                }
            }
        }

        Spacer(Modifier.height(Spacing.s))

        // 2. 金额输入:大字等宽,¥ 前缀主题色,文字按收支语义着色
        val amountColor = if (currentType == TransactionType.EXPENSE) ExpenseRed else IncomeGreen
        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            label = { Text("金额") },
            prefix = { Text("¥", color = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = amountColor,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                // 金额回车直接跳到备注,不用手动点过去
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { noteFocusRequester.requestFocus() },
            ),
            shape = RoundedCornerShape(Corner.medium),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

/** 分类选择卡：4 列分类网格 + 「新建分类」入口。 */
@Composable
internal fun CategoryPickCard(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddCategory: () -> Unit,
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        contentPadding = PaddingValues(Spacing.m),
    ) {
        Text("选择分类", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.s))
        CategoryGrid(
            categories = categories,
            selectedId = selectedId,
            onSelect = onSelect,
            onAddCategory = onAddCategory,
        )
    }
}

/** 详情卡：备注 + 账户 + 日期。 */
@Composable
internal fun TransactionDetailCard(
    note: String,
    onNoteChange: (String) -> Unit,
    accounts: List<Account>,
    selectedAccountId: Long?,
    onAccountSelect: (Long) -> Unit,
    selectedDate: Long,
    onPickDate: () -> Unit,
    noteFocusRequester: FocusRequester,
    onNoteDone: () -> Unit,
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        contentPadding = PaddingValues(Spacing.m),
    ) {
        Text("详情", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.s))
        // 4. 备注
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("备注") },
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(Corner.medium),
            // 备注键盘「完成」直接保存,少一步收键盘/滚动
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onNoteDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(noteFocusRequester),
        )
        Spacer(Modifier.height(Spacing.s))
        // 5. 账户选择
        AccountSelector(
            accounts = accounts,
            selectedId = selectedAccountId,
            onSelect = onAccountSelect,
        )
        Spacer(Modifier.height(Spacing.s))
        // 6. 日期选择
        OutlinedTextField(
            value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(selectedDate)),
            onValueChange = { },
            readOnly = true,
            label = { Text("日期") },
            trailingIcon = {
                IconButton(onClick = onPickDate) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "选择日期")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(Corner.medium),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 周期性记账卡：开关 + 频率 Chip + 下次执行时间。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    frequency: RecurringFrequency,
    onFrequencyChange: (RecurringFrequency) -> Unit,
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        contentPadding = PaddingValues(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Repeat, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.s))
            Text("周期性记账", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            Spacer(Modifier.height(Spacing.s))
            Text("频率", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(
                    "每日" to RecurringFrequency.DAILY,
                    "每周" to RecurringFrequency.WEEKLY,
                    "每月" to RecurringFrequency.MONTHLY,
                    "每年" to RecurringFrequency.YEARLY,
                ).forEach { (label, freq) ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { onFrequencyChange(freq) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            val nextRunText = SimpleDateFormat("yyyy-MM-dd E", Locale.getDefault())
                .format(Date(calculateNextRun(frequency)))
            Text(
                "下次自动记录:$nextRunText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 标签卡：已选标签 Chip 行 + 「+ 添加」入口。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsCard(
    selectedTags: List<String>,
    onRemoveTag: (String) -> Unit,
    onOpenPicker: () -> Unit,
) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        contentPadding = PaddingValues(Spacing.m),
    ) {
        // 已选标签 Chip 行
        if (selectedTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                selectedTags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, "移除", Modifier.size(12.dp))
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
        }
        // 标签输入行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.s))
            Text(if (selectedTags.isEmpty()) "添加标签" else "标签", modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenPicker) {
                Text("+ 添加")
            }
        }
    }
}

/**
 * 标签选择底部弹窗。
 *
 * 没有任何历史标签时展示一组推荐标签，让第一次记账也能一键选标签。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerSheet(
    existingTags: List<String>,
    selectedTags: List<String>,
    newTagText: String,
    onNewTagChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddNewTag: () -> Unit,
    onDismiss: () -> Unit,
) {
    val recommendedTags = remember {
        listOf("刚需", "可选", "报销中", "AA待收", "家人", "朋友")
    }
    val displayTags = if (existingTags.isNotEmpty()) existingTags else recommendedTags
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("选择标签", style = MaterialTheme.typography.titleMedium)
            if (selectedTags.isNotEmpty()) {
                Text(
                    "已选:${selectedTags.joinToString("、")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                displayTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag) },
                    )
                }
            }
            if (existingTags.isEmpty()) {
                Text(
                    "以上为推荐标签,开始记账后会按你的使用习惯排序",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s))
            OutlinedTextField(
                value = newTagText,
                onValueChange = onNewTagChange,
                label = { Text("自定义标签") },
                singleLine = true,
                shape = RoundedCornerShape(Corner.medium),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = onAddNewTag) { Icon(Icons.Filled.Add, "添加") }
                },
            )
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
}
