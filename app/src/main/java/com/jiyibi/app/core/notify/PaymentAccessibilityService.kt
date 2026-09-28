package com.jiyibi.app.core.notify

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 支付页面识别服务（无障碍）。
 *
 * ## 解决什么问题
 * 通知监听抓不到「用户正在微信 / 支付宝里付款」的场景——扫码、转账时支付
 * 过程发生在前台，系统不会推通知。本服务在**支付成功页面出现的那一刻**
 * 读取屏幕上的文字（金额、商户、方向词），交给 [PaymentNotificationParser]
 * 解析、[PaymentRecorder] 落库，与通知路径共用去重，不会重复记账。
 *
 * ## 防误读设计（宁漏勿错）
 * 1. 事件订阅已被配置文件限定为微信 / 支付宝两个包的窗口事件；
 * 2. **只处理疑似支付窗口**：窗口类名需命中支付相关片段（Pay/Trans/Cashier/Msp 等），
 *    或者是命中后的短时间内的内容变化（支付成功的弹层常是同窗口内容变化）；
 * 3. 解析仍走完整规则：没有「支付成功/已转账给」方向词 + 金额一律不记，
 *    翻聊天记录看到旧转账文案时不会误记账；
 * 4. 同一段窗口文本在节流窗内只处理一次，避免内容变化事件风暴。
 *
 * ## 漏记的已知情形（接受）
 * 小程序内支付（AppBrandUI）与自定义控件渲染金额的页面不在支付窗口白名单内——
 * 宁可漏一笔（可在「自动记账」页手动补），不能把浏览页面误记成账。
 */
@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    /** 统一落库器：与通知监听共用同一段记账流程与去重 */
    @Inject
    lateinit var recorder: PaymentRecorder

    /** 上一次处理的窗口文本指纹，用于内容变化事件节流 */
    private var lastSignature: String? = null
    private var lastSignatureAt = 0L

    /** 疑似支付窗口的生效截止时间：窗口切换命中后，短暂放行内容变化事件 */
    private var payWindowActiveUntil = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in PaymentPackages.WATCHED) return
        val now = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口切换：className 是 Activity/Dialog 类名，用类名片段判定是否支付场景
                val cls = event.className?.toString().orEmpty()
                if (PAYMENT_WINDOW_HINTS.any { cls.contains(it, ignoreCase = true) }) {
                    // 命中支付窗口：放行一小段时间，覆盖"同窗口内弹支付成功层"的情形
                    payWindowActiveUntil = now + PAY_WINDOW_ACTIVE_MILLIS
                    handleScreen(packageName, now)
                } else {
                    payWindowActiveUntil = 0L
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 内容变化：className 是 View 类名（不含支付信息），
                // 只在刚命中支付窗口的时间窗内处理
                if (now < payWindowActiveUntil) {
                    handleScreen(packageName, now)
                }
            }

            else -> return
        }
    }

    override fun onInterrupt() {
        // 无反馈通道（feedbackGeneric），无需处理
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 读取当前屏幕文字并尝试解析记账 */
    private fun handleScreen(packageName: String, now: Long) {
        val root = rootInActiveWindow ?: return
        val text = collectTexts(root)
        if (text.isBlank()) return

        // 同一段文本在节流窗内只处理一次（内容变化事件会反复触发）
        val signature = "$packageName:$text"
        if (signature == lastSignature && now - lastSignatureAt < THROTTLE_MILLIS) return
        lastSignature = signature
        lastSignatureAt = now

        val parsed = PaymentNotificationParser.parse(
            packageName = packageName,
            title = "",
            content = text,
        ) ?: run {
            // 支付窗口内未命中规则：打日志供调规则（adb logcat -s JiYiBiNotify）
            Log.d(PaymentRecorder.TAG, "屏幕未命中 [$packageName] ${text.take(80)}")
            return
        }

        Log.i(
            PaymentRecorder.TAG,
            "支付页面识别命中 [${parsed.matchedRule}] ${parsed.rawText.take(60)}",
        )
        recorder.recordAsync(
            source = "无障碍",
            packageName = packageName,
            occurredAt = now,
            parsed = parsed,
        )
    }

    /** 广度优先收集屏幕上的可见文本，限制节点数防止超大页面拖垮主线程 */
    private fun collectTexts(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            node.text?.let { text ->
                if (text.isNotBlank()) sb.append(text).append(' ')
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return sb.toString().trim()
    }

    companion object {
        /** 窗口文本节流窗口 */
        private const val THROTTLE_MILLIS = 3_000L

        /** 命中支付窗口后放行内容变化事件的时长 */
        private const val PAY_WINDOW_ACTIVE_MILLIS = 30_000L

        /** 收集文本的最大节点数 */
        private const val MAX_NODES = 300

        /**
         * 微信 / 支付宝收银台与支付成功页的常见类名片段。
         * 例如：OfflineScanPayUI（微信扫码收银台）、TransUI（微信转账）、
         * MspContainerActivity（支付宝收银台）、PayeeActivity 等；
         * **扫个人收款码的「向XX付款」整条链路跑在 WebViewUI 里**，
         * 因此 webview 也必须放行——防误记由解析规则（方向词+金额）兜底，
         * 而不是靠窗口白名单。
         */
        private val PAYMENT_WINDOW_HINTS = listOf(
            "pay", "trans", "cashier", "msp", "checkstand", "collect",
            "webview", "flybird", "wallet",
        )
    }
}
