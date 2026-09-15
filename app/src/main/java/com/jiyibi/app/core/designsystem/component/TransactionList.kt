package com.jiyibi.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiyibi.app.core.common.TransactionDayGroup
import com.jiyibi.app.core.common.TransactionRowUi
import com.jiyibi.app.core.common.startOfDay
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.centsToYuan
import java.util.Calendar

/** 中文星期名，下标对应 Calendar.DAY_OF_WEEK - 1 */
private val WEEKDAY_NAMES = listOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

/** 昨天 0 点：用 Calendar 减一天，避免夏令时下 `-86_400_000` 产生偏移 */
private fun yesterdayStart(todayStart: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = todayStart
    cal.add(Calendar.DAY_OF_MONTH, -1)
    return cal.timeInMillis
}

/**
 * 日期标题文本：`9月13日 昨天` / `9月12日 星期六` / `9月14日 今天`。
 *
 * 手动拼「M月d日」而不用 SimpleDateFormat：省一次格式化对象构造，
 * 也避免不同 Locale 下 pattern 输出不一致。
 */
private fun dayLabelText(dayStart: Long, todayStart: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dayStart
    val datePart = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    val suffix = when (dayStart) {
        todayStart -> "今天"
        yesterdayStart(todayStart) -> "昨天"
        else -> WEEKDAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }
    return "$datePart $suffix"
}

/**
 * 日期分组标题行：左侧日期，右侧当天出/入合计。
 *
 * 合计金额用中性色而非红/绿：当天出入为 0.00 时染红会显得像异常，
 * 与参考图克制的观感也一致；单条流水的金额仍保留语义色。
 */
@Composable
fun TransactionDayHeader(
    group: TransactionDayGroup,
    modifier: Modifier = Modifier,
) {
    // 同一天只算一次标签，避免每次重组都重新取当前时间
    val label = remember(group.dayStart) {
        dayLabelText(group.dayStart, startOfDay(System.currentTimeMillis()))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        DayTotal(label = "出", cents = group.expenseTotal)
        Spacer(Modifier.width(Spacing.m))
        DayTotal(label = "入", cents = group.incomeTotal)
    }
}

/** 日头里的单个合计项：灰色小标签 + 中性色金额 */
@Composable
private fun DayTotal(label: String, cents: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = formatAmount(cents),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 单条交易行（不含卡片外壳，容器由调用方决定）。
 *
 * 层次与参考图一致：**分类名**作主标题（比备注稳定——手工记账常不填备注），
 * 副标题为 `时间 | 备注`，备注为空时退化为账户名；金额右对齐。
 */
@Composable
fun TransactionRow(
    row: TransactionRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tx = row.tx
    val categoryIcon = row.category?.let { categoryIconByKey(it.icon) } ?: Icons.Filled.Category
    val categoryColor = row.category?.let { cat ->
        if (cat.color != 0) Color(cat.color) else MaterialTheme.colorScheme.primary
    } ?: MaterialTheme.colorScheme.primary

    val amountColor = when (tx.type) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 参考图风格：支出带负号，收入/转账不带正号；密集列表省略 ¥ 以免拥挤
    val amountText = if (tx.type == TransactionType.EXPENSE) {
        "-${formatAmount(tx.amount)}"
    } else {
        formatAmount(tx.amount)
    }

    val title = row.category?.name ?: tx.note.ifBlank { "未分类" }
    val detail = tx.note.ifBlank { row.accountName }
    val timeText = remember(tx.date) { formatTime(tx.date) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 先铺一层不透明 surface 背景再挂 clickable/padding：
            // SwipeToDismissBox 的红色删除底层位于 content 之下，而本行只有文字没有
            // 背景色，垂直 padding 区域就会透出红色，观感上像给每行套了个红框。
            // 这里主动把行画成不透明，红色就只在真正左滑时露出。
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 分类圆形图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(categoryColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(categoryIcon, contentDescription = null, tint = categoryColor)
        }
        Spacer(Modifier.width(Spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = if (detail.isBlank()) timeText else "$timeText | $detail",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            text = amountText,
            color = amountColor,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * 一天的分组卡片：当天所有交易收进同一张 [UnifiedCard]，行间用细分隔线。
 *
 * 相比「每条一卡 + 等间距」，按天成卡让分组边界一眼可见，也更贴近参考图。
 *
 * @param wrapRow 可选的行包装器，用于把每行套进 `SwipeToDeleteItem` 等容器；
 *                分隔线画在包装器**外面**，这样行被滑走时线保持不动。
 */
@Composable
fun TransactionDayCard(
    group: TransactionDayGroup,
    onRowClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    wrapRow: @Composable (row: TransactionRowUi, content: @Composable () -> Unit) -> Unit =
        { _, content -> content() },
) {
    UnifiedCard(
        modifier = modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
        contentPadding = PaddingValues(horizontal = Spacing.m),
    ) {
        group.rows.forEachIndexed { index, row ->
            wrapRow(row) {
                TransactionRow(row = row, onClick = { onRowClick(row.tx.id) })
            }
            if (index != group.rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** 分 → 「12.34」纯数字文本（不含货币符号） */
private fun formatAmount(cents: Long): String = cents.centsToYuan().toPlainString()

/** epoch millis → 「HH:mm」 */
private fun formatTime(millis: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
