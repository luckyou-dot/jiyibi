package com.jiyibi.app.ui.statistics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.designsystem.theme.gradientBrush
import com.jiyibi.app.core.domain.model.AccountType
import com.jiyibi.app.core.domain.model.StatPeriod
import com.jiyibi.app.core.domain.model.centsToYuan
import com.jiyibi.app.ui.yearreview.YearReviewScreen

/** Hero 渐变区高度：容纳透明 TopAppBar + 毛玻璃汇总卡 + 半透明时间档选择条 */
private val HeroHeight = 325.dp

/**
 * 统计页：日/周/月/年账单总览、分类占比列表、支出趋势折线图、同比环比。
 *
 * 顶部为渐变 Hero 区（透明 TopAppBar + 毛玻璃汇总卡 + 半透明时间档选择条），
 * 下方为各账户占比、分类占比（可下钻）、支出趋势折线图。年档渲染年度回顾。
 *
 * 作为底部 Tab 入口，不传 onBack。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 当前展开的分类 id（点击同分类再次折叠）
    var expandCategoryId by remember { mutableStateOf<Long?>(null) }
    // 当前展开分类的交易明细
    val categoryTransactions by viewModel.selectedCategoryTransactions.collectAsStateWithLifecycle()

    val tabs = remember {
        listOf(
            StatPeriod.DAILY to "日",
            StatPeriod.WEEKLY to "周",
            StatPeriod.MONTHLY to "月",
            StatPeriod.YEARLY to "年",
        )
    }
    val selectedIndex = tabs.indexOfFirst { it.first == state.period }.coerceAtLeast(0)

    // 根 Box：底层渐变 Hero 背景 + 透明 Scaffold 叠加，使 TopAppBar 透明地浮于渐变之上
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部渐变 Hero 背景层：固定高度，位于屏幕顶部
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
                    title = { Text("统计", color = MaterialTheme.colorScheme.onPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            // 空状态：trend 为空且非年档 → EmptyState
            if (state.trend.isEmpty() && state.period != StatPeriod.YEARLY) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.BarChart,
                        title = "暂无数据",
                        subtitle = "当前周期内还没有交易记录",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(
                        start = Spacing.l,
                        end = Spacing.l,
                        top = Spacing.l,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    // 1. Hero 毛玻璃汇总卡（支出/收入 AnimatedNumber + 环比/同比 Chip）
                    item { SummaryCard(state) }

                    // 2. 时间档选择条（日/周/月/年）半透明叠加在 Hero 底部
                    item {
                        PeriodSelectorRow(
                            selectedIndex = selectedIndex,
                            tabs = tabs,
                            onSelect = { viewModel.setPeriod(it) },
                        )
                    }

                    if (state.period == StatPeriod.YEARLY) {
                        // 年档：渲染年度回顾视图，替代普通图表
                        item { YearReviewScreen() }
                    } else {
                        // 3. 各账户消费占比卡片
                        item { AccountStatCard(stats = state.accountStats) }

                        // 4. 分类占比列表（可点击下钻）
                        item {
                            CategorySection(
                                stats = state.categoryStats,
                                expandCategoryId = expandCategoryId,
                                onToggleExpand = { id ->
                                    expandCategoryId = if (expandCategoryId == id) null else id
                                    viewModel.toggleCategory(id)
                                },
                                categoryTransactions = categoryTransactions,
                            )
                        }

                        // 5. 支出趋势折线图
                        item { TrendSection(trend = state.trend, period = state.period) }
                    }
                }
            }
        }
    }
}

// ==================== Hero 毛玻璃汇总卡 ====================

@Composable
private fun SummaryCard(state: StatisticsUiState) {
    val periodLabel = when (state.period) {
        StatPeriod.DAILY -> "本日"
        StatPeriod.WEEKLY -> "本周"
        StatPeriod.MONTHLY -> "本月"
        StatPeriod.YEARLY -> "本年"
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.l),
    ) {
        // 上：支出 + 收入（AnimatedNumber 数字滚动 + 等宽字体，白色保证渐变上可读）
        // 两栏各占 weight(1f)，保证区域大小一致；支出/收入统一用 titleLarge 字号
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$periodLabel 支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(Spacing.xs))
                AnimatedNumber(
                    targetValue = state.totalExpense.centsToYuan().toDouble(),
                    prefix = "¥",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    color = ExpenseRed,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "$periodLabel 收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(Spacing.xs))
                AnimatedNumber(
                    targetValue = state.totalIncome.centsToYuan().toDouble(),
                    prefix = "¥",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    color = IncomeGreen,
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        // 下：环比 + 同比 Chip（仅月档有值，其他档显示「—」占位）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ChangeChip(
                label = "环比上期",
                change = state.momChange,
                modifier = Modifier.weight(1f),
            )
            ChangeChip(
                label = "同比去年",
                change = state.yoyChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 环比/同比 chip：半透明白底胶囊，值用语义色（上升→红、下降→绿、未知→白灰）
 */
@Composable
private fun ChangeChip(
    label: String,
    change: Float?,
    modifier: Modifier = Modifier,
) {
    val text = if (change == null) {
        "—"
    } else {
        val sign = if (change >= 0f) "+" else ""
        "${sign}${(change * 100).toInt()}%"
    }
    val valueColor = when {
        change == null -> Color.White.copy(alpha = 0.7f)
        change >= 0f -> ExpenseRed   // 支出上升 → 红
        else -> IncomeGreen           // 支出下降 → 绿
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = valueColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ==================== 时间档选择条（半透明叠加 Hero 底部） ====================

/**
 * 日/周/月/年 选择条：浅灰胶囊容器，选中态主题色实底。
 *
 * 选中态 primary 实底 + 白字，未选用 surfaceVariant + onSurfaceVariant，
 * 确保在 Hero 渐变区与白色背景区均可读。
 */
@Composable
private fun PeriodSelectorRow(
    selectedIndex: Int,
    tabs: List<Pair<StatPeriod, String>>,
    onSelect: (StatPeriod) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            tabs.forEachIndexed { i, (period, label) ->
                val selected = selectedIndex == i
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    onClick = { onSelect(period) },
                ) {
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.s),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ==================== 各账户消费占比 ====================

@Composable
private fun AccountStatCard(stats: List<AccountStat>) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
    ) {
        Text("各账户消费占比", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.s))
        if (stats.isEmpty()) {
            Text(
                "暂无支出数据",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            stats.forEachIndexed { idx, stat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .listItemEnterAnimation(idx)
                        .padding(vertical = Spacing.xs),
                ) {
                    Text(
                        stat.account.name,
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // 动效进度条：账户色取语义色（深色模式下仍可见）
                    AnimatedProgressIndicator(
                        progress = stat.percentage,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.s),
                        height = 6.dp,
                        indicatorColor = accountColor(stat.account.type),
                    )
                    Text(
                        "¥${stat.amount.centsToYuan().toPlainString()}",
                        modifier = Modifier.padding(start = Spacing.s),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${(stat.percentage * 100).toInt()}%",
                        modifier = Modifier.padding(start = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 账户类型对应的主色（语义色，深色模式下仍可见） */
private fun accountColor(type: AccountType): Color = when (type) {
    AccountType.WECHAT -> Color(0xFF43A047) // 绿
    AccountType.ALIPAY -> Color(0xFF1E88E5) // 蓝
    AccountType.CASH -> Color(0xFFFFB300) // 琥珀
    AccountType.BANK -> Color(0xFF8E24AA) // 紫
    AccountType.CREDIT_CARD -> Color(0xFFE53935) // 红
    AccountType.OTHER -> Color(0xFF607D8B) // 蓝灰
}

