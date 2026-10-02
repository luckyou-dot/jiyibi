package com.jiyibi.app.core.notify

import android.content.Context
import android.util.Log
import com.jiyibi.app.core.ai.AiPaymentParser
import com.jiyibi.app.core.data.repository.AccountPreferencesRepository
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.data.repository.AutoRecordPreferencesRepository
import com.jiyibi.app.core.data.repository.MerchantCategoryRepository
import com.jiyibi.app.core.domain.model.CategoryKind
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.repository.AccountRepository
import com.jiyibi.app.core.domain.repository.CategoryRepository
import com.jiyibi.app.core.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 支付事件统一落库器。
 *
 * 「通知监听」与「无障碍支付页面识别」两条来源最终都要走同一段流程：
 * 开关检查 → 去重 → 选账户 → 猜分类 → 写交易 → 同步余额 → 入复核队列 → 弹横幅提醒。
 * 收敛在这里，保证两条路径的记账行为（余额、提醒、复核）完全一致。
 */
@Singleton
class PaymentRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val accountPreferences: AccountPreferencesRepository,
    private val autoRecordPreferences: AutoRecordPreferencesRepository,
    private val merchantCategoryRepository: MerchantCategoryRepository,
    private val aiParser: AiPaymentParser,
) {

    /** 跟随进程生命周期的服务作用域（本类是单例，无需手动取消） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 串行化「判重 → 写库」。
     *
     * 两条来源会在同一次支付后几百毫秒内先后到达，判重（查库）与落库（插库）之间必须原子，
     * 否则两个并发协程会在对方插入之前双双通过判重，同一笔钱记两次。
     */
    private val recordMutex = Mutex()

    /**
     * 已处理事件指纹 → 处理时刻。只在进程存活期内拦截「同一事件被重复投递」。
     *
     * 为什么不查库判重就够了？因为查库判重只能按「金额 + 时间窗 + 类型」猜，
     * 而 `sbn.key` 是系统给一条通知的稳定标识：通知被更新、被重投、被系统重建时
     * key 不变，命中即确定是同一件事，比时间窗判重可靠且不会误伤真实的重复交易。
     * 所有访问都在 [recordMutex] 内，无需额外同步。
     */
    private val handledEvents = LinkedHashMap<String, Long>()

    /**
     * 异步落库入口。调用方（两个系统服务）都在主线程回调里，不能做 IO。
     *
     * @param source     来源标记，仅用于日志（"通知" / "AI通知" / "无障碍"）
     * @param packageName 支付来源包名（微信 / 支付宝）
     * @param occurredAt 支付发生时间：通知用 postTime，无障碍用事件时间
     * @param eventId    事件指纹，通知链路传 `sbn.key`；无障碍链路没有稳定标识，留 null
     */
    fun recordAsync(
        source: String,
        packageName: String,
        occurredAt: Long,
        parsed: ParsedPayment,
        eventId: String? = null,
    ) {
        scope.launch {
            runCatching { record(source, packageName, occurredAt, parsed, eventId) }
                .onFailure { Log.w(TAG, "自动记账落库失败（$source）: ${it.message}") }
        }
    }

    /**
     * 落库主流程，按锁的粒度分三段：
     *
     * - **阶段一（锁内）**：开关、指纹与金额两层去重、选账户——全是本地快操作，
     *   「查库判重 → 插库」必须与其它支付事件串行，否则两条通道会在对方插入前
     *   双双通过判重，同一笔记两次；
     * - **阶段二（锁外）**：猜分类。静态关键词猜不到时会走 AI 网络调用
     *   （读超时上限 20s），绝不能压在互斥锁里，否则一笔待分类的账会
     *   阻塞其后所有支付的入账与横幅提醒；
     * - **阶段三（锁内）**：复查判重后落库——分类期间另一通道可能已把同一笔记上。
     */
    private suspend fun record(
        source: String,
        packageName: String,
        occurredAt: Long,
        parsed: ParsedPayment,
        eventId: String?,
    ) {
        // 用户可能在「自动记账」页关掉了开关，两条来源都要尊重
        if (!autoRecordPreferences.enabled.first()) {
            Log.d(TAG, "自动记账已关闭，跳过（$source）")
            return
        }

        val from = occurredAt - DUPLICATE_WINDOW_MILLIS
        val to = occurredAt + DUPLICATE_WINDOW_MILLIS

        val account: Account
        recordMutex.withLock {
            // 去重第一层：事件指纹。同一条通知被系统重投 / 更新时 key 相同，必定命中。
            if (!claimEvent(eventId, System.currentTimeMillis())) {
                Log.d(TAG, "跳过重复投递的通知（$source）：key=$eventId")
                return
            }

            // 去重第二层：跨通道金额判重（金额 + 类型 + 时间窗）。无障碍读屏没有通知 key，
            // 「无障碍读到支付页 + 随后通知到达」的双通道重复只能靠这一层兜住。
            if (transactionRepository.hasSameAmountInWindow(parsed.amountCents, parsed.type.name, from, to)) {
                Log.d(TAG, "跳过重复支付事件（$source）：${parsed.matchedRule} ${formatYuan(parsed.amountCents)}")
                return
            }

            account = resolveAccount(parsed) ?: run {
                Log.w(TAG, "没有可用账户，跳过自动记账")
                return
            }
        }

        // 阶段二：分类猜测（可能含 AI 网络调用），锁外执行
        val category = resolveCategory(parsed)

        recordMutex.withLock {
            // 复查：分类期间另一通道可能已把同一笔记录上
            if (transactionRepository.hasSameAmountInWindow(parsed.amountCents, parsed.type.name, from, to)) {
                Log.d(TAG, "跳过重复支付事件（$source，分类期间已被抢先记录）")
                return
            }

            val transaction = Transaction(
                id = 0L,
                type = parsed.type,
                amount = parsed.amountCents,
                accountId = account.id,
                toAccountId = null,
                categoryId = category.id,
                // 有商户名就用商户名；否则用清洗后的文案——读屏文本混有状态栏时间、
                // 系统通知等噪声，先剔除再入备注，避免"乱七八糟"
                note = parsed.merchant.ifBlank {
                    PaymentNotificationParser.sanitizeForNote(parsed.rawText)
                }.take(NOTE_MAX_LENGTH),
                tags = emptyList(),
                date = occurredAt,
            )

            try {
                val newId = transactionRepository.upsert(transaction)

                // 与手动记账保持一致：同步调整账户余额，否则账户余额会与流水脱节。
                // 手动路径见 TransactionEditViewModel.applyAccountEffect。
                val delta = if (parsed.type == TransactionType.EXPENSE) {
                    -parsed.amountCents
                } else {
                    parsed.amountCents
                }
                accountRepository.adjustBalance(account.id, delta)

                // 记入复核队列，供「自动记账」页回溯与撤销
                autoRecordPreferences.addRecentId(newId)

                // 入库后弹横幅提醒，点击直达编辑页；未授权通知权限时静默跳过
                runCatching {
                    AutoRecordNotifier.notifyRecorded(
                        context = context,
                        transactionId = newId,
                        type = parsed.type,
                        amountCents = parsed.amountCents,
                        note = transaction.note,
                        categoryName = category.displayName,
                        accountName = account.name,
                        sourcePackage = packageName,
                    )
                }

                Log.i(
                    TAG,
                    "已自动记账 [$source/${parsed.matchedRule}] ${formatYuan(parsed.amountCents)} " +
                        "note=「${transaction.note}」 id=$newId",
                )
            } catch (t: Throwable) {
                // 落库失败要把指纹还回去：否则这条通知稍后被系统重投时
                // 会被指纹表当成重复而静默丢账
                eventId?.let { handledEvents.remove(it) }
                throw t
            }
        }
    }

    /**
     * 选择记账账户，按置信度从高到低：
     *
     * 1. **支付方式匹配**：文案里的「零钱 / 花呗 / 储蓄卡(尾号)」等支付方式
     *    （见 [PayMethodAccountMatcher]）比"默认账户"更贴近资金真实出处——
     *    否则用花呗付款会错扣现金余额；
     * 2. **默认账户**：「我的 → 默认账户」配置的默认支出/收入账户；
     * 3. 以上都落空 → 账户列表第一个。
     */
    private suspend fun resolveAccount(parsed: ParsedPayment): Account? {
        // 已归档的账户不再承接自动记账
        val accounts = accountRepository.observeAll().first().filter { !it.archived }
        if (accounts.isEmpty()) return null

        PayMethodAccountMatcher.match(parsed.payChannel, parsed.cardTail, accounts)?.let { return it }

        val preferred = if (parsed.type == TransactionType.EXPENSE) {
            accountPreferences.defaultExpenseAccountId.first()
        } else {
            accountPreferences.defaultIncomeAccountId.first()
        }
        return accounts.firstOrNull { it.id == preferred } ?: accounts.first()
    }

    /**
     * 猜测分类。三级策略，优先级从高到低：
     *
     * 1. **学习表命中**：用户之前纠正过这个商户 → 直接用用户纠正过的分类（含「明确未分类」墓碑）；
     * 2. **静态关键词表**：[PaymentNotificationParser.guessCategoryName] 按商户关键词猜；
     * 3. 都猜不到 → 返回 null（按「未分类」记录），**不硬塞默认分类**——
     *    把餐饮记成交通比留空更难纠正。
     *
     * @return [ResolvedCategory.id] 写入交易（null = 未分类）；[ResolvedCategory.displayName]
     *         仅用于提醒通知展示
     */
    private suspend fun resolveCategory(parsed: ParsedPayment): ResolvedCategory {
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
                    if (hit != null) return ResolvedCategory(hit.id, hit.name)
                    // 悬空 id：顺手清理。维护动作不能连累记账——抛异常就吞掉
                    runCatching { merchantCategoryRepository.forgetCategory(learned.categoryId) }
                }

                MerchantCategoryRepository.LookupResult.ExplicitlyUncategorized -> {
                    // 用户曾明确要求这个商户记为「未分类」，尊重之，不再走静态表
                    return ResolvedCategory(null, null)
                }

                MerchantCategoryRepository.LookupResult.Unknown -> Unit
            }
        }

        // 2. 静态关键词表兜底；猜不到再问 AI（配置了 Key 才会发起请求）
        val name = PaymentNotificationParser.guessCategoryName(
            text = merchant,
            type = parsed.type,
        )
        if (name != null) {
            return allCategories
                .firstOrNull { it.kind == kind && it.name == name }
                ?.let { ResolvedCategory(it.id, it.name) }
                ?: ResolvedCategory(null, null)
        }

        // 3. AI 分类：把用户自己的分类列表给模型挑一个；都不合适则保持未分类
        if (aiParser.isConfigured()) {
            val aiName = runCatching {
                aiParser.classifyCategory(
                    merchant = parsed.merchant,
                    rawText = parsed.rawText,
                    type = parsed.type,
                    candidates = allCategories.filter { it.kind == kind },
                )
            }.getOrNull()
            if (aiName != null) {
                allCategories
                    .firstOrNull { it.kind == kind && it.name == aiName }
                    ?.let { return ResolvedCategory(it.id, it.name) }
            }
        }

        return ResolvedCategory(null, null)
    }

    /** 分类解析结果：id 写库（null = 未分类），displayName 供提醒通知展示 */
    private data class ResolvedCategory(val id: Long?, val displayName: String?)

    /**
     * 登记并判断事件指纹是否首次出现。
     *
     * @return true = 首次出现，可继续落库；false = TTL 内已处理过，应丢弃。
     *         [eventId] 为 null（无障碍链路）时一律放行，交给第二层金额判重。
     */
    private fun claimEvent(eventId: String?, now: Long): Boolean {
        if (eventId == null) return true
        val previous = handledEvents[eventId]
        if (previous != null && now - previous < EVENT_TTL_MILLIS) return false
        handledEvents[eventId] = now
        // 监听服务常驻，给指纹表兜个容量上限，按最旧优先淘汰
        val excess = handledEvents.size - MAX_HANDLED_EVENTS
        if (excess > 0) {
            handledEvents.entries
                .sortedBy { it.value }
                .take(excess)
                .forEach { handledEvents.remove(it.key) }
        }
        return true
    }

    /** 分 → 「¥12.34」形式，仅用于日志 */
    private fun formatYuan(cents: Long): String = "¥%.2f".format(cents / 100.0)

    companion object {
        /** Logcat 标签：`adb logcat -s JiYiBiNotify` */
        const val TAG = "JiYiBiNotify"

        /** 去重时间窗：同金额同类型在此窗口内视为同一笔支付事件的重复报告。
         *  30s 的量级依据：通知链路比无障碍链路慢 1~10s（推送延迟），
         *  双通道同笔支付都落在这个窗内；而真实连续两笔同额支付（连买两杯）
         *  中间必然隔着完整的一次收付款交互，>30s，不会被误吞。 */
        private const val DUPLICATE_WINDOW_MILLIS = 30_000L

        /**
         * 事件指纹有效期。通知 key 在应用重启、系统重建通知后可能被复用给别的通知，
         * 不能永久记住，否则新通知会被误判成重复而丢账。
         */
        private const val EVENT_TTL_MILLIS = 10 * 60 * 1000L

        /** 指纹表容量上限 */
        private const val MAX_HANDLED_EVENTS = 200

        /** 备注最大长度，避免把整段营销文案写进备注 */
        private const val NOTE_MAX_LENGTH = 60
    }
}
