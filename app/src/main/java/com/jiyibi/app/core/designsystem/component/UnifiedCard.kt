package com.jiyibi.app.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiyibi.app.core.designsystem.theme.LocalGradientColors

/** 间距令牌：统一各页面的留白节奏。 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/** 圆角令牌：small/medium/large/xlarge 为常用半径，full 用于胶囊形。 */
object Corner {
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xlarge = 20.dp
    val full = CircleShape
}

/** 统一卡片支持的视觉变体。 */
enum class UnifiedCardVariant {
    /** 微阴影纯色：surface 背景 + 轻微 tonal/shadow elevation。 */
    ELEVATED,

    /** 细描边：surface 背景 + 1dp outlineVariant 描边。 */
    OUTLINED,

    /** 主题色渐变：使用 LocalGradientColors 的线性渐变作为背景。 */
    GRADIENT,

    /** 毛玻璃半透明：半透明背景 + 模糊（API 31+），低版本/深色模式降级描边。 */
    GLASS,
}

/**
 * 统一卡片组件：通过 [variant] 切换四种视觉风格，统一间距与圆角令牌。
 *
 * 所有变体内部均以 [Column] + [contentPadding] 包裹 [content]，
 * 调用方可直接使用 [ColumnScope] 上下文排列纵向内容。
 *
 * @param modifier 外部修饰符
 * @param variant 视觉变体，默认 [UnifiedCardVariant.ELEVATED]
 * @param cornerRadius 圆角半径，默认 [Corner.large]
 * @param contentPadding 内容内边距，默认 [Spacing.l]
 * @param content 卡片内容（ColumnScope）
 */
@Composable
fun UnifiedCard(
    modifier: Modifier = Modifier,
    variant: UnifiedCardVariant = UnifiedCardVariant.ELEVATED,
    cornerRadius: Dp = Corner.large,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    when (variant) {
        UnifiedCardVariant.ELEVATED -> {
            Surface(
                modifier = modifier,
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }

        UnifiedCardVariant.OUTLINED -> {
            Surface(
                modifier = modifier,
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }

        UnifiedCardVariant.GRADIENT -> {
            val colors = LocalGradientColors.current
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(colors.start, colors.end))),
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }

        UnifiedCardVariant.GLASS -> {
            val darkTheme = isSystemInDarkTheme()
            val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            // 背景半透明颜色：API 31+ 更通透（渐变背景透出更多），低版本降级时更不透明以保持可读性
            val bgColor = when {
                darkTheme -> Color.Black.copy(alpha = 0.75f)
                isBlurSupported -> Color.White.copy(alpha = 0.4f)
                else -> Color.White.copy(alpha = 0.85f)
            }
            // 深色模式或低版本降级时补充细描边，强化边界层次
            val showBorder = darkTheme || !isBlurSupported
            Box(
                modifier = modifier
                    .clip(shape)
                    .then(
                        if (showBorder) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                // 背景层：半透明底色。
                // 说明：这里曾使用 Modifier.blur(15.dp) 追求毛玻璃效果，但该图层内只有一个纯色
                // 矩形（bgColor），对纯色做高斯模糊输出仍是同一纯色，肉眼零差异；而 API 31+ 会真实
                // 创建 RenderEffect 离屏图层，卡片位于 LazyColumn 中时滚动每帧都要重走该路径。
                // 因此移除 blur：视觉不变、省掉一次离屏渲染。
                // 若后续需要真毛玻璃，需改为模糊「背后的内容」（如自定义 GraphicsLayer 捕获或 Haze 库）。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(bgColor),
                )
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }
    }
}

/**
 * 便捷毛玻璃卡片：等价于 [UnifiedCard] 的 [UnifiedCardVariant.GLASS] 变体。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Corner.large,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    UnifiedCard(
        modifier = modifier,
        variant = UnifiedCardVariant.GLASS,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        content = content,
    )
}
