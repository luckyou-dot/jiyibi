package com.jiyibi.app.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.jiyibi.app.core.designsystem.component.AnimatedProgressIndicator
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.component.categoryIconByKey
import com.jiyibi.app.core.designsystem.component.listItemEnterAnimation
import com.jiyibi.app.core.designsystem.theme.BudgetAmber
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.domain.model.CategoryStat
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.centsToYuan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== 分类占比列表 ====================

@Composable
internal fun CategorySection(
    stats: List<CategoryStat>,
    expandCategoryId: Long?,
    onToggleExpand: (Long) -> Unit,
    categoryTransactions: List<Transaction>,
) {
    // 调色板循环：主题色调色板
    val palette = listOf(
        ExpenseRed,
        IncomeGreen,
        BudgetAmber,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    // 占比分母：所有分类金额之和
    val total = stats.sumOf { it.total }.coerceAtLeast(1L)

    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
    ) {
        Text("分类占比", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.s))
        if (stats.isEmpty()) {
            Text(
                "暂无分类数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 按 total 降序
            stats.sortedByDescending { it.total }.forEachIndexed { idx, stat ->
                val color = palette[idx % palette.size]
                val percent = stat.total.toFloat() / total.toFloat()
                CategoryRow(
                    stat = stat,
                    color = color,
                    percent = percent,
                    expanded = expandCategoryId == stat.categoryId,
                    onClick = { onToggleExpand(stat.categoryId) },
                    transactions = if (expandCategoryId == stat.categoryId) categoryTransactions else emptyList(),
                    index = idx,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    stat: CategoryStat,
    color: Color,
    percent: Float,
    expanded: Boolean,
    onClick: () -> Unit,
    transactions: List<Transaction>,
    index: Int = 0,
) {
    // 用分类色，若 color==0 则回退到调色板色
    val displayColor = if (stat.color != 0) Color(stat.color) else color
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .listItemEnterAnimation(index)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // 分类图标（圆形背景 + 分类色 + 图标）
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(displayColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIconByKey(stat.icon),
                    contentDescription = null,
                    tint = displayColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                stat.categoryName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(percent * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 金额：百分比后追加，labelMedium + 加粗 + 起始 8dp 间距
            Text(
                "¥${stat.total.centsToYuan().toPlainString()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        // 占比进度条：动效 + 主题色调色板色
        AnimatedProgressIndicator(
            progress = percent,
            modifier = Modifier.fillMaxWidth(),
            height = 6.dp,
            indicatorColor = displayColor,
        )
        // 展开下钻：该分类交易明细列表（用 UnifiedCard ELEVATED 包裹）
        if (expanded) {
            if (transactions.isEmpty()) {
                Text(
                    "暂无交易记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            } else {
                UnifiedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.s),
                    variant = UnifiedCardVariant.ELEVATED,
                    contentPadding = PaddingValues(Spacing.m),
                ) {
                    transactions.forEach { tx ->
                        CategoryTransactionRow(
                            type = tx.type,
                            amount = tx.amount,
                            note = tx.note,
                            date = tx.date,
                            dateFormat = dateFormat,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** 单条交易明细行：左日期+备注，右金额（收入绿/支出红） */
@Composable
private fun CategoryTransactionRow(
    type: TransactionType,
    amount: Long,
    note: String,
    date: Long,
    dateFormat: SimpleDateFormat,
) {
    val isIncome = type == TransactionType.INCOME
    val prefix = if (isIncome) "+" else "-"
    val color = if (isIncome) IncomeGreen else ExpenseRed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.ifBlank { if (isIncome) "收入" else "支出" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = dateFormat.format(Date(date)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "$prefix ¥${amount.centsToYuan().toPlainString()}",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}
