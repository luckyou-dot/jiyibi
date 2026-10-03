package com.jiyibi.app.core.ai

import android.util.Log
import com.jiyibi.app.core.data.repository.AiConfig
import com.jiyibi.app.core.data.repository.AiPreferencesRepository
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.notify.ParsedPayment
import com.jiyibi.app.core.notify.PaymentNotificationParser
import com.jiyibi.app.core.notify.PaymentPackages
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 审核结论（见 [AiPaymentParser.review]）。
 *
 * @property isPayment 模型判定这是否确为一笔已完成的收付款；false 表示应放弃记账
 * @property merchant  模型提取的商户 / 对方名称，取不到时为空串
 * @property note      模型给出的短备注（≤ 12 字），只描述这一笔支付
 */
data class AiReview(
    val isPayment: Boolean,
    val merchant: String,
    val note: String,
)

/**
 * AI 智能识别：把本地规则啃不动的支付文案交给大模型兜底。
 *
 * ## 三个职责
 * 1. **未识别通知解析**：本地规则表返回 null 的微信 / 支付宝通知，
 *    让模型判断是否是一笔已完成的收支并抽取金额 / 方向 / 商户；
 * 2. **分类猜测增强**：本地关键词表猜不到分类时，把用户的分类列表
 *    给模型，让它选一个最合适的（猜不出返回 null，保持「未分类」）；
 * 3. **内容审核（[review]）**：无障碍读屏拿到的是**整屏窗口文字**，
 *    本地规则只能判断"文本里出现了支付语义词"，无法判断"这一屏到底是
 *    一笔真实支付还是聊天/账单页"。审核步骤让模型复核并输出干净备注。
 *
 * ## 调用原则
 * - **规则永远优先**：AI 只做兜底，规则能命中的账不走网络；
 * - **未配置不工作**：开关关着或没填 API Key 时所有方法静默返回 null，
 *   行为与没有 AI 时完全一致；
 * - **失败即放弃**：网络异常 / 超时 / 返回不成 JSON 一律返回 null，
 *   绝不能因为 AI 挂了影响记账主流程。
 *
 * ## 协议
 * OpenAI 兼容 `POST {baseUrl}/chat/completions`，
 * 默认指向智谱开放平台（GLM-4-Flash 免费模型），可自定义任意兼容网关。
 */
@Singleton
class AiPaymentParser @Inject constructor(
    private val aiPreferences: AiPreferencesRepository,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .readTimeout(java.time.Duration.ofSeconds(20))
        .build()

    /** 是否已具备调用条件（开关开 + Key 已填） */
    suspend fun isConfigured(): Boolean = aiPreferences.config.first().isConfigured

    /**
     * 让大模型解析一条本地规则未命中的通知。
     *
     * @return 可落库的解析结果；模型判定不是已完成收支、或任何环节失败时返回 null
     */
    suspend fun parsePaymentNotification(
        packageName: String,
        title: String,
        content: String,
    ): ParsedPayment? {
        val config = aiPreferences.config.first()
        if (!config.isConfigured) return null

        val userPrompt = buildString {
            append("判断下面这条来自「")
            append(PaymentPackages.displayName(packageName))
            append("」的系统通知是否是一笔【已完成】的收款或付款，并提取信息。\n\n")
            append("通知标题：").append(title.ifBlank { "（无）" }).append('\n')
            append("通知内容：").append(content).append("\n\n")
            append("严格输出 JSON（不要 markdown 代码块，不要解释）：\n")
            append("{\"is_payment\": true或false, \"type\": \"expense\"或\"income\", ")
            append("\"amount\": \"金额字符串如 35.00\", \"merchant\": \"商户或对方名称，未知为空字符串\", ")
            append("\"pay_channel\": \"实际扣款的支付方式，取值：零钱/零钱通/花呗/余额/余额宝/储蓄卡/信用卡/银行卡，")
            append("银行卡类可带尾号如 银行卡(1234)，未知为空字符串\"}\n")
            append("注意：营销推送、群消息、待付款、支付失败都不算已完成；")
            append("amount 取实际支付金额，不要取账户余额；")
            append("pay_channel 只取实际付款的那一个，文案里的账户余额提示不算。")
        }

        val raw = chat(config, SYSTEM_PROMPT, userPrompt) ?: return null
        val parsed = parseAiPaymentResponse(raw) ?: return null
        // rawText 必须是**真实通知文案**，不能是模型输出的那段 JSON：
        // 备注生成、AI 二次审核、未识别队列展示都读它，塞 JSON 进去等于把备注写成
        // `{"is_payment": true, ...}`。商户名由模型给出，这里只补回原文。
        return parsed.copy(
            rawText = listOf(title, content)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(NOTIFICATION_TEXT_LIMIT),
        )
    }

    /**
     * 让大模型从用户的分类列表里挑一个分类。
     *
     * @param candidates 已按收支类型过滤好的候选分类
     * @return 分类名称（调用方按名称匹配 id）；模型认为都不合适时返回 null
     */
    suspend fun classifyCategory(
        merchant: String,
        rawText: String,
        type: TransactionType,
        candidates: List<Category>,
    ): String? {
        if (candidates.isEmpty()) return null
        val config = aiPreferences.config.first()
        if (!config.isConfigured) return null

        val userPrompt = buildString {
            append("这是一笔已记录的").append(if (type == TransactionType.INCOME) "收入" else "支出")
            append("，商户/备注信息：\n")
            append((merchant.ifBlank { rawText }).take(120)).append("\n\n")
            append("用户的可选分类：")
            append(candidates.joinToString("、") { it.name }).append("\n\n")
            append("严格输出 JSON（不要 markdown 代码块，不要解释）：")
            append("{\"category\": \"最合适的分类名\"}")
            append("；若确实都不合适，输出 {\"category\": null}。")
        }

        val raw = chat(config, SYSTEM_PROMPT, userPrompt) ?: return null
        val json = extractJsonObject(raw) ?: return null
        return runCatching {
            val obj = JSONObject(json)
            if (obj.isNull("category")) null else obj.optString("category").ifBlank { null }
        }.getOrNull()
    }

    /**
     * AI 内容审核：复核本地规则从**整屏读屏文本**里抓到的这一笔，并产出干净备注。
     *
     * ## 为什么需要它
     * 无障碍读屏会把整个活动窗口的文字一次性读出来（用户可能正停在聊天列表、
     * 群聊、商品详情页）。本地规则只能判断「文本里出现了支付语义词 + 金额」，
     * 因此存在两个它解决不了的问题：
     * 1. **误判**：聊天记录里提到"已支付¥2.00"会被当成一笔新支付；
     * 2. **备注脏**：商户名提不出来时，备注只能退回整段读屏文本，
     *    用户看到的备注是一堆与自己无关的窗口内容。
     *
     * 审核步骤把这两件事一起交给模型：判定是否确为一笔**已完成**的收付款、
     * 提取对方的简短名称、并给出只描述这一笔的短备注。
     *
     * ## 失败即放行（重要取舍）
     * 未配置 / 网络失败 / 输出不成 JSON 时返回 null，调用方**按原样继续记账**。
     * 绝不能因为审核环节不可用就不记账——少记一笔是静默的，比多一条待确认的记录更糟。
     *
     * @param source      来源描述（"无障碍" / "通知" / "AI通知"），用于提示词与日志
     * @param packageName 支付来源包名
     * @param text        读屏 / 通知原始文本（超长会被截断后再送模型）
     * @param amountCents 本地规则解析出的金额（分），供模型对照
     * @param type        本地规则解析出的收支方向
     */
    suspend fun review(
        source: String,
        packageName: String,
        text: String,
        amountCents: Long,
        type: TransactionType,
    ): AiReview? {
        val config = aiPreferences.config.first()
        if (!config.isConfigured) return null

        val userPrompt = buildString {
            append("你在审核一个记账 App 的支付抓取结果。抓取文本可能是**整屏文字**，")
            append("里面混有聊天、商品页、系统提示等无关内容。\n\n")
            append("来源：").append(source)
            append("（").append(PaymentPackages.displayName(packageName)).append("）\n")
            append("本地规则判定：").append(if (type == TransactionType.INCOME) "收入" else "支出")
            append(" ¥").append(BigDecimal.valueOf(amountCents).movePointLeft(2).toPlainString()).append('\n')
            append("抓取文本：").append(text.take(REVIEW_TEXT_LIMIT)).append("\n\n")
            append("请审核三件事：\n")
            append("1) 这段文本是否确实描述了一笔【已完成】的收付款？")
            append("（营销推送、聊天记录、账单/余额汇总、待付款、支付失败、退款申请中 → false）\n")
            append("2) 提取这一笔对应的商户 / 对方名称；文中没有就返回空字符串。\n")
            append("3) 给一句不超过 12 字的简短备注，只描述这一笔支付，")
            append("**不要**把屏幕上的其他内容写进去。\n\n")
            append("严格输出 JSON（不要 markdown 代码块，不要解释）：")
            append("{\"is_payment\": true或false, \"merchant\": \"\", \"note\": \"\"}")
        }

        val raw = chat(config, REVIEW_SYSTEM_PROMPT, userPrompt) ?: return null
        return parseAiReview(raw)
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------
    /** 发起一次对话补全，返回模型输出的文本；失败返回 null */
    private suspend fun chat(config: AiConfig, system: String, user: String): String? =
        withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("model", config.model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)),
                )
                .put("temperature", 0.1)
                .put("max_tokens", 200)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(config.baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "AI 接口返回 ${response.code}: ${response.message}")
                    return@use null
                }
                val payload = response.body?.string() ?: return@use null
                JSONObject(payload)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "JiYiBiAi"

        private const val SYSTEM_PROMPT =
            "你是一个记账应用的通知解析引擎。只输出 JSON，不要输出任何其他文字。"

        /** 审核任务的系统提示：强调"只输出 JSON"与"别把无关内容写进备注" */
        private const val REVIEW_SYSTEM_PROMPT =
            "你是一个记账应用的审核引擎，负责复核从手机屏幕/通知里抓到的支付线索。" +
                "只输出 JSON，不要输出任何其他文字。"

        /** 送审文本长度上限：整屏读屏可能很长，截断以控制 token 与延迟 */
        private const val REVIEW_TEXT_LIMIT = 400

        /** AI 解析路径保留的通知原文长度上限 */
        private const val NOTIFICATION_TEXT_LIMIT = 200

        /**
         * 从模型输出中抠出 JSON 对象文本。
         * 模型偶尔会带 markdown 代码块或前后缀解释，取第一个 `{` 到最后一个 `}`。
         */
        fun extractJsonObject(raw: String): String? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return raw.substring(start, end + 1)
        }

        /**
         * 解析「内容审核」任务的模型输出。
         *
         * 与 [parseAiPaymentResponse] 的区别：审核任务允许 `is_payment=false`（否决），
         * 因此这里必须把 false 也解析出来交给调用方，而不是当成解析失败返回 null。
         * 只有「连 is_payment 字段都没有」时才返回 null（视为审核不可用 → 调用方按原样记账）。
         */
        fun parseAiReview(raw: String): AiReview? {
            val json = extractJsonObject(raw) ?: return null
            return runCatching {
                val obj = JSONObject(json)
                if (!obj.has("is_payment")) return@runCatching null
                AiReview(
                    isPayment = obj.optBoolean("is_payment", true),
                    merchant = obj.optString("merchant").trim().take(MERCHANT_LIMIT),
                    note = obj.optString("note").trim().take(NOTE_LIMIT),
                )
            }.getOrNull()
        }

        /** 模型给出的商户名长度上限 */
        private const val MERCHANT_LIMIT = 20

        /** 模型给出的备注长度上限：提示词要求 12 字，留点余量 */
        private const val NOTE_LIMIT = 24

        /**
         * 解析「通知识别」任务的模型输出为可落库结果。
         * 非支付 / 字段缺失 / 金额非法一律返回 null。
         */
        fun parseAiPaymentResponse(raw: String): ParsedPayment? {
            val json = extractJsonObject(raw) ?: return null
            return runCatching {
                val obj = JSONObject(json)
                if (!obj.optBoolean("is_payment", false)) return@runCatching null

                val type = when (obj.optString("type")) {
                    "expense" -> TransactionType.EXPENSE
                    "income" -> TransactionType.INCOME
                    else -> return@runCatching null
                }
                val cents = BigDecimal(obj.optString("amount"))
                    .movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .toLong()
                if (cents <= 0L) return@runCatching null

                val payChannel = obj.optString("pay_channel").trim().ifBlank { null }
                ParsedPayment(
                    amountCents = cents,
                    type = type,
                    merchant = obj.optString("merchant").trim(),
                    rawText = raw.trim().take(120),
                    matchedRule = "AI",
                    payChannel = payChannel,
                    // 模型可能返回「银行卡(1234)」这类复合串，尾号从串里再抠一次
                    cardTail = payChannel?.let { PaymentNotificationParser.extractCardTail(it) },
                )
            }.getOrNull()
        }
    }
}
