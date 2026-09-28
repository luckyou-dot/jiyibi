package com.jiyibi.app.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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

    private const val CHANNEL_ID = "auto_record"
    private const val CHANNEL_NAME = "自动记账提醒"

    /**
     * 发出「已自动记账」横幅通知，点击直达该笔交易的编辑页。
     *
     * @param transactionId  新入库交易 id（同时用作通知 id，天然一笔一条）
     * @param categoryName   解析出的分类名，未分类时传 null
     * @param sourcePackage  通知来源包名（微信 / 支付宝），用于展示来源
     */
    fun notifyRecorded(
        context: Context,
        transactionId: Long,
        type: TransactionType,
        amountCents: Long,
        note: String,
        categoryName: String?,
        sourcePackage: String,
    ) {
        // Android 13+ 必须持有运行时通知权限，否则 notify 静默失效甚至抛 SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val sign = if (type == TransactionType.EXPENSE) "-" else "+"
        val amountText = "¥${amountCents.centsToYuan().toPlainString()}"
        val detail = listOfNotNull(
            note.ifBlank { null },
            categoryName ?: "未分类",
            PaymentPackages.displayName(sourcePackage),
        ).joinToString(" · ")

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_EDIT_TRANSACTION_ID, transactionId)
            // SINGLE_TOP：MainActivity 已在前台时走 onNewIntent，避免销毁重建闪烁
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            transactionId.hashCode(),
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

        NotificationManagerCompat.from(context).notify(transactionId.hashCode(), notification)
    }

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
}
