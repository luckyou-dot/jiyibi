package com.jiyibi.app.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jiyibi.app.core.domain.model.RecurringFrequency
import com.jiyibi.app.core.domain.model.RecurringRule
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.balanceDeltas
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.RecurringRepository
import com.jiyibi.app.core.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.TimeZone

/**
 * 周期性记账执行 Worker。
 *
 * 调度：每 15 分钟由 [RecurringScheduler] 唤醒一次（WorkManager 最小周期）。
 *
 * 执行逻辑：
 * 1. 查询所有到期规则（getDue）
 * 2. 对 autoRecord=true 的规则：自动生成一笔交易 + 调整账户余额
 * 3. 对 autoRecord=false 的规则：跳过自动入账（仅推进下次执行时间）
 * 4. 推进 nextRunAt 到下个周期
 */
@HiltWorker
class RecurringWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recurringRepository: RecurringRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val dueRules = try {
            recurringRepository.getDue(now)
        } catch (e: Exception) {
            // DB 异常：下次再试
            return Result.retry()
        }

        dueRules.forEach { rule ->
            try {
                if (rule.autoRecord) {
                    // 自动入账：生成交易 + 调整账户余额
                    autoRecordTransaction(rule)
                }
                // autoRecord=false 时：不自动入账，仅推进下次执行时间
                // 推进到下个周期
                val nextRun = rule.nextRun(rule.frequency, rule.interval)
                recurringRepository.advanceNextRun(rule.id, nextRun)
            } catch (e: Exception) {
                // 单条规则失败不影响其他规则
            }
        }
        return Result.success()
    }

    /** 自动生成一笔交易并调整账户余额 */
    private suspend fun autoRecordTransaction(rule: RecurringRule) {
        val now = System.currentTimeMillis()
        val tx = Transaction(
            type = rule.type,
            amount = rule.amount,
            accountId = rule.accountId,
            categoryId = rule.categoryId,
            note = rule.title.ifBlank { "周期性记账" },
            date = now,
            recurringRuleId = rule.id,
        )
        transactionRepository.upsert(tx)
        // 同步账户余额：增减规则统一由 Transaction.balanceDeltas 定义，避免各处再写一份 when(type)
        tx.balanceDeltas().forEach { delta ->
            accountRepository.adjustBalance(delta.accountId, delta.delta)
        }
    }

    /** 计算下次执行时间：在 nextRunAt 基础上 + interval * frequency */
    private fun RecurringRule.nextRun(frequency: RecurringFrequency, interval: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = System.currentTimeMillis()
        when (frequency) {
            RecurringFrequency.DAILY -> cal.add(Calendar.DAY_OF_YEAR, interval)
            RecurringFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, interval)
            RecurringFrequency.MONTHLY -> cal.add(Calendar.MONTH, interval)
            RecurringFrequency.YEARLY -> cal.add(Calendar.YEAR, interval)
        }
        return cal.timeInMillis
    }
}
