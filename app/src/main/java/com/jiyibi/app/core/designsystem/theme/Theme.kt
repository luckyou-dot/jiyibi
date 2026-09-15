package com.jiyibi.app.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 渐变色对：包含起始色与结束色，供页面背景、卡片头部等场景使用。
 *
 * M3 的 [androidx.compose.material3.ColorScheme] 没有渐变字段，
 * 因此通过 [LocalGradientColors] 单独向子组件传递。
 */
data class GradientColors(val start: Color, val end: Color)

/**
 * 当前主题对应的渐变色 CompositionLocal。
 *
 * 默认值为未指定颜色，在 [JiYiBiTheme] 内部会被覆盖为真实配色。
 */
val LocalGradientColors = staticCompositionLocalOf {
    GradientColors(start = Color.Unspecified, end = Color.Unspecified)
}

/**
 * 根据主题调色板构造浅色 ColorScheme。
 */
private fun paletteToLightScheme(p: ThemePalette) = lightColorScheme(
    primary = p.lightPrimary,
    onPrimary = p.lightOnPrimary,
    primaryContainer = p.lightPrimaryContainer,
    onPrimaryContainer = p.lightOnPrimaryContainer,
    secondary = p.lightSecondary,
    tertiary = p.lightTertiary,
    background = p.lightBackground,
    surface = p.lightSurface,
    surfaceVariant = p.lightSurfaceVariant,
    onSurface = p.lightOnSurface,
    onSurfaceVariant = p.lightOnSurfaceVariant,
)

/**
 * 根据主题调色板构造深色 ColorScheme。
 */
private fun paletteToDarkScheme(p: ThemePalette) = darkColorScheme(
    primary = p.darkPrimary,
    onPrimary = p.darkOnPrimary,
    primaryContainer = p.darkPrimaryContainer,
    onPrimaryContainer = p.darkOnPrimaryContainer,
    secondary = p.darkSecondary,
    tertiary = p.darkTertiary,
    background = p.darkBackground,
    surface = p.darkSurface,
    surfaceVariant = p.darkSurfaceVariant,
    onSurface = p.darkOnSurface,
    onSurfaceVariant = p.darkOnSurfaceVariant,
)

/**
 * 应用主题。
 *
 * @param darkTheme 是否夜间模式，默认跟随系统
 * @param appTheme  应用主题风格，默认 [AppTheme.MINT] 薄荷清新
 * @param dynamicColor 是否启用 Material You 动态取色（Android 12+），默认关闭以保持品牌鲜活配色
 */
@Composable
fun JiYiBiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: AppTheme = AppTheme.MINT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = paletteOf(appTheme)
    val colorScheme = if (darkTheme) paletteToDarkScheme(palette) else paletteToLightScheme(palette)
    // 根据明暗模式取对应的渐变色对
    val gradientColors = if (darkTheme) {
        GradientColors(start = palette.darkGradientStart, end = palette.darkGradientEnd)
    } else {
        GradientColors(start = palette.lightGradientStart, end = palette.lightGradientEnd)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalGradientColors provides gradientColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JiYiBiTypography,
            content = content,
        )
    }
}

/**
 * 便捷函数：返回当前主题对应的线性渐变 Brush。
 *
 * 起止色取自 [LocalGradientColors]，需在 [JiYiBiTheme] 内部调用。
 * 结果按 [colors] 做 `remember` 缓存——若每次重组都新建 Brush 实例，
 * 调用点 `.background(brush)` 会因参数变化而重复触发绘制。
 */
@Composable
fun gradientBrush(): Brush {
    val colors = LocalGradientColors.current
    return remember(colors) {
        Brush.linearGradient(colors = listOf(colors.start, colors.end))
    }
}
