package com.jiyibi.app.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** DataStore 扩展属性：未识别通知独立 preferences 文件 */
private val Context.unmatchedDataStore by preferencesDataStore(name = "unmatched_notifications")

/**
 * 一条未能解析出账目、但来源确实是微信 / 支付宝的通知快照。
 *
 * @property packageName 来源包名
 * @property title        通知标题（如"服务通知"）
 * @property content      通知正文（截断后）
 * @property postedAt     通知发出时间（即 `StatusBarNotification.postTime`）
 */
data class UnmatchedNotification(
    val packageName: String,
    val title: String,
    val content: String,
    val postedAt: Long,
)

/**
 * 「未识别支付通知」环形队列。
 *
 * ## 为什么要有它
 * 通知文案会随微信 / 支付宝版本变化，规则一旦漏了新句式，之前唯一的现象是
 * Logcat 里多一行 `未命中`——用户在 App 里完全看不见，几个月都不会发现
 * 自动记账已经失效。把未命中的原始文案留在 App 里展示，失效就变成
 * 「看得到哪条没识别」，不必连电脑抓日志。
 *
 * ## 设计
 * - 用 DataStore 而非加表字段：本表是纯派生的辅助数据，丢了不影响账目，
 *   不值得占用一次 Room 迁移配额（项目约定见 [AppDatabase.MIGRATIONS]）；
 * - 条目以 JSON 数组序列化存单个 String（DataStore 只能存原语）；
 * - 按「包名+标题+正文」去重：同一条通知被系统重复投递 / 更新时，
 *   只刷新时间并挪到队首，不重复占位。
 */
@Singleton
class UnmatchedNotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** 队列容量上限：只保留最近这么多条 */
        const val MAX_ENTRIES = 20

        /** 单条文案截断长度，防止超长营销文撑爆 DataStore */
        private const val MAX_TEXT_LENGTH = 120

        private val KEY_ENTRIES = stringPreferencesKey("unmatched_entries")
    }

    /** 未识别通知列表，按时间倒序（最新在前） */
    val unmatched: Flow<List<UnmatchedNotification>> = context.unmatchedDataStore.data.map { prefs ->
        decode(prefs[KEY_ENTRIES])
    }

    /** 追加一条未识别通知；同文案重复投递时刷新时间并挪到队首 */
    suspend fun add(packageName: String, title: String, content: String, postedAt: Long) {
        val entry = UnmatchedNotification(
            packageName = packageName,
            title = title.take(MAX_TEXT_LENGTH),
            content = content.take(MAX_TEXT_LENGTH),
            postedAt = postedAt,
        )
        context.unmatchedDataStore.edit { prefs ->
            // 文案完全相同（包名+标题+正文）视为同一条通知的重发
            val rest = decode(prefs[KEY_ENTRIES]).filterNot {
                it.packageName == entry.packageName &&
                    it.title == entry.title &&
                    it.content == entry.content
            }
            prefs[KEY_ENTRIES] = encode(listOf(entry) + rest)
        }
    }

    /** 清空队列 */
    suspend fun clear() {
        context.unmatchedDataStore.edit { prefs -> prefs.remove(KEY_ENTRIES) }
    }

    private fun encode(entries: List<UnmatchedNotification>): String {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("pkg", it.packageName)
                    .put("title", it.title)
                    .put("content", it.content)
                    .put("at", it.postedAt),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<UnmatchedNotification> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            JSONArray(raw).let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    UnmatchedNotification(
                        packageName = obj.optString("pkg"),
                        title = obj.optString("title"),
                        content = obj.optString("content"),
                        postedAt = obj.optLong("at"),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
