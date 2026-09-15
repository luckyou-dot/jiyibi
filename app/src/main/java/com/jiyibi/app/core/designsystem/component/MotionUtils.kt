package com.jiyibi.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 数字滚动动效组件。
 *
 * 当 [targetValue] 变化时，从当前显示值平滑动画到目标值，并以
 * `prefix + 格式化数字 + suffix` 的形式渲染。常用于金额、余额等数字的过渡展示。
 *
 * 首次显示时直接以目标值呈现（无动画），后续每次 [targetValue] 变化都会
 * 以 [FastOutSlowInEasing] 缓动在 [durationMillis] 内过渡。
 *
 * 实现说明：动画值 `animatable.value` 在 Composable 体内直接读取，因此动画进行
 * 期间本组件会逐帧重组（重组范围仅限组件自身，即一次 Text 调用与字符串格式化）。
 * 这是数字滚动动效本身的必要代价，未使用 `derivedStateOf` 折叠。
 *
 * @param targetValue 目标数值
 * @param modifier 外部修饰符
 * @param durationMillis 动画时长（毫秒），默认 600ms
 * @param decimals 保留小数位数，默认 2
 * @param prefix 数字前缀，例如 "¥"
 * @param suffix 数字后缀
 * @param style 文本样式，默认 [MaterialTheme.typography.displayMedium]
 * @param color 文本颜色，[Color.Unspecified] 时跟随 [style] 中的颜色
 */
@Composable
fun AnimatedNumber(
    targetValue: Double,
    modifier: Modifier = Modifier,
    durationMillis: Int = 600,
    decimals: Int = 2,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = Color.Unspecified,
) {
    // 用 Float Animatable 驱动数值过渡；初始即为目标值，首次显示不动画
    val animatable = remember { Animatable(targetValue.toFloat()) }
    LaunchedEffect(targetValue) {
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        )
    }
    val currentValue = animatable.value.toDouble()
    Text(
        text = "$prefix${String.format("%.${decimals}f", currentValue)}$suffix",
        modifier = modifier,
        style = style,
        color = color,
    )
}

/**
 * 列表项入场动画修饰符。
 *
 * 应用后，元素会以「淡入 + 从下方 16dp 位移」的方式进入，并按 [index]
 * 顺序错开延迟（每项 [startDelayMillis] 毫秒），形成自上而下的逐项入场效果。
 *
 * 由于内部需要使用 `remember` / `LaunchedEffect`，通过 [Modifier.composed] 包装，
 * 可直接像普通修饰符一样链式调用。
 *
 * @param index 列表项位置，用于计算错开延迟
 * @param durationMillis 单项动画时长（毫秒），默认 300ms
 * @param startDelayMillis 每项之间错开的延迟（毫秒），默认 50ms
 */
fun Modifier.listItemEnterAnimation(
    index: Int,
    durationMillis: Int = 300,
    startDelayMillis: Int = 50,
): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(16f) }
    // 首次进入组合时启动动画；按 index 错开开始时间
    LaunchedEffect(Unit) {
        delay(index * startDelayMillis.toLong())
        // 透明度与位移并行执行，各自独立动画
        launch { alpha.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) }
        launch { offsetY.animateTo(0f, tween(durationMillis, easing = FastOutSlowInEasing)) }
    }
    this
        .graphicsLayer {
            this.alpha = alpha.value
            this.translationY = offsetY.value.dp.toPx()
        }
}

/**
 * 动效进度条组件。
 *
 * 当 [progress] 变化时，指示器宽度以平滑动画过渡。进度值会被限制在 0f~1f 范围内
 * 绘制；若需表达「超支」等异常状态，由调用方自行传入红色 [indicatorColor]，
 * 组件本身只按传入颜色绘制。
 *
 * 结构：外层 [Box] 作为轨道（[trackColor] + 圆角），内层 [Box] 作为指示器
 * （[indicatorColor] + 按动画进度填充宽度）。
 *
 * @param progress 进度，取值 0f~1f（超出范围会被裁剪到 0f~1f）
 * @param modifier 外部修饰符
 * @param height 进度条高度，默认 8dp
 * @param trackColor 轨道颜色，默认 [MaterialTheme.colorScheme.surfaceVariant]
 * @param indicatorColor 指示器颜色，默认 [MaterialTheme.colorScheme.primary]
 * @param durationMillis 动画时长（毫秒），默认 800ms
 */
@Composable
fun AnimatedProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    durationMillis: Int = 800,
) {
    // 将目标进度裁剪到 0f~1f，并平滑过渡到目标值
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "animatedProgress",
    )
    // 轨道层：固定高度 + 圆角 + 轨道底色
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        // 指示器层：按动画进度填充宽度
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(indicatorColor),
        )
    }
}
