package com.jiyibi.app.ui.transaction

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.categoryIconByKey
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.RecurringFrequency
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.yuanToCents

/** 交易类型对应的中文标签（internal：编辑页与其分区组件共用） */
internal fun TransactionType.label(): String = when (this) {
    TransactionType.EXPENSE -> "支出"
    TransactionType.INCOME -> "收入"
    TransactionType.TRANSFER -> "转账"
}

/** 根据表单状态构造 Transaction */
internal fun buildTransaction(
    viewModel: TransactionEditViewModel,
    type: TransactionType,
    amountText: String,
    accountId: Long?,
    toAccountId: Long?,
    categoryId: Long?,
    date: Long,
    note: String,
    selectedTags: List<String>,
): Transaction {
    val cents = amountText.toDoubleOrNull()?.yuanToCents() ?: 0L
    return Transaction(
        id = if (viewModel.isEditMode) viewModel.transactionId else 0L,
        type = type,
        amount = cents,
        accountId = accountId ?: 0L,
        toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
        categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
        note = note,
        tags = selectedTags.toList(),
        date = date,
    )
}

/** 根据频率计算下次自动记录的时间戳(毫秒),以当前时间为起点 */
internal fun calculateNextRun(freq: RecurringFrequency): Long {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    return when (freq) {
        RecurringFrequency.DAILY -> now + day
        RecurringFrequency.WEEKLY -> now + day * 7
        RecurringFrequency.MONTHLY -> now + day * 30
        RecurringFrequency.YEARLY -> now + day * 365
    }
}

/** 分类网格:4 列,每个 item 居中显示圆形渐变底图标 + 名称,末尾固定一个「+」新增入口 */
@Composable
internal fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddCategory: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(categories, key = { it.id }) { cat ->
            CategoryItem(
                name = cat.name,
                iconKey = cat.icon,
                color = cat.color,
                selected = selectedId == cat.id,
                onClick = { onSelect(cat.id) },
            )
        }
        item(key = "add") {
            CategoryItem(
                name = "新建",
                iconKey = null,
                color = 0,
                selected = false,
                isAdd = true,
                onClick = onAddCategory,
            )
        }
    }
}

/** 单个分类项:圆形渐变底图标,选中加主题色描边 + 缩放动效 */
@Composable
private fun CategoryItem(
    name: String,
    iconKey: String?,
    color: Int,
    selected: Boolean,
    isAdd: Boolean = false,
    onClick: () -> Unit,
) {
    val categoryColor = if (color != 0) Color(color) else MaterialTheme.colorScheme.primary
    // 圆形渐变底色:分类色 → 半透明渐变;新建项用主题色渐变
    val circleBrush = if (isAdd) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                categoryColor,
                categoryColor.copy(alpha = 0.25f),
            )
        )
    }
    // 缩放动效:选中 1.0,未选 0.9
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "categoryScale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(Corner.medium))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 圆形渐变底图标,选中时加主题色描边
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleBrush)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isAdd) Icons.Filled.Add else categoryIconByKey(iconKey),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 单个账户下拉选择 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSelector(
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.name ?: "请选择账户",
            onValueChange = { },
            readOnly = true,
            label = { Text("账户") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(Corner.medium),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text(acc.name) },
                    onClick = {
                        onSelect(acc.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
