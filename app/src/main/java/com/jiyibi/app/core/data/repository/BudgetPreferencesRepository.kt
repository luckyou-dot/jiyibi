package com.jiyibi.app.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** DataStore 扩展属性：预算偏好独立 preferences 文件 */
private val Context.budgetDataStore by preferencesDataStore(name = "budget_preferences")

/**
 * 预算偏好仓库：记录「默认月度总预算」的预置状态。
 *
 * 应用会为当前月份自动预置一条默认月度总预算（见 `BudgetViewModel`），
 * 本仓库用 [seededPeriodStart] 记录已经预置过默认预算的月份起点，作用有二：
 *
 * 1. 同一个月内不重复预置，避免每次进入预算页都新建一行；
 * 2. 用户主动删除该默认预算后不再强行补回，尊重用户意图。
 *
 * 跨月后 `periodStart` 变化，会自动为新月份重新预置默认预算。
 */
@Singleton
class BudgetPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_SEEDED_PERIOD_START =
            longPreferencesKey("default_budget_seeded_period_start")
    }

    /** 已预置默认预算的月份起点（periodStart 毫秒）；从未预置过时返回 null */
    suspend fun seededPeriodStart(): Long? =
        context.budgetDataStore.data.first()[KEY_SEEDED_PERIOD_START]

    /** 标记指定月份已完成默认预算预置 */
    suspend fun markSeeded(periodStart: Long) {
        context.budgetDataStore.edit { prefs ->
            prefs[KEY_SEEDED_PERIOD_START] = periodStart
        }
    }
}
