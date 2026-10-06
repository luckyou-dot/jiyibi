package com.jiyibi.app.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore 扩展属性：商户→分类学习表独立 preferences 文件 */
private val Context.merchantCategoryDataStore by
    preferencesDataStore(name = "merchant_category_map")

/**
 * 「商户名 → 分类 id」学习表。
 *
 * ## 为什么需要它
 * [com.jiyibi.app.core.notify.PaymentNotificationParser] 内置的关键词表是**静态**的，
 * 且存在两个固有缺陷：表格顺序决定优先级（「美团打车」会被「餐饮」组的 `美团` 先命中），
 * 且返回的是**分类名**，用户一旦在分类管理里改名就静默失效。
 * 更关键的是：用户每次手工把自动记账的分类改对之后，下次同一个商户还是会猜错——**系统不学习**。
 *
 * 本仓库解决最后这一点：用户在编辑页把某条自动记账的分类改掉时，
 * 把「该条交易的商户名 → 用户选中的分类 id」记下来；下次同样的商户名出现时，
 * **优先采用学习结果**，静态关键词表退化为首次遇见的冷启动兜底。
 *
 * ## 为什么存 id 而不是分类名
 * 分类名可被用户随意重命名，存名字会在改名后失效；存 id 则天然跟随改名。
 * 分类被删除时，读取端会因查不到该 id 而自动回退到静态关键词表（见
 * [com.jiyibi.app.core.notify.PaymentRecorder.resolveCategory]）。
 *
 * ## 为什么用 DataStore 而不是 Room
 * 这张表是纯派生的辅助数据，丢了也只是退化为猜测，放在 DataStore 可以省掉一次数据库升级；
 * 而 Room 侧的项目约定是**每次 schema 变更都必须显式写 Migration**（见
 * [com.jiyibi.app.core.database.AppDatabase.MIGRATIONS]，本项目刻意不用
 * `fallbackToDestructiveMigration()`，缺 Migration 会启动失败而非清库），
 * 辅助数据不值得为它占用一次迁移配额。
 */
@Singleton
class MerchantCategoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /**
         * 学习表条目上限。超出后丢弃最早写入的条目（FIFO）。
         *
         * 设上限是为了防止长期使用后 Key 无限膨胀：DataStore 每次读取都要反序列化整个文件，
         * 条目过多会拖慢启动路径。200 条足够覆盖个人用户的常去商户。
         */
        const val MAX_ENTRIES = 200

        private val KEY_MAP = stringPreferencesKey("merchant_category_map")

        /** 条目分隔符：商户名可能含任意字符，故用 ASCII 控制字符分隔 */
        private const val ENTRY_SEPARATOR = "\u001F"
        private const val FIELD_SEPARATOR = "\u001E"

        /**
         * 商户名归一化：去首尾空白、去掉常见的金额与标点尾巴，并统一大小写。
         *
         * 不做归一化的话，「星巴克」与「星巴克 」会被当成两个商户，学习表很快就充满重复项。
         */
        fun normalizeMerchant(raw: String): String = raw
            .trim()
            // 去掉尾部可能粘上的标点与金额片段，如「星巴克 35.00」「星巴克，」
            .trimEnd('，', ',', '。', '.', ':', '：', ' ', '\n', '\t')
            .replace(Regex("\\s*[¥￥]?\\s*[0-9]+(?:\\.[0-9]{1,2})?\\s*元?\\s*$"), "")
            .trim()
            .lowercase()
    }

    /** 学习表快照：归一化商户名 → 分类 id */
    val mappings: Flow<Map<String, Long>> = context.merchantCategoryDataStore.data.map { prefs ->
        decode(prefs[KEY_MAP])
    }

    /**
     * 记录一条学习结果：`merchantName → categoryId`。
     *
     * 同一个商户再次被纠正时直接覆盖旧值（以最新一次用户选择为准）。
     *
     * @param merchantName 商户名（内部会做归一化，可传未清理的原始值）
     * @param categoryId   用户在编辑页最终选中的分类 id；传 `null` 表示用户**刻意清空**了分类，
     *                     此时也会写入一条「映射到 0」的墓碑记录，避免下次又被自动猜上分类
     */
    suspend fun learn(merchantName: String, categoryId: Long?) {
        val key = normalizeMerchant(merchantName)
        if (key.isEmpty()) return
        // 0 作为「用户明确要求未分类」的墓碑值：静态关键词表命中后会被它覆盖为 null
        val value = categoryId ?: CATEGORY_TOMBSTONE

        context.merchantCategoryDataStore.edit { prefs ->
            val current = decode(prefs[KEY_MAP])
            // LinkedHashMap 语义：删掉旧 key 再放回队尾，保证 FIFO 淘汰的是真正的老条目
            val updated = LinkedHashMap(current)
            updated.remove(key)
            updated[key] = value
            val trimmed = if (updated.size > MAX_ENTRIES) {
                updated.entries.drop(updated.size - MAX_ENTRIES).associate { it.key to it.value }
            } else {
                updated
            }
            prefs[KEY_MAP] = encode(trimmed)
        }
    }

    /**
     * 查询某商户的学习结果。
     *
     * @return 命中的分类 id；命中「未分类墓碑」时返回 [Long.MIN_VALUE] 之外的哨兵需由调用方区分，
     *         故这里改为返回三态：[LookupResult]
     */
    suspend fun lookup(merchantName: String): LookupResult {
        val key = normalizeMerchant(merchantName)
        if (key.isEmpty()) return LookupResult.Unknown
        val value = mappings.first()[key] ?: return LookupResult.Unknown
        return if (value == CATEGORY_TOMBSTONE) LookupResult.ExplicitlyUncategorized
        else LookupResult.Matched(value)
    }

    /** 清空学习表（「自动记账」页的「重置分类学习」入口调用） */
    suspend fun clear() {
        context.merchantCategoryDataStore.edit { prefs -> prefs.remove(KEY_MAP) }
    }

    /** 某个分类被删除后，清理所有指向它的学习条目，避免留下悬空 id */
    suspend fun forgetCategory(categoryId: Long) {
        context.merchantCategoryDataStore.edit { prefs ->
            val current = decode(prefs[KEY_MAP])
            val filtered = current.filterValues { it != categoryId }
            if (filtered.size != current.size) prefs[KEY_MAP] = encode(filtered)
        }
    }

    /** 商户名 → 分类 id 的学习结果三态 */
    sealed interface LookupResult {
        /** 没有该商户的任何学习记录，交给静态关键词表 */
        data object Unknown : LookupResult

        /** 学到过：该商户属于这个分类 */
        data class Matched(val categoryId: Long) : LookupResult

        /** 学到过：用户明确把该商户设为「未分类」，不要再自动猜 */
        data object ExplicitlyUncategorized : LookupResult
    }

    // ------------------------------------------------------------------
    // 编解码
    // ------------------------------------------------------------------

    /**
     * 编码为 `key\u001E value\u001F key\u001E value ...` 的单字符串。
     *
     * 不用 `stringSet`：Set 无序，无法做 FIFO 淘汰；也不用 JSON 库，
     * 避免为一张小表引入额外依赖。
     */
    private fun encode(map: Map<String, Long>): String =
        map.entries.joinToString(ENTRY_SEPARATOR) { (k, v) -> "$k$FIELD_SEPARATOR$v" }

    /** 解码，容忍脏数据：字段缺失、id 非数字的条目直接跳过 */
    private fun decode(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, Long>()
        raw.split(ENTRY_SEPARATOR).forEach { entry ->
            val idx = entry.indexOf(FIELD_SEPARATOR)
            if (idx <= 0) return@forEach
            val key = entry.substring(0, idx)
            val value = entry.substring(idx + 1).toLongOrNull() ?: return@forEach
            if (key.isNotBlank()) result[key] = value
        }
        return result
    }
}

/** 「用户明确要求未分类」的墓碑值，真实分类 id 不会为 0（Room 自增从 1 开始） */
private const val CATEGORY_TOMBSTONE = 0L
