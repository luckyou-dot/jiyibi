package com.jiyibi.app.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jiyibi.app.MainActivity
import com.jiyibi.app.R
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.centsToYuan

/**
 * 自动记账成功后的横幅提醒（heads-up notification）。
 *
 * ## 为什么用通知而不是悬浮窗
 * 应用内弹窗只有在 App 前台时可见；跨 App 的悬浮窗需要
 * `SYSTEM_ALERT_WINDOW` 特殊权限，多数 ROM 引导苛刻且观感突兀。
 * 高优先级通知渠道在锁屏 / 息屏 / 任意界面下都能弹出横幅，
 * 是「入库后提醒用户」这一需求的标准做法。
 *
 * ## 权限
 * Android 13+ 弹通知需要运行时 `POST_NOTIFICATIONS` 授权。
 * 未授权时这里**静默跳过**——交易已经入库，提醒只是锦上添花，
 * 不能因为没权限就打断或报错。
 */
object AutoRecordNotifier {

    /** 点击通知跳编辑页时携带的交易 id */
    const val EXTRA_EDIT_TRANSACTION_ID = "edit_transaction_id"

    /** 点击自检提醒时携带的标记：要求 App 直接落在「自动记账」页 */
    const val EXTRA_OPEN_AUTO_RECORD = "open_auto_record"

    private const val CHANNEL_ID = "auto_record"
    private const val CHANNEL_NAME = "自动记账提醒"

    /** 服务掉线提醒的独立渠道：重要性高（要能弹出来），但独立于记账成功提醒便于单独关闭 */
    private const val ALERT_CHANNEL_ID = "auto_record_alert"
    private const val ALERT_CHANNEL_NAME = "自动记账失效提醒"

    /**
     * 服务掉线提醒的固定通知 id。
     *
     * 用固定 id 而不是每条一个：这是**持续状态**（权限没恢复就一直有效），
     * 重复上报只会原地更新同一条通知，配合 `setOnlyAlertOnce(true)` 不会反复响铃。
     * 恢复健康后由 [clearServiceDown] 主动撤掉。
     */
    private const val SERVICE_DOWN_NOTIFICATION_ID = 0x5EED

    /**
     * 发出（或更新）「自动记账已失效」提醒。
     *
     * 触发点有三处：App 内自检、后台巡检 Worker、无障碍服务被系统解绑的瞬间
     * （见 `PaymentAccessibilityService.onUnbind`）。三者都走这一个入口，
     * 保证文案与跳转一致。
     *
     * 通知带一个「去开启无障碍」按钮：这是最常见的缺项，直接送到系统设置页，
     * 省掉"打开 App → 我的 → 自动记账 → 找按钮"的路径。
     */
    fun notifyServiceDown(context: Context, health: AutoRecordHealth) {
        // 连通知权限都没有时，任何提醒都发不出去（Android 13+）
        if (!hasNotificationPermission(context)) return

        ensureAlertChannel(context)

        val issues = health.captureIssues()
        val text = when {
            issues.size >= 2 -> "通知使用权与无障碍服务都被系统关闭了，支付后将不再自动记账"
            AutoRecordIssue.ACCESSIBILITY in issues -> "无障碍服务已被系统关闭，支付后将不再自动记账"
            else -> "通知使用权已被系统关闭，支付后将不再自动记账"
        }

        // 内容点击：打开 App 的「自动记账」页（那里有完整自检清单与修复按钮）
        val openApp = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_AUTO_RECORD, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            SERVICE_DOWN_NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 动作按钮：直接进系统无障碍设置页（缺的是通知使用权时改跳那一页）
        val target = if (AutoRecordIssue.ACCESSIBILITY in issues) {
            AutoRecordHealthChecker.accessibilitySettingsIntent()
        } else {
            AutoRecordHealthChecker.notificationAccessSettingsIntent()
        }
        val fixIntent = PendingIntent.getActivity(
            context,
            SERVICE_DOWN_NOTIFICATION_ID + 1,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fixLabel = if (AutoRecordIssue.ACCESSIBILITY in issues) "去开启无障碍" else "去开启通知使用权"

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("自动记账已失效")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text\n\n系统在强停应用（清理后台 / 划掉最近任务）或覆盖安装后会关闭这两项授权，" +
                        "需要手动重新开启。",
                ),
            )
            .setContentIntent(contentIntent)
            .addAction(0, fixLabel, fixIntent)
            // 权限没恢复前不要每次巡检都响一遍
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context)
            .notify(SERVICE_DOWN_NOTIFICATION_ID, notification)
    }

    /** 自检恢复健康后撤掉失效提醒（幂等，没有通知时调用也无副作用） */
    fun clearServiceDown(context: Context) {
        NotificationManagerCompat.from(context).cancel(SERVICE_DOWN_NOTIFICATION_ID)
    }

    /**
     * 发出「已自动记账」横幅通知，点击直达该笔交易的编辑页。
     *
     * @param transactionId  新入库交易 id（同时用作通知 id，天然一笔一条）
     * @param categoryName   解析出的分类名，未分类时传 null
     * @param accountName    落到的账户名（展示用，便于当场核对支付方式匹配是否正确）
     * @param sourcePackage  通知来源包名（微信 / 支付宝），用于展示来源
     */
    fun notifyRecorded(
        context: Context,
        transactionId: Long,
        type: TransactionType,
        amountCents: Long,
        note: String,
        categoryName: String?,
        accountName: String,
        sourcePackage: String,
    ) {
        // Android 13+ 必须持有运行时通知权限，否则 notify 静默失效甚至抛 SecurityException
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val sign = if (type == TransactionType.EXPENSE) "-" else "+"
        val amountText = "¥${amountCents.centsToYuan().toPlainString()}"
        val detail = listOfNotNull(
            note.ifBlank { null },
            categoryName ?: "未分类",
            accountName,
            PaymentPackages.displayName(sourcePackage),
        ).joinToString(" · ")

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_EDIT_TRANSACTION_ID, transactionId)
            // SINGLE_TOP：MainActivity 已在前台时走 onNewIntent，避免销毁重建闪烁
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(transactionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("已自动记账 $sign$amountText")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // 渠道 importance 已是 HIGH；这里同时设 priority 兜底旧系统的 heads-up 行为
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(transactionId), notification)
    }

    /** 交易 id → 非负通知 id：Long 直接 hashCode 会产出负值且高位截断后易碰撞 */
    private fun notificationId(transactionId: Long): Int =
        (transactionId and 0x7FFFFFFFL).toInt()

    /** 通知权限检查统一走自检模块，避免两处判断漂移 */
    private fun hasNotificationPermission(context: Context): Boolean =
        AutoRecordHealthChecker.hasNotificationPermission(context)

    /** 创建高优先级渠道（minSdk 26，无需版本判断也保留写法习惯） */
    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "支付通知自动记账成功后的横幅提醒"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 创建「自动记账失效」提醒渠道 */
    private fun ensureAlertChannel(context: Context) {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "无障碍服务 / 通知使用权被系统关闭时的提醒"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
