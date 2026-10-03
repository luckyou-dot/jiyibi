package com.jiyibi.app.ui.autorecord

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.data.repository.AiConfig
import com.jiyibi.app.core.data.repository.UnmatchedNotification
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.EmptyState
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.SwipeToDeleteItem
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.component.categoryIconByKey
import com.jiyibi.app.core.designsystem.theme.BudgetAmber
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.centsToYuan
import com.jiyibi.app.core.notify.AccessibilityAccessHelper
import com.jiyibi.app.core.notify.NotificationAccessHelper
import com.jiyibi.app.core.notify.PaymentPackages
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动记账页。
 *
 * 三块内容：
 * 1. **通知使用权状态**：未授权时给出跳转按钮（这是系统级设置，App 无法弹窗申请）；
 * 2. **功能开关**：临时停掉自动记账而不必撤销授权；
 * 3. **最近自动记录**：最近 50 条由通知自动生成的交易，可点进编辑或左滑删除。
 *
 * 授权状态在每次 `ON_RESUME` 重新读取，因此「去开启 → 返回」后会自动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRecordScreen(
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: AutoRecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 通知使用权不由 App 控制，只能读系统设置；返回本页时重新判定
    var accessGranted by remember { mutableStateOf(NotificationAccessHelper.isEnabled(context)) }
    // 无障碍服务同理：从系统设置返回后重新判定
    var accessibilityGranted by remember { mutableStateOf(AccessibilityAccessHelper.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        accessGranted = NotificationAccessHelper.isEnabled(context)
        accessibilityGranted = AccessibilityAccessHelper.isEnabled(context)
        onPauseOrDispose { }
    }

    // Android 13+ 弹横幅提醒还需要运行时通知权限（系统设置页回来后一并刷新）
    var notifPermissionGranted by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifPermissionGranted = granted || hasNotificationPermission(context)
    }
    LifecycleResumeEffect(Unit) {
        notifPermissionGranted = hasNotificationPermission(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动记账") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.l,
                end = Spacing.l,
                top = Spacing.m,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                PermissionCard(
                    granted = accessGranted,
                    onOpenSettings = {
                        runCatching { context.startActivity(NotificationAccessHelper.settingsIntent()) }
                    },
                )
            }

            item {
                AccessibilityCard(
                    granted = accessibilityGranted,
                    onOpenSettings = {
                        runCatching { context.startActivity(AccessibilityAccessHelper.settingsIntent()) }
                    },
                )
            }

            item {
                AiCard(
                    config = aiConfig,
                    onSave = viewModel::saveAiConfig,
                )
            }

            item {
                SwitchCard(
                    enabled = state.enabled,
                    onToggle = viewModel::setEnabled,
                )
            }

            // Android 13+ 未授通知权限时，自动记账成功的横幅提醒弹不出来
            if (!notifPermissionGranted) {
                item {
                    NotificationPermissionCard(
                        onRequest = {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
            }

            item {
                LearningCard(
                    learnedCount = state.learnedCount,
                    onReset = viewModel::resetCategoryLearning,
                )
            }

            item { SectionHeader("最近自动记录") }

            if (state.records.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.NotificationsOff,
                        title = "还没有自动记录",
                        subtitle = "授权后，微信 / 支付宝支付成功时会自动记一笔",
                    )
                }
            } else {
                items(state.records, key = { it.tx.id }) { item ->
                    SwipeToDeleteItem(
                        onDelete = { viewModel.delete(item) },
                        confirmTitle = "删除这笔自动记录",
                        confirmMessage = "将同时回滚该笔对账户余额的影响，确定删除吗？",
                    ) {
                        AutoRecordRow(item = item, onClick = { onEditTransaction(item.tx.id) })
                    }
                }
            }

            // 未识别的支付通知：规则漏掉新句式时在这里看得到，而不是无声无息
            if (state.unmatched.isNotEmpty()) {
                item { SectionHeader("未识别的支付通知") }
                items(state.unmatched, key = { "${it.postedAt}-${it.content}" }) { entry ->
                    UnmatchedRow(entry = entry, onAddManually = onAddTransaction)
                }
                item {
                    TextButton(
                        onClick = viewModel::clearUnmatched,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("清空未识别记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item { TuningTipCard() }
        }
    }
}

/** 通知使用权状态卡：未授权时给出跳转入口 */
@Composable
private fun PermissionCard(granted: Boolean, onOpenSettings: () -> Unit) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            BudgetAmber.copy(alpha = 0.18f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (granted) {
                        Icons.Filled.NotificationsActive
                    } else {
                        Icons.Filled.NotificationsOff
                    },
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else BudgetAmber,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "通知使用权",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (granted) "已开启，可以自动记账了" else "未开启，自动记账不会生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        ExpenseRed
                    },
                )
            }
        }

        if (!granted) {
            Spacer(Modifier.height(Spacing.s))
            Text(
                "这一项不是普通权限，系统要求你去设置里手动打开。点下面的按钮会跳到「通知使用权」列表，找到「记一笔」并打开即可。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("去系统设置开启")
            }
        }
    }
}

/** 功能开关卡 */
@Composable
private fun SwitchCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "支付通知自动记账",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "识别微信 / 支付宝支付成功通知，自动记一笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * 分类学习卡：展示「商户 → 分类」学习表的规模，并提供重置入口。
 *
 * 这一块的意义是让「自动记账会越用越准」这件事**可见**：
 * 用户每次手工纠正分类都会写进学习表，条目数增长就是学习在生效的证据；
 * 猜错了想回到出厂状态，也有地方一键清空。
 */
@Composable
private fun LearningCard(learnedCount: Int, onReset: () -> Unit) {
    var showResetConfirm by remember { mutableStateOf(false) }

    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "分类学习",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (learnedCount > 0) {
                        "已记住 $learnedCount 个商户的分类，越用越准"
                    } else {
                        "改过自动记账的分类后，这里会记住你的选择"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.s))
        Text(
            "自动记账的分类先按内置关键词猜。如果你在编辑页改了它猜的分类，" +
                "下次同一个商户就直接用你改过的分类，不再猜。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (learnedCount > 0) {
            Spacer(Modifier.height(Spacing.s))
            TextButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("重置分类学习", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置分类学习") },
            text = { Text("将清空已记住的 $learnedCount 个商户分类，之后重新按内置规则猜测。已记好的账不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onReset()
                    },
                ) {
                    Text("重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 分组标题 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.s),
    )
}

/**
 * AI 智能识别配置卡（可选增强）。
 *
 * 规则永远优先，AI 只做三件兜底：① 规则解析不了的通知识别；② 关键词猜不到的分类；
 * ③ **读屏内容审核**——无障碍读到的是整屏文字，由模型复核"这一屏是不是一笔真实支付"
 * 并给出干净备注（防止把整屏聊天内容写进备注）。
 * 未配置 / 关闭时行为与纯本地完全一致。API Key 只存本机 DataStore。
 */
@Composable
private fun AiCard(config: AiConfig, onSave: (AiConfig) -> Unit) {
    // 本地编辑态：以已保存值为初值；点保存后数据回流，值一致不会打断输入
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var model by remember(config) { mutableStateOf(config.model) }

    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (config.isConfigured) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = if (config.isConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AI 智能识别（可选增强）",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (config.isConfigured) "已启用：兜底解析 + 分类猜测 + 读屏内容审核"
                    else if (enabled) "开关已开，还需在下方填入 API Key 才会生效"
                    else "未启用：保持纯本地识别",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Spacer(Modifier.height(Spacing.s))
        Text(
            "内置规则命中时不会联网；只有规则识别不了的通知、关键词猜不到的分类，" +
                "以及读屏抓到的整屏文字（复核是否真是一笔支付、并生成干净备注）才调用大模型。" +
                "已预填 Agnes AI 的接口地址与模型名（当前免费），在下方填入你的 API Key 并打开开关即可用；" +
                "也可改成任意 OpenAI 兼容接口（智谱、DeepSeek 等）。API Key 只保存在本机，不进版本库。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.s))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.s))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("接口地址（OpenAI 兼容）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.s))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.m))
        Button(
            onClick = { onSave(AiConfig(enabled = enabled, baseUrl = baseUrl, apiKey = apiKey, model = model)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存 AI 配置")
        }
    }
}

/**
 * 无障碍支付页面识别卡（可选增强）。
 *
 * 通知监听有个天然盲区：**正在微信 / 支付宝里扫码、转账时**，支付发生在前台，
 * 系统不会给正在使用的 App 推通知，监听器收不到事件。无障碍服务在支付成功
 * 页面出现时当场读屏记账，补上这个场景。可选开关：不开也能用通知方式记。
 */
@Composable
private fun AccessibilityCard(granted: Boolean, onOpenSettings: () -> Unit) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AccessibilityNew,
                    contentDescription = null,
                    tint = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "支付页面识别（无障碍增强）",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (granted) "已开启，扫码 / 转账支付当场记账" else "可选：补足通知抓不到的主动支付场景",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.s))
        Text(
            "你正在微信 / 支付宝里扫码、转账时，付款过程发生在前台，系统不会推送通知，" +
                "通知监听抓不到。开启本服务后，支付成功页面出现时会读取屏幕上的金额与商户当场记账。" +
                "只识别微信 / 支付宝的支付页面，不读取其他内容。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!granted) {
            Spacer(Modifier.height(Spacing.s))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("去系统设置开启")
            }
        }
    }
}

/** Android 13+ 通知权限引导卡：没权限时自动记账成功后弹不出横幅提醒 */
@Composable
private fun NotificationPermissionCard(onRequest: () -> Unit) {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BudgetAmber.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.NotificationsOff,
                    contentDescription = null,
                    tint = BudgetAmber,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "记账提醒没有通知权限",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "自动记账仍会正常入库，但成功后弹不出横幅提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseRed,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("开启记账提醒")
        }
    }
}

/** 单条未识别通知：展示原文快照，可一键跳去手动记一笔 */
@Composable
private fun UnmatchedRow(entry: UnmatchedNotification, onAddManually: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.OUTLINED,
        cornerRadius = Corner.large,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = BudgetAmber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                "${PaymentPackages.displayName(entry.packageName)} · " +
                    timeFormat.format(Date(entry.postedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        // 原始文案是补规则的第一手资料，用等宽字体保留
        Text(
            text = listOf(entry.title, entry.content)
                .filter { it.isNotBlank() }
                .joinToString("："),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
        Spacer(Modifier.height(Spacing.s))
        TextButton(onClick = onAddManually) {
            Text("这笔没记上，去手动记一笔")
        }
    }
}

/** Android 13 以下不需要运行时通知权限，视为已授权 */
private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

/**
 * 单条自动记录：分类图标 + 备注 + 时间/账户 + 金额。
 *
 * 与首页「最近交易」样式保持一致，便于辨认。
 */
@Composable
private fun AutoRecordRow(item: AutoRecordItem, onClick: () -> Unit) {
    val tx = item.tx
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val amountColor = when (tx.type) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = when (tx.type) {
        TransactionType.EXPENSE -> "-"
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER -> ""
    }
    val categoryIcon = item.category?.let { categoryIconByKey(it.icon) } ?: Icons.Filled.Category
    val categoryColor = item.category?.let { cat ->
        if (cat.color != 0) Color(cat.color) else MaterialTheme.colorScheme.primary
    } ?: MaterialTheme.colorScheme.primary

    UnifiedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        variant = UnifiedCardVariant.ELEVATED,
        cornerRadius = Corner.large,
        contentPadding = PaddingValues(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    tx.note.ifBlank { item.category?.name ?: "未分类" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                Text(
                    "${dateFormat.format(Date(tx.date))} · ${item.accountName} · 自动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                "$sign¥${tx.amount.centsToYuan().toPlainString()}",
                color = amountColor,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/**
 * 调规则提示卡。
 *
 * 微信 / 支付宝的通知文案会随版本变化，规则失效时需要用 Logcat 看原始文案来补规则，
 * 所以把命令直接写在界面上，免得回头找不到。
 */
@Composable
private fun TuningTipCard() {
    UnifiedCard(
        modifier = Modifier.fillMaxWidth(),
        variant = UnifiedCardVariant.OUTLINED,
        cornerRadius = Corner.large,
    ) {
        Text(
            "识别不准怎么办",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "微信 / 支付宝的推送文案会随 App 版本变化。若有支付没记上，用电脑连手机执行下面的命令，" +
                "看「未命中」那几行的原始文案，把它补进 PaymentNotificationParser 的规则表即可。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.s))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.s))
        Text(
            "adb logcat -s JiYiBiNotify",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
