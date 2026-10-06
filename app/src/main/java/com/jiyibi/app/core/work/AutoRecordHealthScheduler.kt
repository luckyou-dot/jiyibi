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
 * - [checkNow]：需要立刻确认状态时用（App 启动、服务解绑），一次性任务。
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

    /** 立即巡检一次（一次性） */
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
