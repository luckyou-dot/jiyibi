package com.jiyibi.app.core.notify

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jiyibi.app.core.ai.AiPaymentParser
import com.jiyibi.app.core.data.repository.UnmatchedNotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 支付通知监听服务：把微信 / 支付宝的支付推送自动记成账。
 *
 * ## 工作方式
 * 1. 系统在用户授权「通知使用权」后，会把所有通知回调到 [onNotificationPosted]；
 * 2. 先按**包名白名单**过滤（[PaymentPackages.WATCHED]），非白名单直接返回；
 * 3. 读取完整文案（BigText / TextLines 优先，见 [readText]）后交给 [PaymentNotificationParser]；
 * 4. 命中的交给 [PaymentRecorder] 落库（选账户、猜分类、写交易、同步余额、弹提醒）；
 * 5. 未命中的微信 / 支付宝通知把原始文案打进「未识别队列」，在 App 内可见，
 *    便于按真机文案补规则。
 *
 * ## 通知方案的天然盲区（为什么还要无障碍服务）
 * 用户**正在微信 / 支付宝里付款**时（扫码、转账），支付过程发生在前台，
 * 系统不会给正在使用的 App 自己推通知——监听器收不到任何事件。
 * 这个场景由 [PaymentAccessibilityService] 读取支付成功页面来补足。
 *
 * ## 刻意不做的事
 * - **不静默吞掉解析失败的通知**：进未识别队列 + 打日志，宁可多一条待办也不要用户莫名其妙少账。
 * - **不读验证码**：Android 15 起含 OTP 的通知对不受信任的监听服务会被系统屏蔽，
 *   且本功能也无需该能力。
 */
@AndroidEntryPoint
class PaymentNotificationListener : NotificationListenerService() {

    /** 统一落库器：与无障碍服务共用同一段记账流程 */
    @Inject
    lateinit var recorder: PaymentRecorder

    /** 未识别通知队列：规则漏掉新句式时，让用户在 App 里看得到，而不是只有 Logcat */
    @Inject
    lateinit var unmatchedNotificationRepository: UnmatchedNotificationRepository

    /** AI 兜底解析：规则未命中时先问一次大模型（未配置则行为与从前一致） */
    @Inject
    lateinit var aiParser: AiPaymentParser

    /**
     * 服务作用域。
     *
     * `onNotificationPosted` 是系统回调，不能在里面直接做 IO，
     * 因此自己起一个跟随服务生命周期的协程作用域。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(PaymentRecorder.TAG, "通知监听已连接，支付通知自动记账生效")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(PaymentRecorder.TAG, "通知监听已断开（被系统回收，或用户撤销了通知使用权），尝试自动重绑")
        // 激进 ROM（省电策略）会回收监听服务且不一定主动重绑，
        // 主动 requestRebind 能显著提高存活率；用户主动撤销授权时系统会忽略此请求。
        requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: return

        // 只关心白名单里的支付 App，其他通知一概不看
        if (packageName !in PaymentPackages.WATCHED) return

        val read = readText(notification)
        val parsed = PaymentNotificationParser.parse(packageName, read.title, read.content)
        if (parsed == null) {
            // 未命中：先试 AI 兜底（配置了 Key 才会真正发起请求），
            // AI 也啃不动再进未识别队列——只打 Logcat 用户根本看不到。
            Log.d(
                PaymentRecorder.TAG,
                "未命中 [${PaymentPackages.displayName(packageName)}] " +
                    "title=「${read.title}」 content=「${read.content}」",
            )
            val occurredAt = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()
            scope.launch {
                val aiParsed = if (aiParser.isConfigured()) {
                    runCatching {
                        aiParser.parsePaymentNotification(packageName, read.title, read.content)
                    }.getOrNull()
                } else {
                    null
                }
                if (aiParsed != null) {
                    Log.i(PaymentRecorder.TAG, "AI 兜底解析成功：${read.content.take(40)}")
                    recorder.recordAsync(
                        source = "AI通知",
                        packageName = packageName,
                        occurredAt = occurredAt,
                        parsed = aiParsed,
                        // 通知 key：同一条通知被系统重投 / 更新时 key 不变，供落库器精确判重
                        eventId = sbn.key,
                    )
                } else {
                    unmatchedNotificationRepository.add(
                        packageName = packageName,
                        title = read.title,
                        content = read.content,
                        postedAt = occurredAt,
                    )
                }
            }
            return
        }

        recorder.recordAsync(
            source = "通知",
            packageName = packageName,
            occurredAt = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis(),
            parsed = parsed,
            eventId = sbn.key,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 一条通知读出的文本 */
    private data class NotificationText(val title: String, val content: String)

    /**
     * 读取通知的完整文案。
     *
     * **关键点**：支付通知基本都是 `BigTextStyle` / `InboxStyle`，
     * 此时 `EXTRA_TEXT` 只是被截断的短句，完整文案（含金额）在
     * `EXTRA_BIG_TEXT` 或 `EXTRA_TEXT_LINES` 里。只读 `EXTRA_TEXT` 会漏金额，
     * 这是接通知记账最常见的坑。
     */
    private fun readText(notification: Notification): NotificationText {
        val extras = notification.extras ?: return NotificationText("", "")
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it?.toString().orEmpty() }
            .orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        // 优先级：BigText > TextLines > Text
        val primary = listOf(bigText, lines, text)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        // subText 常带「已支付」「收款到账」这类方向关键词，补进正文提高规则命中率
        val content = if (subText.isNotBlank() && !primary.contains(subText)) {
            "$subText $primary".trim()
        } else {
            primary
        }
        return NotificationText(title = title, content = content)
    }
}
