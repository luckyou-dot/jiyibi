package com.jiyibi.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// 支出 / 收入 / 预算 语义色（全主题共享）
val ExpenseRed = Color(0xFFFF5252)
val IncomeGreen = Color(0xFF26C281)
val BudgetAmber = Color(0xFFFFB300)

/**
 * 应用主题枚举：4 种风格供用户切换。
 *
 * - [MINT]   薄荷清新（默认）：青绿渐变，清爽自然
 * - [VIBRANT] 紫粉活力：紫粉渐变，年轻时尚
 * - [SUNSET]  暖阳治愈：橙红渐变，温暖亲和
 * - [MORANDI] 莫兰迪高级：低饱和灰调，优雅质感
 */
enum class AppTheme(val key: String, val displayName: String) {
    MINT("mint", "薄荷清新"),
    VIBRANT("vibrant", "紫粉活力"),
    SUNSET("sunset", "暖阳治愈"),
    MORANDI("morandi", "莫兰迪高级");

    companion object {
        fun fromKey(key: String?): AppTheme =
            entries.firstOrNull { it.key == key } ?: MINT
    }
}

/**
 * 单套主题的配色集合（浅色 + 深色）。
 *
 * 浅色用于日间，深色用于夜间模式。
 * 每套主题同时配套一对浅色渐变色与一对深色渐变色，
 * 供页面背景、卡片头部等场景使用 [LocalGradientColors] 取用。
 */
data class ThemePalette(
    // 浅色
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    val lightOnSurface: Color,
    val lightOnSurfaceVariant: Color,
    // 浅色渐变背景
    val lightGradientStart: Color,
    val lightGradientEnd: Color,
    // 深色
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkTertiary: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val darkOnSurface: Color,
    val darkOnSurfaceVariant: Color,
    // 深色渐变背景
    val darkGradientStart: Color,
    val darkGradientEnd: Color,
)

/** 薄荷清新：薄荷青主色 #1ABC9C，渐变至深青 #16A085（默认主题） */
val MintPalette = ThemePalette(
    lightPrimary = Color(0xFF1ABC9C),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFC7F0E4),
    lightOnPrimaryContainer = Color(0xFF00382E),
    lightSecondary = Color(0xFF16A085),
    lightTertiary = Color(0xFF4DD0E1),
    lightBackground = Color(0xFFF5FBF8),
    lightSurface = Color(0xFFFAFDFB),
    lightSurfaceVariant = Color(0xFFD5E8E2),
    lightOnSurface = Color(0xFF1A1C1B),
    lightOnSurfaceVariant = Color(0xFF3F4946),
    lightGradientStart = Color(0xFF1ABC9C),
    lightGradientEnd = Color(0xFF16A085),
    darkPrimary = Color(0xFF5FE3C7),
    darkOnPrimary = Color(0xFF00382E),
    darkPrimaryContainer = Color(0xFF0A6E5C),
    darkOnPrimaryContainer = Color(0xFFC7F0E4),
    darkSecondary = Color(0xFF2ECCA8),
    darkTertiary = Color(0xFF4DD0E1),
    darkBackground = Color(0xFF0E1714),
    darkSurface = Color(0xFF0E1714),
    darkSurfaceVariant = Color(0xFF2A3833),
    darkOnSurface = Color(0xFFE0E3E1),
    darkOnSurfaceVariant = Color(0xFFBEC9C5),
    darkGradientStart = Color(0xFF1ABC9C),
    darkGradientEnd = Color(0xFF16A085),
)

/** 紫粉活力：紫主色 #9B59B6，渐变至粉红 #E91E63 */
val VibrantPalette = ThemePalette(
    lightPrimary = Color(0xFF9B59B6),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFEAD9F3),
    lightOnPrimaryContainer = Color(0xFF3A1B4F),
    lightSecondary = Color(0xFFE91E63),
    lightTertiary = Color(0xFFFFB300),
    lightBackground = Color(0xFFFBF5FD),
    lightSurface = Color(0xFFFFFBFE),
    lightSurfaceVariant = Color(0xFFE8DCEC),
    lightOnSurface = Color(0xFF1C1B1F),
    lightOnSurfaceVariant = Color(0xFF49454F),
    lightGradientStart = Color(0xFF9B59B6),
    lightGradientEnd = Color(0xFFE91E63),
    darkPrimary = Color(0xFFC9A0DC),
    darkOnPrimary = Color(0xFF3A1B4F),
    darkPrimaryContainer = Color(0xFF5E3A75),
    darkOnPrimaryContainer = Color(0xFFEAD9F3),
    darkSecondary = Color(0xFFF06292),
    darkTertiary = Color(0xFFFFD54F),
    darkBackground = Color(0xFF171219),
    darkSurface = Color(0xFF171219),
    darkSurfaceVariant = Color(0xFF2F2733),
    darkOnSurface = Color(0xFFE6E1E5),
    darkOnSurfaceVariant = Color(0xFFCAC4D0),
    darkGradientStart = Color(0xFF9B59B6),
    darkGradientEnd = Color(0xFFE91E63),
)

/** 暖阳治愈：橙红主色 #FF7043，渐变至琥珀 #FFCA28 */
val SunsetPalette = ThemePalette(
    lightPrimary = Color(0xFFFF7043),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFFFD4C2),
    lightOnPrimaryContainer = Color(0xFF5C1E00),
    lightSecondary = Color(0xFFFFCA28),
    lightTertiary = Color(0xFFFF5252),
    lightBackground = Color(0xFFFFF8F5),
    lightSurface = Color(0xFFFFFCFA),
    lightSurfaceVariant = Color(0xFFFFE0D0),
    lightOnSurface = Color(0xFF201A17),
    lightOnSurfaceVariant = Color(0xFF52443D),
    lightGradientStart = Color(0xFFFF7043),
    lightGradientEnd = Color(0xFFFFCA28),
    darkPrimary = Color(0xFFFF9C7C),
    darkOnPrimary = Color(0xFF5C1E00),
    darkPrimaryContainer = Color(0xFF8E3D1A),
    darkOnPrimaryContainer = Color(0xFFFFD4C2),
    darkSecondary = Color(0xFFFFD54F),
    darkTertiary = Color(0xFFFF8A80),
    darkBackground = Color(0xFF1F1410),
    darkSurface = Color(0xFF1F1410),
    darkSurfaceVariant = Color(0xFF3A2A22),
    darkOnSurface = Color(0xFFEFE0DA),
    darkOnSurfaceVariant = Color(0xFFD7C3B9),
    darkGradientStart = Color(0xFFFF7043),
    darkGradientEnd = Color(0xFFFFCA28),
)

/** 莫兰迪高级：莫兰迪灰蓝主色 #8D9DB6，渐变至灰 #A0AEC0 */
val MorandiPalette = ThemePalette(
    lightPrimary = Color(0xFF8D9DB6),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFD6DCE6),
    lightOnPrimaryContainer = Color(0xFF1F2A38),
    lightSecondary = Color(0xFFA0AEC0),
    lightTertiary = Color(0xFFB0A8B9),
    lightBackground = Color(0xFFF7F8FA),
    lightSurface = Color(0xFFFCFCFD),
    lightSurfaceVariant = Color(0xFFDEE2EA),
    lightOnSurface = Color(0xFF1A1C1B),
    lightOnSurfaceVariant = Color(0xFF424948),
    lightGradientStart = Color(0xFF8D9DB6),
    lightGradientEnd = Color(0xFFA0AEC0),
    darkPrimary = Color(0xFFB0BCCE),
    darkOnPrimary = Color(0xFF1F2A38),
    darkPrimaryContainer = Color(0xFF48566B),
    darkOnPrimaryContainer = Color(0xFFD6DCE6),
    darkSecondary = Color(0xFFC0CAD6),
    darkTertiary = Color(0xFFC2BAC6),
    darkBackground = Color(0xFF121417),
    darkSurface = Color(0xFF121417),
    darkSurfaceVariant = Color(0xFF2A2E36),
    darkOnSurface = Color(0xFFE0E3E1),
    darkOnSurfaceVariant = Color(0xFFC0C4C2),
    darkGradientStart = Color(0xFF8D9DB6),
    darkGradientEnd = Color(0xFFA0AEC0),
)

/** 根据主题枚举获取对应调色板 */
fun paletteOf(theme: AppTheme): ThemePalette = when (theme) {
    AppTheme.MINT -> MintPalette
    AppTheme.VIBRANT -> VibrantPalette
    AppTheme.SUNSET -> SunsetPalette
    AppTheme.MORANDI -> MorandiPalette
}
