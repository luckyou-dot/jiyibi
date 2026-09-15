package com.jiyibi.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BreakfastDining
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 分类可选图标键名列表（用于分类管理图标选择网格） */
val CategoryIconKeys: List<String> = listOf(
    "Restaurant", "Fastfood", "BreakfastDining", "DirectionsCar", "ShoppingBag",
    "SportsEsports", "Home", "LocalHospital", "School", "HealthAndSafety",
    "Flight", "Pets", "ChildCare", "Subscriptions", "Payments", "Business",
    "Work", "Undo", "Category",
    // 收入类常用图标
    "Savings", "AccountBalanceWallet", "TrendingUp", "Paid", "CurrencyExchange",
    "Redeem", "CardGiftcard", "EmojiEvents", "SwapHoriz",
)

/**
 * 根据分类存储的图标键名解析为 Material [ImageVector]。
 * 未知键名回退到 [Icons.Filled.Category]。
 */
fun categoryIconByKey(key: String?): ImageVector = when (key) {
    "Restaurant" -> Icons.Filled.Restaurant
    "Fastfood" -> Icons.Filled.Fastfood
    "BreakfastDining" -> Icons.Filled.BreakfastDining
    "DirectionsCar" -> Icons.Filled.DirectionsCar
    "ShoppingBag" -> Icons.Filled.ShoppingBag
    "SportsEsports" -> Icons.Filled.SportsEsports
    "Home" -> Icons.Filled.Home
    "LocalHospital" -> Icons.Filled.LocalHospital
    "School" -> Icons.Filled.School
    "HealthAndSafety" -> Icons.Filled.HealthAndSafety
    "Flight" -> Icons.Filled.Flight
    "Pets" -> Icons.Filled.Pets
    "ChildCare" -> Icons.Filled.ChildCare
    "Subscriptions" -> Icons.Filled.Subscriptions
    "Payments" -> Icons.Filled.Payments
    "Business" -> Icons.Filled.Business
    "Work" -> Icons.Filled.Work
    "Undo" -> Icons.AutoMirrored.Filled.Undo
    "Savings" -> Icons.Filled.Savings
    "AccountBalanceWallet" -> Icons.Filled.AccountBalanceWallet
    "TrendingUp" -> Icons.AutoMirrored.Filled.TrendingUp
    "Paid" -> Icons.Filled.Paid
    "CurrencyExchange" -> Icons.Filled.CurrencyExchange
    "Redeem" -> Icons.Filled.Redeem
    "CardGiftcard" -> Icons.Filled.CardGiftcard
    "EmojiEvents" -> Icons.Filled.EmojiEvents
    "SwapHoriz" -> Icons.Filled.SwapHoriz
    else -> Icons.Filled.Category
}

/**
 * 通用脚手架：TopAppBar + 可选返回按钮 + 可选 actions。
 *
 * 用于二级页面统一头部样式，避免每个 Screen 重复写 Scaffold/TopAppBar 样板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JiyibiScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = actions,
            )
        }
    ) { padding ->
        content(padding)
    }
}

/**
 * 空状态占位：居中显示图标 + 标题 + 副标题 + 可选主按钮。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionText != null && onAction != null) {
                Button(onClick = onAction) { Text(actionText) }
            }
        }
    }
}

/**
 * 加载中状态：居中显示 CircularProgressIndicator。
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * 错误状态：居中错误图标 + 消息 + 可选「重试」按钮。
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Button(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

/**
 * 左滑删除容器：左滑到阈值时弹出确认框，用户确认后才真正删除。
 *
 * 背景显示红色 + 右侧删除图标，content 区域显示正常内容。
 * 滑动越过阈值不会立即删除，而是让行回弹并弹出确认对话框，
 * 避免误触导致数据丢失；用户点「删除」才回调 [onDelete]。
 *
 * @param onDelete 用户在确认框中点击「删除」后触发
 * @param confirmTitle 确认框标题
 * @param confirmMessage 确认框正文
 * @param backgroundCorner 红色删除底层的圆角。外层是独立卡片时用 [Corner.large]；
 *        被包在 [UnifiedCard] 内部做「一天一卡」分组列表时用 [Corner.small]，
 *        否则大圆角会在卡片内显得突兀。
 * @param modifier 外部修饰符
 * @param content 列表项正常内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    confirmTitle: String = "确认删除",
    confirmMessage: String = "删除后无法恢复，确定要删除这条记录吗？",
    backgroundCorner: Dp = Corner.large,
    content: @Composable () -> Unit,
) {
    // 是否显示删除确认框
    var showConfirm by remember { mutableStateOf(false) }

    // 左滑（EndToStart 方向）越过阈值时不直接删除：弹出确认框并让行回弹
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showConfirm = true
            }
            // 始终返回 false：行回弹到原位，等待用户在确认框中决定
            false
        }
    )
    // 只在真正滑动（或回弹中）时才画红色，静止时底层全透明。
    // SwipeToDismissBox 的 backgroundContent 始终铺在 content 之下，若常驻红色，
    // 只要行内容有任何未绘制区域（垂直 padding、圆角、透明背景）就会透出红边。
    val showBackground by remember {
        derivedStateOf {
            dismissState.progress > 0.01f ||
                dismissState.targetValue != SwipeToDismissBoxValue.Settled
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            if (showBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(backgroundCorner))
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        },
        content = { content() },
    )

    // 删除确认对话框
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }
}
