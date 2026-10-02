package com.jiyibi.app.core.notify

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
/**
 * 支付窗口类名分类：决定无障碍服务对「支付放行窗口」的开 / 关 / 保持策略。
 *
 * 真机观测（Redmi / 微信 8.x，2026-10）：扫个人收款码付款的完整链路是
 * `RemittanceBusiUI`（金额输入）→ `dialog.k2`（密码弹窗，混淆名）→
 * `dialog.u3`（确认弹窗）→ `UIPageFragmentActivity`（支付成功页，通用容器）。
 * 后三者的类名**都不含支付语义**——若「未命中白名单即清零放行窗口」，
 * 密码弹窗一出现链路就被掐断，成功页永远读不到。因此分类而不是二值判断：
 *
 * - [Kind.PAYMENT] 命中支付片段 → 刷新放行窗口并立即读屏；
 * - [Kind.UNRELATED] 明确无关页（聊天主页 / 扫一扫取景等）→ 立即关闭放行窗口，
 *   防止用户回到聊天列表后，窗口期内的内容变化把聊天文案误记成账；
 * - [Kind.NEUTRAL] 弹窗 / 通用容器 / 混淆类名 → 不关窗口；窗口期内顺带读屏
 *   （支付成功页正是 `UIPageFragmentActivity` 这类容器）。
 *
 * 只看 simpleName（最后一个 `.` 之后），并剔除 "Alipay" 品牌词：
 * 支付宝包名 `com.eg.android.AlipayGphone` 与主页 `AlipayLogin` 本身就含 "pay"，
 * 若带包名匹配，支付宝**所有**页面都会命中白名单，窗口筛选形同虚设。
 */
internal object PaymentWindowHints {

    /** 支付页 / 收银台类名片段 */
    private val PAYMENT_HINTS = listOf(
        "pay", "trans", "cashier", "msp", "checkstand", "collect",
        "webview", "flybird", "wallet", "remit",
    )

    /** 与支付明确无关的页面：出现即关闭放行窗口（聊天主页、扫一扫取景、相册等） */
    private val UNRELATED_HINTS = listOf(
        "Launcher", "BaseScanUI", "ChatUI", "GalleryUI", "Login",
    )

    /** 窗口分类 */
    enum class Kind { PAYMENT, UNRELATED, NEUTRAL }

    fun classify(className: String): Kind {
        if (className.isBlank()) return Kind.NEUTRAL
        val normalized = className
            .substringAfterLast('.')
            .replace("alipay", "", ignoreCase = true)
        return when {
            UNRELATED_HINTS.any { normalized.contains(it, ignoreCase = true) } -> Kind.UNRELATED
            PAYMENT_HINTS.any { normalized.contains(it, ignoreCase = true) } -> Kind.PAYMENT
            else -> Kind.NEUTRAL
        }
    }
}

@AndroidEntryPoint
class PaymentAccessibilityService : AccessibilityService() {

    /** 统一落库器：与通知监听共用同一段记账流程与去重 */
    @Inject
    lateinit var recorder: PaymentRecorder

    /** 主线程 Handler：窗口切换事件到达时 rootInActiveWindow 可能还是旧窗口，延迟读屏等它换血 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 待执行的延迟读屏任务，服务销毁时清理 */
    private var pendingRead: Runnable? = null

    /** 上一次处理的窗口文本指纹，用于内容变化事件节流 */
    private var lastSignature: String? = null
    private var lastSignatureAt = 0L

    /** 上一次由内容变化事件触发读屏的时刻：限频输入金额时的按键风暴 */
    private var lastContentReadAt = 0L

    /** 疑似支付窗口的生效截止时间：窗口切换命中后，短暂放行内容变化事件 */
    private var payWindowActiveUntil = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 与通知监听的「已连接」日志对齐：诊断时能直接确认服务是否真的起来了
        Log.i(PaymentRecorder.TAG, "无障碍服务已连接，支付页面识别生效")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        pendingRead?.let(mainHandler::removeCallbacks)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in PaymentPackages.WATCHED) return
        val now = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口切换：className 是 Activity/Dialog 类名，按三态分类决定放行窗口策略
                val cls = event.className?.toString().orEmpty()
                // 观测日志（I 级）：真机窗口类名是补规则表的唯一可靠依据，
                // 用户支付一笔后 adb logcat -s JiYiBiNotify 即可看到完整窗口链路
                Log.i(PaymentRecorder.TAG, "窗口切换 [$packageName] $cls")
                when (PaymentWindowHints.classify(cls)) {
                    PaymentWindowHints.Kind.PAYMENT -> {
                        // 命中支付窗口：刷新放行时间窗，覆盖"同窗口内弹支付成功层"的情形
                        payWindowActiveUntil = now + PAY_WINDOW_ACTIVE_MILLIS
                        scheduleScreenRead(packageName)
                    }

                    PaymentWindowHints.Kind.UNRELATED ->
                        // 明确无关页（聊天主页等）：关闭放行窗口，防止窗口期内的
                        // 聊天内容变化把"已转账给XX"之类的消息文案误记成账
                        payWindowActiveUntil = 0L

                    PaymentWindowHints.Kind.NEUTRAL ->
                        // 弹窗 / 通用容器 / 混淆类名（dialog.k2、UIPageFragmentActivity）：
                        // 支付流程常经过——不清零；窗口期内读一次屏，支付成功页正是这类容器
                        if (now < payWindowActiveUntil) {
                            scheduleScreenRead(packageName)
                        }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 内容变化：className 是 View 类名（不含支付信息），
                // 只在刚命中支付窗口的时间窗内处理；加最小读屏间隔——
                // 输入金额时每个按键都触发一次内容变化，连读既费电也无增益
                if (now < payWindowActiveUntil && now - lastContentReadAt >= MIN_READ_INTERVAL_MILLIS) {
                    lastContentReadAt = now
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

    /**
     * 延迟读屏。窗口切换事件到达的瞬间，[rootInActiveWindow] 往往还指着
     * 上一个窗口（系统侧的竞态），立刻读只能拿到旧页面的文字。
     * 延迟半个交互帧再读，root 已换成新窗口。
     */
    private fun scheduleScreenRead(packageName: String) {
        pendingRead?.let(mainHandler::removeCallbacks)
        pendingRead = Runnable {
            handleScreen(packageName, System.currentTimeMillis())
        }.also { mainHandler.postDelayed(it, READ_DELAY_MILLIS) }
    }

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

        // 标准规则先行；未命中再试极简成功页兜底——本方法只在支付放行窗口内被调用，
        // 上下文已确认用户处于支付流程，可接受"无方向词"的成功页形态
        val parsed = PaymentNotificationParser.parse(
            packageName = packageName,
            title = "",
            content = text,
        ) ?: PaymentNotificationParser.parseMinimalSuccess(packageName, text)
        ?: run {
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

        /** 窗口切换后的读屏延迟：等 rootInActiveWindow 完成新旧窗口切换 */
        private const val READ_DELAY_MILLIS = 600L

        /** 内容变化事件的最小读屏间隔：输入金额时每个按键一次事件，无需逐一响应 */
        private const val MIN_READ_INTERVAL_MILLIS = 500L
    }
}
