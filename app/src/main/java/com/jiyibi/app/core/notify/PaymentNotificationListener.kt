package com.jiyibi.app.core.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jiyibi.app.core.data.repository.AccountPreferencesRepository
import com.jiyibi.app.core.data.repository.AutoRecordPreferencesRepository
import com.jiyibi.app.core.data.repository.MerchantCategoryRepository
import com.jiyibi.app.core.domain.model.CategoryKind
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.CategoryRepository
import com.jiyibi.app.core.domain.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 支付通知监听服务：把微信 / 支付宝的支付推送自动记成账。
 *
 * ## 工作方式
 * 1. 系统在用户授权「通知使用权」后，会把所有通知回调到 [onNotificationPosted]；
 * 2. 先按**包名白名单**过滤（[PaymentPackages.WATCHED]），非白名单直接返回；
 * 3. 读取完整文案（BigText / TextLines 优先，见 [readText]）后交给 [PaymentNotificationParser]；
 * 4. 命中的按「默认账户 + 猜测分类」写入交易表，并同步调整账户余额；
 * 5. 未命中的微信 / 支付宝通知把原始文案打到 Logcat，便于按真机文案补规则。
 *
 * ## 调规则的方法
 * ```
 * adb logcat -s JiYiBiNotify
 * ```
 * 看到 `未命中 [...]` 的行，把其中的文案片段补进
 * [PaymentNotificationParser] 的 `RULES` 或 `IGNORE_KEYWORDS` 即可。
 *
 * ## 刻意不做的事
 * - **不静默吞掉解析失败的通知**：一律打日志，宁可多一行日志也不要用户莫名其妙少账。
 * - **不读验证码**：Android 15 起含 OTP 的通知对不受信任的监听服务会被系统屏蔽，
 *   且本功能也无需该能力。
 */
@AndroidEntryPoint
class PaymentNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var accountPreferences: AccountPreferencesRepository

    @Inject
    lateinit var autoRecordPreferences: AutoRecordPreferencesRepository

    /** 「商户 → 分类」学习表：用户手工纠正过的分类优先于静态关键词表 */
    @Inject
    lateinit var merchantCategoryRepository: MerchantCategoryRepository

    /**
     * 服务作用域。
     *
     * `onNotificationPosted` 是系统回调，不能在里面直接做 IO，
     * 因此自己起一个跟随服务生命周期的协程作用域。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "通知监听已连接，支付通知自动记账生效")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "通知监听已断开（被系统回收，或用户撤销了通知使用权）")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: return

        // 只关心白名单里的支付 App，其他通知一概不看
        if (packageName !in PaymentPackages.WATCHED) return

        val read = readText(notification)
        val parsed = PaymentNotificationParser.parse(packageName, read.title, read.content)
        if (parsed == null) {
            // 未命中：留下原始文案供调规则，不要静默丢弃
            Log.d(
                TAG,
                "未命中 [${PaymentPackages.displayName(packageName)}] " +
                    "title=「${read.title}」 content=「${read.content}」",
            )
            return
        }

        scope.launch { record(sbn, parsed) }
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

    /** 落库：去重 → 选账户 → 猜分类 → 写交易 → 同步余额 → 入复核队列 */
    private suspend fun record(sbn: StatusBarNotification, parsed: ParsedPayment) {
        // 用户可能在「自动记账」页关掉了开关
        if (!autoRecordPreferences.enabled.first()) {
            Log.d(TAG, "自动记账已关闭，跳过")
            return
        }

        val occurredAt = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()

        // 去重：同一条通知可能被重复投递（通知被更新或重发），金额与时间都相同
        val duplicated = transactionRepository.hasSameAmountInWindow(
            amount = parsed.amountCents,
            from = occurredAt - DUPLICATE_WINDOW_MILLIS,
            to = occurredAt + DUPLICATE_WINDOW_MILLIS,
        )
        if (duplicated) {
            Log.d(TAG, "跳过重复通知：${parsed.matchedRule} ${formatYuan(parsed.amountCents)}")
            return
        }

        val accountId = resolveAccountId(parsed.type)
        if (accountId == null) {
            Log.w(TAG, "没有可用账户，跳过自动记账")
            return
        }

        val transaction = Transaction(
            id = 0L,
            type = parsed.type,
            amount = parsed.amountCents,
            accountId = accountId,
            toAccountId = null,
            categoryId = resolveCategoryId(parsed),
            // 有商户名就用商户名，否则退化为原始文案（截断，避免备注过长）
            note = parsed.merchant.ifBlank { parsed.rawText }.take(NOTE_MAX_LENGTH),
            tags = emptyList(),
            date = occurredAt,
        )

        val newId = transactionRepository.upsert(transaction)

        // 与手动记账保持一致：同步调整账户余额，否则账户余额会与流水脱节。
        // 手动路径见 TransactionEditViewModel.applyAccountEffect。
        val delta = if (parsed.type == TransactionType.EXPENSE) {
            -parsed.amountCents
        } else {
            parsed.amountCents
        }
        accountRepository.adjustBalance(accountId, delta)

        // 记入复核队列，供「自动记账」页回溯与撤销
        autoRecordPreferences.addRecentId(newId)

        Log.i(
            TAG,
            "已自动记账 [${parsed.matchedRule}] ${formatYuan(parsed.amountCents)} " +
                "note=「${transaction.note}」 id=$newId",
        )
    }

    /**
     * 选择记账账户：优先用「我的 → 默认账户」里配置的默认支出/收入账户，
     * 该账户不存在时回退到账户列表第一个。
     */
    private suspend fun resolveAccountId(type: TransactionType): Long? {
        val accounts = accountRepository.observeAll().first()
        if (accounts.isEmpty()) return null

        val preferred = if (type == TransactionType.EXPENSE) {
            accountPreferences.defaultExpenseAccountId.first()
        } else {
            accountPreferences.defaultIncomeAccountId.first()
        }
        return preferred?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.first().id
    }

    /**
     * 猜测分类 id。三级策略，优先级从高到低：
     *
     * 1. **学习表命中**：用户之前纠正过这个商户 → 直接用用户纠正过的分类（含「明确未分类」墓碑）；
     * 2. **静态关键词表**：[PaymentNotificationParser.guessCategoryName] 按商户关键词猜；
     * 3. 都猜不到 → 返回 null（按「未分类」记录），**不硬塞默认分类**——
     *    把餐饮记成交通比留空更难纠正。
     *
     * 学习表优先于静态表，是因为静态表存在固有缺陷：表格顺序决定优先级
     * （「美团打车」会被「餐饮」组的 `美团` 先命中），且返回分类名，用户改名后即失效。
     * 用户的每一次手工纠正都比内置表更权威。
     */
    private suspend fun resolveCategoryId(parsed: ParsedPayment): Long? {
        val merchant = parsed.merchant.ifBlank { parsed.rawText }
        val kind = if (parsed.type == TransactionType.INCOME) {
            CategoryKind.INCOME
        } else {
            CategoryKind.EXPENSE
        }
        val allCategories = categoryRepository.observeAll().first()

        // 1. 学习表：只在商户名非空时查询（rawText 是全句，做 key 没有泛化价值）
        if (parsed.merchant.isNotBlank()) {
            when (val learned = merchantCategoryRepository.lookup(parsed.merchant)) {
                is MerchantCategoryRepository.LookupResult.Matched -> {
                    // 顺带校验：分类可能已被删除，或用户把它改成了相反的收支类型
                    val hit = allCategories.firstOrNull {
                        it.id == learned.categoryId && it.kind == kind
                    }
                    if (hit != null) return hit.id
                    // 悬空 id：顺手清理，避免每次都要走一遍这个分支
                    merchantCategoryRepository.forgetCategory(learned.categoryId)
                }

                MerchantCategoryRepository.LookupResult.ExplicitlyUncategorized -> {
                    // 用户曾明确要求这个商户记为「未分类」，尊重之，不再走静态表
                    return null
                }

                MerchantCategoryRepository.LookupResult.Unknown -> Unit
            }
        }

        // 2. 静态关键词表兜底
        val name = PaymentNotificationParser.guessCategoryName(
            text = merchant,
            type = parsed.type,
        ) ?: return null

        return allCategories.firstOrNull { it.kind == kind && it.name == name }?.id
    }

    /** 分 → 「¥12.34」形式，仅用于日志 */
    private fun formatYuan(cents: Long): String = "¥%.2f".format(cents / 100.0)

    companion object {
        /** Logcat 标签：`adb logcat -s JiYiBiNotify` */
        const val TAG = "JiYiBiNotify"

        /** 去重时间窗：同金额在此窗口内视为同一条通知的重复投递 */
        private const val DUPLICATE_WINDOW_MILLIS = 5_000L

        /** 备注最大长度，避免把整段营销文案写进备注 */
        private const val NOTE_MAX_LENGTH = 60
    }
}
