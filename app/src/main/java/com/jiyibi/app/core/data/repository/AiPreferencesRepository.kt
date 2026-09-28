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

/** DataStore 扩展属性：AI 智能识别偏好独立 preferences 文件 */
private val Context.aiDataStore by preferencesDataStore(name = "ai_preferences")

/** AI 智能识别配置快照 */
data class AiConfig(
    val enabled: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = DEFAULT_API_KEY,
    val model: String = DEFAULT_MODEL,
) {
    /** 开关开启且已填 Key，才会真正发起 AI 调用 */
    val isConfigured: Boolean get() = enabled && apiKey.isNotBlank() && baseUrl.isNotBlank()

    companion object {
        /**
         * 填表默认值：Agnes AI（OpenAI 兼容，agnes-2.5-flash 当前免费）。
         * 只是省去手输地址，**不代表会发请求**——没有 Key 时 [isConfigured] 为 false。
         */
        const val DEFAULT_BASE_URL = "https://api.agnes-ai.cn/v1"
        const val DEFAULT_MODEL = "agnes-2.5-flash"

        /**
         * API Key 出厂为空，必须由使用者在「自动记账 → AI 智能识别」里亲手填入。
         *
         * **绝不能把真实 Key 写进这个常量**：本仓库有公开 Git 远端，常量会同时进入
         * Git 历史与 APK 产物（`strings` 就能搜出来），等于把 Key 公开分发给所有安装者，
         * 且提交过就很难从历史里抹掉。
         */
        const val DEFAULT_API_KEY = ""
    }
}

/**
 * AI 智能识别偏好仓库。
 *
 * 出厂预置 OpenAI 兼容服务的地址与模型名作为填表默认值；API Key 一律留空，
 * 用户可改成任意兼容服务（智谱、DeepSeek、Moonshot、自建网关）并填入自己的 Key。
 * Key 只保存在本机 DataStore，不上传、不参与备份导出、不进版本库。
 */
@Singleton
class AiPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("ai_enabled")
        private val KEY_BASE_URL = stringPreferencesKey("ai_base_url")
        private val KEY_API_KEY = stringPreferencesKey("ai_api_key")
        private val KEY_MODEL = stringPreferencesKey("ai_model")
    }

    val config: Flow<AiConfig> = context.aiDataStore.data.map { prefs ->
        AiConfig(
            enabled = prefs[KEY_ENABLED] ?: false,
            baseUrl = prefs[KEY_BASE_URL] ?: AiConfig.DEFAULT_BASE_URL,
            apiKey = prefs[KEY_API_KEY] ?: AiConfig.DEFAULT_API_KEY,
            model = prefs[KEY_MODEL] ?: AiConfig.DEFAULT_MODEL,
        )
    }

    suspend fun save(config: AiConfig) {
        context.aiDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = config.enabled
            prefs[KEY_BASE_URL] = config.baseUrl.trim()
            prefs[KEY_API_KEY] = config.apiKey.trim()
            prefs[KEY_MODEL] = config.model.trim().ifBlank { AiConfig.DEFAULT_MODEL }
        }
    }
}
