package com.jiyibi.app.core.notify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 自动记账的自检缺项。
 *
 * 分四类是因为**修复方式各不相同**，用户需要知道点哪个按钮：
 * 通知使用权 / 无障碍服务要去系统设置授权（App 无法代劳），
 * 通知权限是运行时权限（可以弹窗申请），
 * 电池优化与自启动是 ROM 的保活设置（只能引导）。
 */
enum class AutoRecordIssue {
    /** 通知使用权未授权：系统「通知 → 通知使用权」里没勾选本应用 */
    NOTIFICATION_ACCESS,

    /** 无障碍服务未开启：被系统关闭（强停 / 覆盖安装 / ROM 清理后台）或从未开启 */
    ACCESSIBILITY,

    /** Android 13+ 未授予 POST_NOTIFICATIONS：记账提醒弹不出来（不影响入账） */
    NOTIFICATIONS_PERMISSION,

    /** 未加入电池优化白名单：后台更容易被系统冻结，读屏与通知都可能收不到 */
    BATTERY_OPTIMIZATION,
}

/**
 * 自动记账前置条件自检结果。
 *
 * ## 为什么会「权限自己关掉」
 * Android 上**只有用户能开关无障碍服务**，App 无法自行开启或恢复。以下情形会让系统
 * 直接把本应用的无障碍服务 / 通知使用权关掉，且**不会通知用户**：
 * 1. **强停（force-stop）**：MIUI 等 ROM 的「清理后台」、把最近任务卡片划掉，
 *    在系统看来等于强停应用 —— 无障碍与通知监听都会被关闭，必须手动重开；
 * 2. **覆盖安装 / 应用更新**：包被替换时服务解绑，多数机型不会自动恢复；
 * 3. ROM 安全中心的「省电 / 后台管理」清理。
 *
 * 所以这里能做的只有三件事：**尽早发现**（自检 + 后台巡检通知）、
 * **一键跳转重开**、**把保活设置讲清楚**。真正"自动恢复权限"在非 root / 非 shell 的
 * 普通应用上做不到，这是平台限制，不是没实现。
 *
 * @property notificationAccess 通知使用权是否已授权
 * @property accessibility      无障碍服务是否已开启
 * @property notificationsAllowed 是否持有 POST_NOTIFICATIONS（Android 13 以下恒为 true）
 * @property batteryUnrestricted 是否已加入电池优化白名单
 */
data class AutoRecordHealth(
    val notificationAccess: Boolean,
    val accessibility: Boolean,
    val notificationsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
) {

    /**
     * 两条抓取通道是否**都**就绪。
     *
     * 只开通知监听也能记账（用户在前台付款时抓不到，属已知盲区）；
     * 因此自检卡把「都就绪」当健康标准，但单通道可用时不拦着用户用。
     */
    val channelsReady: Boolean get() = notificationAccess && accessibility

    /** 是否有任一通道可用（决定「自动记账当前完全失效」这类强提示要不要出现） */
    val anyChannelReady: Boolean get() = notificationAccess || accessibility

    /** 当前缺失的项，顺序即建议修复顺序（先修影响记账的，再修保活的） */
    fun issues(): List<AutoRecordIssue> = buildList {
        if (!notificationAccess) add(AutoRecordIssue.NOTIFICATION_ACCESS)
        if (!accessibility) add(AutoRecordIssue.ACCESSIBILITY)
        if (!notificationsAllowed) add(AutoRecordIssue.NOTIFICATIONS_PERMISSION)
        if (!batteryUnrestricted) add(AutoRecordIssue.BATTERY_OPTIMIZATION)
    }

    /** 只影响记账本身的缺项（用于「自动记账失效」提示，不把保活设置算进来） */
    fun captureIssues(): List<AutoRecordIssue> = buildList {
        if (!notificationAccess) add(AutoRecordIssue.NOTIFICATION_ACCESS)
        if (!accessibility) add(AutoRecordIssue.ACCESSIBILITY)
    }

    /**
     * 该撤掉「自动记账已失效」提醒了吗。
     *
     * 判定只看**两条抓取通道**（与 [captureIssues] 同源，不把通知权限 / 电池白名单算进来）：
     * 那两项缺失只影响提醒与保活，不该左右"失效提醒撤不撤"。
     *
     * ## 为什么需要这个显式判定
     * 发提醒有三条路径（两个服务的解绑回调 + 后台巡检），撤销此前却只有巡检一条，
     * 而 `AutoRecordHealthScheduler.checkNow()` 只在**进程启动**时入队（真机上约 0.2s 跑完）。
     * 于是 **App 进程一直活着、只是被切回前台**时没有任何路径去撤那条通知：
     * 用户从系统设置把权限开回来、切回 App，自检卡已全绿，通知栏那条失效提醒却还挂着，
     * 看起来像没修好。所以用户**看得到自己已经把权限开回来**的时刻
     * （App 回到前台、「自动记账」页读到自检结果）必须能当场撤销它。
     */
    fun shouldClearAlert(): Boolean = captureIssues().isEmpty()
}

/**
 * 自检读取与设置页跳转。
 *
 * 读的都是系统设置（`Settings.Secure` / `PowerManager`），不会触发任何权限弹窗。
 */
object AutoRecordHealthChecker {

    fun check(context: Context): AutoRecordHealth = AutoRecordHealth(
        notificationAccess = NotificationAccessHelper.isEnabled(context),
        accessibility = AccessibilityAccessHelper.isEnabled(context),
        notificationsAllowed = hasNotificationPermission(context),
        batteryUnrestricted = isBatteryUnrestricted(context),
    )

    /** Android 13+ 才需要运行时通知权限；低版本恒为 true */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 是否已在电池优化白名单里（读不到 PowerManager 时按"已放行"处理，避免误报） */
    fun isBatteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 系统「无障碍」设置页 */
    fun accessibilitySettingsIntent(): Intent = AccessibilityAccessHelper.settingsIntent()

    /** 系统「通知使用权」设置页 */
    fun notificationAccessSettingsIntent(): Intent = NotificationAccessHelper.settingsIntent()

    /**
     * 电池优化设置页。
     *
     * 刻意**不申请** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 特殊权限去直接弹
     * 「是否允许后台运行」的系统对话框：那条路径需要声明一个在应用商店里受限制的权限，
     * 而把用户送到设置页自己选，效果一样且没有合规负担。
     */
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 本应用详情页：MIUI / EMUI 的「自启动」「省电策略」「后台运行」都在这里 */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
