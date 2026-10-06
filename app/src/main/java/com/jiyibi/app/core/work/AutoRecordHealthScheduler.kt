package com.jiyibi.app.core.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动记账健康巡检的调度器。
 *
 * - [schedulePeriodic]：App 启动时调用一次（KEEP 策略去重），之后由 WorkManager 每
 *   [PERIODIC_HOURS] 小时唤醒一次；
 * - [checkNow]：App 启动（`JiYiBiApp.onCreate`）时入队的一次性任务，覆盖"进程已死、
 *   用户重新打开 App"这一条路径。
 *
 * ## 撤销提醒不归这里管
 * 巡检**只在进程启动时**入队，所以服务解绑回调（`PaymentAccessibilityService.onUnbind` /
 * `PaymentNotificationListener.onListenerDisconnected`）**不调用** [checkNow]：它们直接走
 * `AutoRecordNotifier.notifyServiceDown` 发提醒，更及时，也避免每次解绑都往 WorkManager 塞任务。
 * 而"撤销"发生在用户看得见结果的时刻（`MainActivity.onResume` 与「自动记账」页），
 * 由 `AutoRecordNotifier.reconcileServiceDown` 当场收尾 —— 否则进程一直活着时没人撤那条通知。
 *
 * 为什么是 6 小时而不是 15 分钟：这不是实时功能，只是在"用户几天没打开 App"时
 * 尽早发现权限掉线；过于频繁只会白耗电。
 */
@Singleton
class AutoRecordHealthScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 周期巡检（幂等：重复调用不会叠加任务） */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<AutoRecordHealthWorker>(
            PERIODIC_HOURS,
            TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** App 启动时立即巡检一次（一次性任务；**不用于服务解绑**，原因见类注释） */
    fun checkNow() {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<AutoRecordHealthWorker>().build())
    }

    companion object {
        private const val WORK_NAME = "auto_record_health"

        /** 巡检周期（小时） */
        const val PERIODIC_HOURS = 6L
    }
}
