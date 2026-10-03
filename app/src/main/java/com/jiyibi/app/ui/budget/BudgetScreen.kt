package com.jiyibi.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.designsystem.component.AnimatedNumber
import com.jiyibi.app.core.designsystem.component.AnimatedProgressIndicator
import com.jiyibi.app.core.designsystem.component.EmptyState
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.component.listItemEnterAnimation
import com.jiyibi.app.core.designsystem.theme.BudgetAmber
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.designsystem.theme.gradientBrush
import com.jiyibi.app.core.domain.model.centsToYuan

/**
 * 预算页：月度总预算 + 分类预算列表，按月统计已用与剩余。
 *
 * 顶部为渐变 Hero 区（透明 TopAppBar + 毛玻璃总预算卡），与首页/统计页风格一致。
 */

/** Hero 渐变区高度：从状态栏延伸至分类预算与+添加板块的正上方 */
private val HeroHeight = 263.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onAddBudget: () -> Unit,
    onAddCategoryBudget: () -> Unit,
    onEditBudget: (Long) -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val totalBudget = state.totalBudget
    val overspent = totalBudget != null && totalBudget.amountLimit > 0 &&
        state.totalUsed.toFloat() / totalBudget.amountLimit > 1f

    // 根 Box：底层渐变 Hero 背景 + 透明 Scaffold 叠加，使 TopAppBar 透明地浮于渐变之上
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部渐变 Hero 背景层：固定高度，位于屏幕顶部，不随滚动变化
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroHeight)
                .background(gradientBrush()),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("预算", color = MaterialTheme.colorScheme.onPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAddCategoryBudget,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("添加预算") },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { TotalBudgetCard(state, onAddBudget) }
                item {
                    // 分类预算标题 + 添加按钮行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 分类预算标题：胶囊形状 + 主题色浅底
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "分类预算",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // 自定义胶囊按钮：主题色背景 + 白色文字图标
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onAddCategoryBudget)
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White,
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    "添加",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                if (state.categoryBudgets.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Wallet,
                            title = "还没有分类预算",
                            subtitle = "点击右下角添加",
                            actionText = "添加",
                            onAction = onAddCategoryBudget,
                        )
                    }
                } else {
                    itemsIndexed(state.categoryBudgets, key = { _, it -> it.budget.id }) { index, item ->
                        // 入场动画修饰符作用于外层容器，保持 CategoryBudgetRow 签名不变
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .listItemEnterAnimation(index),
                        ) {
                            CategoryBudgetRow(item, onClick = { onEditBudget(item.budget.id) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 月度总预算 Hero 卡：毛玻璃叠加在 Hero 渐变之上，
 * 额度/已用/剩余三栏(AnimatedNumber 等宽白色) + 动效进度条阈值变色。
 */
@Composable
private fun TotalBudgetCard(state: BudgetUiState, onAddBudget: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.l),
    ) {
        if (state.totalBudget == null) {
            // 未设置月度预算：毛玻璃卡内提示并引导设置
            Text(
                "本月总预算",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                "未设置月度预算",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = onAddBudget) { Text("设置总预算") }
        } else {
            val limit = state.totalBudget.amountLimit
            val used = state.totalUsed
            val remain = limit - used
            val ratio = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 2f) else 0f
            val overspent = ratio > 1f
            Text(
                "本月总预算",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(Spacing.m))
            // 额度 / 已用 / 剩余 三栏：数字 AnimatedNumber 滚动
            // 额度绿、已用红、剩余黄，便于直观区分
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BudgetHeroColumn("额度", limit, IncomeGreen)
                BudgetHeroColumn("已用", used, ExpenseRed)
                BudgetHeroColumn("剩余", remain, BudgetAmber)
            }
            Spacer(Modifier.height(Spacing.m))
            // 进度条颜色按阈值：<0.8 白色 / 0.8~1.0 琥珀 / >1 红色
            AnimatedProgressIndicator(
                progress = ratio.coerceAtMost(1f),
                modifier = Modifier.fillMaxWidth(),
                height = 10.dp,
                trackColor = Color.White.copy(alpha = 0.25f),
                indicatorColor = when {
                    ratio > 1f -> ExpenseRed
                    ratio >= 0.8f -> BudgetAmber
                    else -> Color.White
                },
            )
            if (overspent) {
                Spacer(Modifier.height(Spacing.xs))
                Text("已超支", style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
            }
        }
    }
}

/** Hero 卡内的单栏：标签 + AnimatedNumber 滚动金额（按语义着色）。 */
@Composable
private fun BudgetHeroColumn(label: String, amountCents: Long, amountColor: Color) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
        )
        AnimatedNumber(
            targetValue = amountCents.centsToYuan().toDouble(),
            prefix = "¥",
            style = MaterialTheme.typography.titleLarge,
            color = amountColor,
        )
    }
}

/** 单个分类预算行：UnifiedCard(ELEVATED) + 分类色点 + 名称 + 动效进度条 + 已用/额度 + 阈值提示 */
@Composable
private fun CategoryBudgetRow(item: CategoryBudgetItem, onClick: () -> Unit) {
    val budget = item.budget
    val category = item.category
    val limit = budget.amountLimit
    val used = item.used
    val ratio = item.progress
    val threshold = budget.alertThreshold
    val dotColor = category?.let { cat ->
        if (cat.color != 0) Color(cat.color) else MaterialTheme.colorScheme.outline
    } ?: MaterialTheme.colorScheme.outline

    UnifiedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        variant = UnifiedCardVariant.ELEVATED,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.size(Spacing.s))
                Text(
                    category?.name ?: "未分类",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                "已用 ¥${used.centsToYuan().toPlainString()} / ¥${limit.centsToYuan().toPlainString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.s))
        // 动效进度条，阈值变色：<threshold 主色 / ≥threshold 琥珀 / >1 红色
        AnimatedProgressIndicator(
            progress = ratio.coerceAtMost(1f),
            modifier = Modifier.fillMaxWidth(),
            indicatorColor = when {
                ratio > 1f -> ExpenseRed
                ratio >= threshold -> BudgetAmber
                else -> MaterialTheme.colorScheme.primary
            },
        )
        if (ratio > 1f) {
            Spacer(Modifier.height(Spacing.xs))
            Text("已超支", style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
        } else if (ratio >= threshold) {
            Spacer(Modifier.height(Spacing.xs))
            Text("接近预算", style = MaterialTheme.typography.labelSmall, color = BudgetAmber)
        }
    }
}
