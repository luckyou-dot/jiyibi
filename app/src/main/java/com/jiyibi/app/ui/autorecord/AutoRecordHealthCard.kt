package com.jiyibi.app.ui.autorecord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.theme.BudgetAmber
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.notify.AutoRecordHealth

/**
 * 自动记账自检卡：把四项前置条件摊开给用户看，缺哪项就点哪项去修。
 *
 * ## 为什么要有这张卡
 * 系统在**强停应用**（ROM 的「清理后台」、划掉最近任务卡片）或**覆盖安装**后，
 * 会关闭本应用的无障碍服务与通知使用权，而且不会告知用户。现象就是
 * 「扫码支付了却没自动记账，进设置一看无障碍被关了」。
 *
 * 普通应用**无法自行重新开启**这两项授权（Android 的安全模型：只能由用户在系统设置里开），
 * 所以这张卡的职责是：把状态说清楚、把跳转做到一步、把「为什么会掉」讲明白，
 * 再配合后台巡检通知（`AutoRecordHealthWorker`）在用户不开 App 时也能知道。
 *
 * @param health            当前自检结果
 * @param autoRecordEnabled 自动记账总开关；关着时不做「已失效」强提示
 */
@Composable
internal fun HealthCheckCard(
    health: AutoRecordHealth,
    autoRecordEnabled: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    val issues = health.issues()
    val captureIssues = health.captureIssues()
    // 总开关开着、但两条抓取通道都断了：这正是「支付了却没记账」的状态，必须醒目
    val totallyBroken = autoRecordEnabled && !health.anyChannelReady

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
                        if (issues.isEmpty()) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            BudgetAmber.copy(alpha = 0.18f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (issues.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (issues.isEmpty()) MaterialTheme.colorScheme.primary else BudgetAmber,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "自动记账自检",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (issues.isEmpty()) "四项都已就绪" else "有 ${issues.size} 项待处理",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (issues.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        ExpenseRed
                    },
                )
            }
        }

        if (totallyBroken) {
            Spacer(Modifier.height(Spacing.s))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corner.medium))
                    .background(ExpenseRed.copy(alpha = 0.10f))
                    .padding(Spacing.m),
            ) {
                Text(
                    "自动记账当前已失效",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = ExpenseRed,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "系统在「清理后台 / 划掉最近任务卡片」或覆盖安装本应用后，会关闭通知使用权与无障碍服务，" +
                        "并且不会提醒你。这就是扫码付了钱却没记账的原因。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
                Button(
                    onClick = if (health.accessibility) onOpenNotificationAccess else onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("去重新开启")
                }
            }
        }

        Spacer(Modifier.height(Spacing.s))

        HealthRow(
            icon = if (health.notificationAccess) {
                Icons.Filled.NotificationsActive
            } else {
                Icons.Filled.NotificationsOff
            },
            title = "通知使用权",
            ok = health.notificationAccess,
            okText = "已开启：微信 / 支付宝支付通知能自动记账",
            issueText = "未开启：收不到支付通知，自动记账不会生效",
            actionLabel = "去开启",
            onAction = onOpenNotificationAccess,
        )

        HealthRow(
            icon = Icons.Filled.AccessibilityNew,
            title = "支付页面识别（无障碍）",
            ok = health.accessibility,
            okText = "已开启：扫码 / 转账支付当场记账",
            issueText = "已关闭：扫码、转账这类主动支付抓不到",
            actionLabel = "去开启",
            onAction = onOpenAccessibility,
        )

        if (!health.notificationsAllowed) {
            HealthRow(
                icon = Icons.Filled.NotificationsOff,
                title = "通知权限",
                ok = false,
                okText = "",
                issueText = "未授权：记账仍会入库，但成功后的横幅提醒弹不出来",
                actionLabel = "去授权",
                onAction = onRequestNotificationPermission,
            )
        }

        HealthRow(
            icon = Icons.Filled.BatterySaver,
            title = "省电与后台运行",
            ok = health.batteryUnrestricted,
            okText = "已加入电池优化白名单",
            issueText = "未加入：后台容易被系统冻结，通知与读屏都可能漏",
            actionLabel = "去设置",
            onAction = onOpenBatterySettings,
        )

        Spacer(Modifier.height(Spacing.s))
        Text(
            "国产 ROM 还建议：在应用详情里允许「自启动」、把省电策略设为「无限制」，" +
                "并在最近任务列表里把本应用锁定，避免清理后台时被强停。" +
                "Android 不允许应用自己重新开启这些授权，只能麻烦你手动开一次。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        TextButton(onClick = onOpenAppDetails) {
            Text("打开应用详情（自启动 / 省电策略）")
        }

        if (captureIssues.isNotEmpty() && !totallyBroken) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "提示：两项抓取通道只要有一项可用就能记账，但会漏掉对应的支付场景，建议都开启。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 自检清单里的一行：状态图标 + 标题 + 说明 + 右侧修复入口 */
@Composable
private fun HealthRow(
    icon: ImageVector,
    title: String,
    ok: Boolean,
    okText: String,
    issueText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else ExpenseRed,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.s))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (ok) okText else issueText,
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    ExpenseRed
                },
            )
        }
        if (!ok) {
            Spacer(Modifier.width(Spacing.s))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
