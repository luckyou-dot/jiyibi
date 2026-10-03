package com.jiyibi.app.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiyibi.app.core.designsystem.component.AnimatedNumber
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.theme.BudgetAmber
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.domain.model.centsToYuan
import androidx.compose.ui.platform.LocalContext
import com.jiyibi.app.core.common.AppVersion

/**
 * 顶部渐变 Hero 区：使用主题渐变作为背景，自顶部状态栏延伸而下。
 *
 * 内部包含：
 * 1. 透明 TopAppBar 占位（64dp，与叠加的 TopAppBar 高度对齐）
 * 2. [HeaderItem]：应用图标（圆形毛玻璃底）+ 名称 + 版本号
 * 3. [AssetBoardCard]：毛玻璃资产看板，三栏金额展示
 */
/**
 * 顶部 Hero 内容区：应用图标 + 名称 + 版本 + 毛玻璃资产看板。
 */
@Composable
internal fun HeroContent(
    board: AssetBoard,
    onEditAssets: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // 应用图标 + 名称 + 版本号（白色文字保证渐变上可读）
        HeaderItem()

        Spacer(Modifier.height(Spacing.m))

        // 毛玻璃资产看板：叠加在 Hero 底部
        AssetBoardCard(
            board = board,
            onEditAssets = onEditAssets,
        )

        Spacer(Modifier.height(Spacing.l))
    }
}

/**
 * 顶部 Header：居中显示应用 Logo（72dp 圆形毛玻璃底 + 钱包图标）、应用名、版本号。
 * 文字使用白色，确保在渐变背景上可读。
 */
@Composable
internal fun HeaderItem() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // 72dp 圆形毛玻璃底包裹钱包图标
        GlassCard(
            modifier = Modifier.size(72.dp),
            // 72dp 尺寸下 36dp 圆角即为正圆
            cornerRadius = 36.dp,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White,
                )
            }
        }
        Text(
            text = "记一笔",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        // 版本号运行时读取：BuildConfig 常量会被内联进字节码，增量编译下
        // 未改动的本文件可能一直带着旧值（曾显示 v1.05 而 manifest 是 1.16）
        val context = LocalContext.current
        val versionName = remember(context) { AppVersion.name(context) }
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/**
 * 资产看板卡片：总资产 / 本月结余 / 待收 三栏展示，使用毛玻璃效果叠加在 Hero 上。
 *
 * 数字使用 [AnimatedNumber]（等宽字体 + 白色），列间用 [VerticalDivider] 分隔。
 * 点击「总资产」列触发 [onEditAssets]，允许用户手动修改总资产。
 */
@Composable
private fun AssetBoardCard(
    board: AssetBoard,
    onEditAssets: () -> Unit,
) {
    // 等宽字体样式，保证数字滚动时宽度稳定
    val numberStyle = MaterialTheme.typography.titleMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
    // 本月结余：正数绿色（结余），负数红色（超支）
    val balanceColor = if (board.monthBalance >= 0) IncomeGreen else ExpenseRed
    val balancePrefix = if (board.monthBalance >= 0) "¥" else "-¥"
    val balanceValue = kotlin.math.abs(board.monthBalance.centsToYuan().toDouble())

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l),
        contentPadding = PaddingValues(Spacing.m),
    ) {
        Column {
            // 顶部行：标题 + 「本月」半透明 AssistChip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "💰 资产看板",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text("本月", color = Color.White) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White.copy(alpha = 0.25f),
                        labelColor = Color.White,
                    ),
                )
            }
            Spacer(Modifier.height(Spacing.s))
            // 三栏金额展示：总资产白、本月结余绿/红、待收黄
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 总资产：可点击编辑
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEditAssets),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "总资产",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Box {
                        AnimatedNumber(
                            targetValue = board.totalAssets.centsToYuan().toDouble(),
                            prefix = "¥",
                            style = numberStyle,
                            color = Color.White,
                        )
                    }
                }
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = Color.White.copy(alpha = 0.3f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "本月结余",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    AnimatedNumber(
                        targetValue = balanceValue,
                        prefix = balancePrefix,
                        style = numberStyle,
                        color = balanceColor,
                    )
                }
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = Color.White.copy(alpha = 0.3f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "待收",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    AnimatedNumber(
                        targetValue = board.pendingReceivable.centsToYuan().toDouble(),
                        prefix = "¥",
                        style = numberStyle,
                        color = BudgetAmber,
                    )
                }
            }
        }
    }
}
