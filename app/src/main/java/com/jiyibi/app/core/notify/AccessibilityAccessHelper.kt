package com.jiyibi.app.core.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 「无障碍服务」授权状态检测与设置页跳转。
 *
 * 与通知使用权一样，无障碍**不是运行时权限**，只能送用户到系统设置页手动开启。
 * 系统设置页里服务显示为「记一笔 · 支付页面识别」。
 */
object AccessibilityAccessHelper {

    /** 系统 Settings.Secure 中已启用无障碍总开关的键 */
    private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"

    /** 已启用无障碍服务的扁平化 ComponentName 列表键（`:` 连接） */
    private const val KEY_ENABLED_SERVICES = "enabled_accessibility_services"

    /** 判断本 App 的支付页面识别服务是否已被用户启用 */
    fun isEnabled(context: Context): Boolean {
        val cr = context.contentResolver
        if (Settings.Secure.getInt(cr, KEY_ACCESSIBILITY_ENABLED, 0) != 1) return false
        val flat = Settings.Secure.getString(cr, KEY_ENABLED_SERVICES) ?: return false
        if (flat.isBlank()) return false

        val expected = ComponentName(context, PaymentAccessibilityService::class.java)
        return flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
    }

    /** 跳转到系统「无障碍」设置页（各 ROM 呈现不同，需用户自行找到服务开关） */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
