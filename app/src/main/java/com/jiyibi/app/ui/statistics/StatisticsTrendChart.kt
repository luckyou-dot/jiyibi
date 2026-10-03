package com.jiyibi.app.ui.statistics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.domain.model.StatPeriod
import com.jiyibi.app.core.domain.model.TrendPoint
import com.jiyibi.app.core.domain.model.centsToYuan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== 趋势折线图 ====================

@Composable
internal fun TrendSection(trend: List<TrendPoint>, period: StatPeriod) {
    // 范围标签：本日/本周/本月/本年
    val rangeLabel = when (period) {
        StatPeriod.DAILY -> "本日"
        StatPeriod.WEEKLY -> "本周"
        StatPeriod.MONTHLY -> "本月"
        StatPeriod.YEARLY -> "本年"
    }
    // 日均：trend 总支出 / 点数
    val dailyAvg = if (trend.isNotEmpty()) trend.sumOf { it.expense } / trend.size else 0L
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
    ) {
        // 标题行：标题 + 范围·日均
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "支出趋势",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$rangeLabel · 日均 ¥${dailyAvg.centsToYuan().toPlainString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.m))
        if (trend.isEmpty()) {
            Text(
                "暂无趋势数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TrendChart(trend)
        }
    }
}

/**
 * 美化版趋势图：平滑曲线 + 渐变填充 + 网格背景 + 光晕数据点
 *
 * - 贝塞尔平滑曲线，折线下方渐变填充
 * - 横向网格线 + Y 轴刻度
 * - 数据点带光晕效果，峰值高亮
 * - 入场动画：从左到右 clip 揭示
 * - 底部日期标签均匀分布
 */
@Composable
private fun TrendChart(trend: List<TrendPoint>) {
    val maxExpense = (trend.maxOfOrNull { it.expense } ?: 0L).coerceAtLeast(1L)
    val primaryColor = MaterialTheme.colorScheme.primary
    val peakColor = ExpenseRed
    val textColor = MaterialTheme.colorScheme.onSurface
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labeledPoints = trend.filter { it.expense > 0L }
    val density = LocalDensity.current.density

    val progress = remember { Animatable(0f) }
    LaunchedEffect(trend) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }
    val reveal = progress.value

    val chartPaddingTop = 24f
    val chartPaddingBottom = 8f

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            val w = size.width
            val h = size.height
            if (trend.isEmpty()) return@Canvas

            val chartLeft = 0f
            val chartRight = w
            val chartTop = chartPaddingTop
            val chartBottom = h - chartPaddingBottom
            val chartW = chartRight - chartLeft
            val chartH = chartBottom - chartTop

            fun pointX(idx: Int): Float =
                if (trend.size == 1) chartLeft + chartW / 2f
                else chartLeft + idx.toFloat() / (trend.size - 1) * chartW

            fun pointY(expense: Long): Float =
                chartBottom - (expense.toFloat() / maxExpense.toFloat()) * chartH

            // 1. 横向网格线（4 条）
            val gridCount = 4
            val gridLines = (0..gridCount).map { i ->
                chartBottom - i.toFloat() / gridCount * chartH
            }

            drawIntoCanvas { canvas ->
                val gridPaint = android.graphics.Paint().apply {
                    color = gridColor.toArgb()
                    strokeWidth = 1f
                    isAntiAlias = true
                }

                gridLines.forEach { y ->
                    canvas.nativeCanvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
                }
            }

            // 2. 按进度从左到右 clip 揭示
            clipRect(left = 0f, top = 0f, right = chartLeft + chartW * reveal, bottom = h) {
                // 计算平滑曲线控制点（贝塞尔）
                val points = trend.mapIndexed { i, p ->
                    Offset(pointX(i), pointY(p.expense))
                }

                // 3. 渐变填充区域
                if (points.size >= 2) {
                    val fillPath = Path().apply {
                        moveTo(points.first().x, chartBottom)
                        lineTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val midX = (prev.x + curr.x) / 2f
                            cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }
                        lineTo(points.last().x, chartBottom)
                        close()
                    }

                    val gradient = androidx.compose.ui.graphics.LinearGradientShader(
                        from = Offset(0f, chartTop),
                        to = Offset(0f, chartBottom),
                        colors = listOf(
                            primaryColor.copy(alpha = 0.35f),
                            primaryColor.copy(alpha = 0.02f),
                        ),
                    )
                    drawPath(
                        path = fillPath,
                        brush = androidx.compose.ui.graphics.ShaderBrush(gradient),
                    )
                }

                // 4. 平滑折线
                if (points.size == 1) {
                    drawLine(
                        color = primaryColor,
                        start = Offset(chartLeft, points.first().y),
                        end = Offset(chartRight, points.first().y),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                } else {
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val midX = (prev.x + curr.x) / 2f
                            cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }
                    }
                    drawPath(
                        path = linePath,
                        color = primaryColor,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }

                // 5. 数据点 + 光晕 + 金额标签
                drawIntoCanvas { canvas ->
                    val haloPaint = android.graphics.Paint().apply {
                        color = primaryColor.copy(alpha = 0.25f).toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val dotPaint = android.graphics.Paint().apply {
                        color = primaryColor.toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val dotInnerPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val peakHaloPaint = android.graphics.Paint().apply {
                        color = peakColor.copy(alpha = 0.3f).toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val peakDotPaint = android.graphics.Paint().apply {
                        color = peakColor.toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val labelBgPaint = android.graphics.Paint().apply {
                        color = primaryColor.copy(alpha = 0.9f).toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val peakLabelBgPaint = android.graphics.Paint().apply {
                        color = peakColor.copy(alpha = 0.9f).toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val labelTextPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 22f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    labeledPoints.forEach { p ->
                        val idx = trend.indexOf(p)
                        val x = pointX(idx)
                        val y = pointY(p.expense)
                        val isPeak = p.expense == maxExpense

                        // 光晕
                        canvas.nativeCanvas.drawCircle(
                            x, y,
                            if (isPeak) 12f else 9f,
                            if (isPeak) peakHaloPaint else haloPaint,
                        )
                        // 外圆点
                        canvas.nativeCanvas.drawCircle(
                            x, y,
                            if (isPeak) 6.5f else 5f,
                            if (isPeak) peakDotPaint else dotPaint,
                        )
                        // 内白心
                        canvas.nativeCanvas.drawCircle(
                            x, y,
                            if (isPeak) 2.5f else 2f,
                            dotInnerPaint,
                        )

                        // 金额标签（圆角矩形背景 + 文字）
                        val label = "¥${p.expense.centsToYuan().toPlainString()}"
                        val textWidth = labelTextPaint.measureText(label)
                        val bgPaddingH = 10f
                        val bgPaddingV = 5f
                        val bgWidth = textWidth + bgPaddingH * 2
                        val bgHeight = 26f
                        val bgRadius = 13f

                        var textX = x
                        var bgLeft = textX - bgWidth / 2f
                        // 防止越界
                        if (bgLeft < chartLeft) {
                            bgLeft = chartLeft
                            textX = bgLeft + bgWidth / 2f
                        }
                        if (bgLeft + bgWidth > chartRight) {
                            bgLeft = chartRight - bgWidth
                            textX = bgLeft + bgWidth / 2f
                        }
                        // 标签在点上方，太靠近顶部则放下方
                        val labelAbove = y - bgHeight - 8f > chartTop
                        val bgTop = if (labelAbove) y - bgHeight - 8f else y + 8f
                        val textY = if (labelAbove) bgTop + bgHeight - 8f else bgTop + bgHeight - 8f

                        val bgRect = android.graphics.RectF(
                            bgLeft, bgTop, bgLeft + bgWidth, bgTop + bgHeight
                        )
                        canvas.nativeCanvas.drawRoundRect(
                            bgRect, bgRadius, bgRadius,
                            if (isPeak) peakLabelBgPaint else labelBgPaint,
                        )
                        canvas.nativeCanvas.drawText(label, textX, textY, labelTextPaint)
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.s))

        // 底部日期标签：均匀分布 5 个点
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val labelCount = (trend.size / 3).coerceAtLeast(2).coerceAtMost(5)
            val step = (trend.size - 1).toFloat() / (labelCount - 1).coerceAtLeast(1)
            (0 until labelCount).forEach { i ->
                val idx = (i * step).toInt().coerceIn(0, trend.size - 1)
                Text(
                    formatTrendDate(trend[idx].timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun formatTrendDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
