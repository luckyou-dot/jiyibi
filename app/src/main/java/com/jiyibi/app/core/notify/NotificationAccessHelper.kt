package com.jiyibi.app.core.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 「通知使用权」状态检测与设置页跳转。
 *
 * 通知使用权（Notification access）**不是运行时权限**，没有 `requestPermissions()` 这条路——
 * 系统只允许 App 把用户送到设置页由用户手动开启。因此这里提供：
 * - [isEnabled]：读系统设置判断本 App 的监听服务是否已被授权
 * - [settingsIntent]：跳转到「通知使用权」列表页
 */
object NotificationAccessHelper {

    /** 系统 Settings.Secure 中记录已授权监听服务的键 */
    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    /**
     * 判断本 App 的通知监听服务是否已被用户授权。
     *
     * 系统把已授权的监听服务以「扁平化 ComponentName 用 `:` 连接」的形式存在
     * [ENABLED_LISTENERS] 里，例如 `com.a/com.a.Svc:com.b/com.b.Svc`。
     */
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
            ?: return false
        if (flat.isBlank()) return false

        val expected = ComponentName(context, PaymentNotificationListener::class.java)
        // 主判据：逐项做 ComponentName 相等比较
        val exact = flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
        if (exact) return true

        // 兜底：个别 ROM 写入的类名形式不规范（如缩写 ".core.notify.X"），
        // 此时退化为「本包名下是否有任一监听服务被授权」。
        return flat.contains(context.packageName)
    }

    /** 跳转到系统「通知使用权」设置页 */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
