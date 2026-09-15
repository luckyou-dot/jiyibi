package com.jiyibi.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jiyibi.app.core.data.DefaultBudgetProvisioner
import com.jiyibi.app.core.work.RecurringScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口。
 *
 * 负责：
 * - 触发 Hilt 依赖注入图初始化
 * - 配置 WorkManager（用于记账提醒、周期性记账等后台任务）
 * - 启动周期性记账 Worker 调度
 * - 预置默认月度总预算（¥1200）
 */
@HiltAndroidApp
class JiYiBiApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var recurringScheduler: RecurringScheduler

    @Inject
    lateinit var defaultBudgetProvisioner: DefaultBudgetProvisioner

    /** 应用级作用域：承载 Application 生命周期内的一次性初始化任务 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 启动周期性记账轮询（每 15 分钟检查一次到期规则）
        recurringScheduler.schedule()
        // 预置本月默认月度总预算（¥1200），使预算页与首页预算进度立即可见
        appScope.launch { defaultBudgetProvisioner.ensureCurrentMonthTotalBudget() }
    }
}
