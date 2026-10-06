package com.jiyibi.app.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore 扩展属性：自动记账偏好独立 preferences 文件 */
private val Context.autoRecordDataStore by preferencesDataStore(name = "auto_record_preferences")

/**
 * 自动记账偏好仓库。
 *
 * 保存两类信息：
 * 1. **功能开关**：通知自动记账是否启用（默认开启）；
 * 2. **最近自动记录的交易 id 队列**：按时间倒序保存最近若干条自动记账产生的交易 id。
 *
 * 为什么用「id 队列」而不是给 `transactions` 表加 `source` 字段？
 * 加字段属于 Room schema 变更，按项目约定必须显式写 Migration（见 [AppDatabase.MIGRATIONS]，
 * 本项目刻意不用 `fallbackToDestructiveMigration()`，缺 Migration 会启动失败而非清库）。
 * 而「最近自动记录」本质上是一个容量有限的**复核队列**，只关心最近这几十条，
 * 用 DataStore 维护成本更低，也省掉一次迁移。真要按来源做长期统计
 * （比如「自动记账占总支出多少」）时，再加字段并配好 Migration 也不迟
 * —— 届时迁移成本只是一条 `ALTER TABLE`，不必因为"怕清库"而回避。
 *
 * 注意：从 JSON 备份恢复后，恢复出的交易 id 会变化，本队列可能指向已不存在的记录；
 * 使用方（`AutoRecordViewModel`）在查询时会自然过滤掉查不到的 id。
 */
@Singleton
class AutoRecordPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** 队列容量上限：只保留最近这么多条自动记录供复核 */
        const val MAX_RECENT = 50

        private val KEY_ENABLED = booleanPreferencesKey("auto_record_enabled")
        private val KEY_RECENT_IDS = stringPreferencesKey("auto_record_recent_ids")
    }

    /** 功能开关，默认 `true`（装上就先开着，用户嫌吵再去关） */
    val enabled: Flow<Boolean> = context.autoRecordDataStore.data.map { prefs ->
        prefs[KEY_ENABLED] ?: true
    }

    /**
     * 最近自动记录的交易 id，**按时间倒序**（最新的在前）。
     *
     * 以逗号分隔字符串存储而非 `stringSet`：Set 无序，会丢掉「哪条更新」的信息，
     * 而复核列表恰恰最需要按新到旧排列。
     */
    val recentIds: Flow<List<Long>> = context.autoRecordDataStore.data.map { prefs ->
        decodeIds(prefs[KEY_RECENT_IDS])
    }

    /** 设置功能开关 */
    suspend fun setEnabled(enabled: Boolean) {
        context.autoRecordDataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    /** 把新自动记录的交易 id 放到队首，超出容量则丢弃最旧的 */
    suspend fun addRecentId(id: Long) {
        context.autoRecordDataStore.edit { prefs ->
            val current = decodeIds(prefs[KEY_RECENT_IDS])
            val updated = (listOf(id) + current.filter { it != id }).take(MAX_RECENT)
            prefs[KEY_RECENT_IDS] = updated.joinToString(",")
        }
    }

    /** 从队列中移除某个 id（用户删除或撤销某条自动记录后调用） */
    suspend fun removeRecentId(id: Long) {
        context.autoRecordDataStore.edit { prefs ->
            val updated = decodeIds(prefs[KEY_RECENT_IDS]).filter { it != id }
            prefs[KEY_RECENT_IDS] = updated.joinToString(",")
        }
    }

    /** 解析逗号分隔的 id 字符串，容忍空值、空白与脏数据 */
    private fun decodeIds(raw: String?): List<Long> =
        raw?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()
}
