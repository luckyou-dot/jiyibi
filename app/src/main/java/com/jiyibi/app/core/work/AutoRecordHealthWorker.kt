package com.jiyibi.app.core.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jiyibi.app.core.data.repository.AutoRecordPreferencesRepository
import com.jiyibi.app.core.notify.AutoRecordHealthChecker
import com.jiyibi.app.core.notify.AutoRecordNotifier
import com.jiyibi.app.core.notify.PaymentRecorder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 自动记账自检 Worker：定期确认「通知使用权 + 无障碍服务」还在，掉线就提醒用户。
 *
 * ## 为什么需要它
 * 系统在**强停应用**（ROM 的「清理后台」、划掉最近任务卡片）或**覆盖安装**后，
 * 会关闭本应用的无障碍服务与通知使用权，而且**不会告诉用户**。
 * 之前的唯一现象是"支付了但没记账"，用户往往过几天才发现。
 * 这个 Worker 每 [AutoRecordHealthScheduler.PERIODIC_HOURS] 小时查一次，
 * 掉线时发一条带「去开启无障碍」按钮的通知。
 *
 * ## 能力边界（必须说清楚）
 * - 强停后 WorkManager 本身也跑不了，直到用户**再次打开 App**；因此这条巡检
 *   是"尽力而为"的第二道网，主要覆盖 ROM 清理、系统升级等进程还活着的场景；
 * - 真正即时的提醒来自两个服务自身的解绑回调
 *   （`PaymentAccessibilityService.onUnbind` / `PaymentNotificationListener.onListenerDisconnected`）。
 *
 * ## 什么时候不打扰
 * - 用户自己关掉了自动记账开关 → 直接撤掉提醒、不巡检；
 * - 恢复健康 → 主动撤掉之前那条失效提醒（幂等）。
 */
@HiltWorker
class AutoRecordHealthWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val autoRecordPreferences: AutoRecordPreferencesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // 开关关着就不必打扰：用户主动选择不自动记账
        if (!autoRecordPreferences.enabled.first()) {
            AutoRecordNotifier.clearServiceDown(context)
            return Result.success()
        }

        val health = AutoRecordHealthChecker.check(context)
        // 打一行自检日志：`adb logcat -s JiYiBiNotify` 就能看清到底哪一项掉了，
        // 不必让用户去系统设置里逐条核对
        Log.i(
            PaymentRecorder.TAG,
            "自动记账自检：通知使用权=${health.notificationAccess} 无障碍=${health.accessibility} " +
                "通知权限=${health.notificationsAllowed} 省电白名单=${health.batteryUnrestricted}",
        )
        if (health.captureIssues().isEmpty()) {
            // 已恢复：撤掉历史提醒，避免用户误以为还坏着
            AutoRecordNotifier.clearServiceDown(context)
        } else {
            Log.w(
                PaymentRecorder.TAG,
                "自动记账不可用，缺项=${health.captureIssues().joinToString()}，已发出失效提醒",
            )
            AutoRecordNotifier.notifyServiceDown(context, health)
        }
        // 读系统设置不会失败；即便异常也没必要重试打扰用户
        return Result.success()
    }
}
