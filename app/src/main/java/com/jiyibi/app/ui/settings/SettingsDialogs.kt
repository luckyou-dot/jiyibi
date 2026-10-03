package com.jiyibi.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.theme.AppTheme
import com.jiyibi.app.core.designsystem.theme.paletteOf
import com.jiyibi.app.core.domain.model.Account

/** 主题色板预览：圆形渐变，由主色 → 辅色构成。
 */
@Composable
internal fun ThemeSwatch(theme: AppTheme, size: androidx.compose.ui.unit.Dp) {
    val palette = paletteOf(theme)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.lightPrimary, palette.lightSecondary),
                ),
            ),
    )
}

/**
 * 主题风格选择对话框：列出 4 种主题，每个选项左侧为色板预览，右侧为当前选中标记。
 */
@Composable
internal fun ThemePickerDialog(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题风格") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    val selected = theme == currentTheme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(theme)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemeSwatch(theme = theme, size = 28.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = theme.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                text = themeDescription(theme),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 各主题的简短描述 */
private fun themeDescription(theme: AppTheme): String = when (theme) {
    AppTheme.MINT -> "青绿渐变 · 清爽自然"
    AppTheme.VIBRANT -> "紫粉渐变 · 年轻时尚"
    AppTheme.SUNSET -> "橙红渐变 · 温暖治愈"
    AppTheme.MORANDI -> "低饱和灰调 · 高级优雅"
}

/**
 * 默认账户副标题：两个账户都未设置时显示「未设置」，
 * 否则显示「支出 {name} · 收入 {name}」（未设置的项显示「未设置」）。
 */
internal fun defaultAccountSubtitle(expenseName: String?, incomeName: String?): String {
    if (expenseName == null && incomeName == null) return "未设置"
    return "支出 ${expenseName ?: "未设置"} · 收入 ${incomeName ?: "未设置"}"
}

/**
 * 「分类与账户」二级选择弹窗：点选后进入对应管理页面。
 */
@Composable
internal fun CategoryAccountPickerDialog(
    onOpenCategory: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类与账户") },
        text = {
            Column {
                PickerEntryRow(
                    icon = Icons.Filled.Category,
                    title = "分类管理",
                    subtitle = "管理收支分类与图标",
                    onClick = onOpenCategory,
                )
                PickerEntryRow(
                    icon = Icons.Filled.AccountBalance,
                    title = "账户管理",
                    subtitle = "管理账户与余额",
                    onClick = onOpenAccount,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 「分类与账户」弹窗内的单行入口 */
@Composable
private fun PickerEntryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 「周期与借贷」二级选择弹窗：点选后进入对应管理页面。
 */
@Composable
internal fun RecurringDebtPickerDialog(
    onOpenRecurring: () -> Unit,
    onOpenDebt: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("周期与借贷") },
        text = {
            Column {
                PickerEntryRow(
                    icon = Icons.Filled.Repeat,
                    title = "周期性记账",
                    subtitle = "自动记录固定收支",
                    onClick = onOpenRecurring,
                )
                PickerEntryRow(
                    icon = Icons.Filled.Handshake,
                    title = "借贷记录",
                    subtitle = "跟踪应收应付",
                    onClick = onOpenDebt,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 「默认账户」选择对话框：同时设置默认支出账户与默认收入账户。
 *
 * 列出全部账户，分「支出账户」「收入账户」两个区块，选中项右侧带 Check 标记。
 * 选择后不自动关闭，便于用户连续设置两项后再手动关闭。
 * 当无可用账户时提示用户先创建账户。
 *
 * @param accounts           全部可用账户
 * @param expenseAccountId   当前默认支出账户 id（null 表示未设置）
 * @param incomeAccountId    当前默认收入账户 id（null 表示未设置）
 * @param onSelectExpense    选择支出账户回调
 * @param onSelectIncome     选择收入账户回调
 * @param onDismiss          关闭弹窗回调
 */
@Composable
internal fun DefaultAccountPickerDialog(
    accounts: List<Account>,
    expenseAccountId: Long?,
    incomeAccountId: Long?,
    onSelectExpense: (Long) -> Unit,
    onSelectIncome: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认账户") },
        text = {
            if (accounts.isEmpty()) {
                Text("暂无可用账户，请先在「分类与账户」中创建账户。")
            } else {
                Column {
                    // 支出账户区块
                    Text(
                        "支出账户",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    accounts.forEach { account ->
                        AccountPickerRow(
                            account = account,
                            selected = account.id == expenseAccountId,
                            onClick = { onSelectExpense(account.id) },
                        )
                    }
                    Spacer(Modifier.size(Spacing.s))
                    // 收入账户区块
                    Text(
                        "收入账户",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    accounts.forEach { account ->
                        AccountPickerRow(
                            account = account,
                            selected = account.id == incomeAccountId,
                            onClick = { onSelectIncome(account.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 默认账户弹窗内的单行账户项：色块 + 名称，选中项右侧带 Check */
@Composable
private fun AccountPickerRow(
    account: Account,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(account.color)),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
