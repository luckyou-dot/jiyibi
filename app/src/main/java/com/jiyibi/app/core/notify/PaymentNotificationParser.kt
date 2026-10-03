package com.jiyibi.app.core.notify

import com.jiyibi.app.core.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 已知支付 / 钱包 App 的包名白名单。
 *
 * **识别来源只能靠包名**，不能靠通知标题里出现「微信」「支付宝」等字样——
 * 标题文本任何 App 都能伪造，包名才是系统保证的。
 */
object PaymentPackages {
    /** 微信 */
    const val WECHAT = "com.tencent.mm"

    /** 支付宝 */
    const val ALIPAY = "com.eg.android.AlipayGphone"

    /** 关注的包名集合：只有这些 App 的通知才会进入解析流程 */
    val WATCHED: Set<String> = setOf(WECHAT, ALIPAY)

    /** 包名 → 可读名称，用于备注与日志 */
    fun displayName(packageName: String): String = when (packageName) {
        WECHAT -> "微信"
        ALIPAY -> "支付宝"
        else -> packageName
    }
}

/**
 * 从一条支付通知解析出的结果。
 *
 * @property amountCents  金额（分）
 * @property type         收支方向
 * @property merchant     商户 / 对方名称，解析不到时为空串
 * @property rawText      通知原始文案，写入备注便于人工核对
 * @property matchedRule  命中的规则名，便于调规则时定位
 * @property payChannel   支付方式标签（零钱/零钱通/花呗/余额/余额宝/银行卡/信用卡），
 *                        文案未提及时为 null，供「按支付方式选账户」使用
 * @property cardTail     银行卡尾号（4 位），可精确对上用户自建的银行卡账户
 */
data class ParsedPayment(
    val amountCents: Long,
    val type: TransactionType,
    val merchant: String,
    val rawText: String,
    val matchedRule: String,
    val payChannel: String? = null,
    val cardTail: String? = null,
)

/**
 * 支付通知文案解析器。
 *
 * 微信 / 支付宝的推送文案**不公开、且随版本变化**，因此这里不写死完整句式，
 * 而是采用「黑名单关键词 → 有序规则表 → 通用金额兜底」三段式：
 *
 * 1. [IGNORE_KEYWORDS]：命中即判定为非支付通知，直接丢弃（群消息、营销、公众号等）
 * 2. [RULES]：按顺序匹配，**先判收入再判支出**（否则「收款成功」会被支出规则误吃）
 * 3. 金额：优先取紧邻方向关键词的那个数字，避免「余额 ¥1000，已支付 ¥35」取错
 *
 * 规则不命中时返回 null，由调用方把原始文案打到 Logcat，便于按真机文案补规则。
 */
object PaymentNotificationParser {

    /**
     * 忽略关键词：命中任意一个即认定为非支付通知。
     *
     * 这批词主要来自微信的聊天/群消息、服务号推送，以及支付宝的营销推送，
     * 它们同样从 `com.tencent.mm` / 支付宝包名发出，必须显式排除。
     *
     * **注意**：这里绝不能出现「服务通知」「点击查看」这类词——现代版微信的
     * 支付凭证正是通过「服务通知」会话推送的（通知标题就叫"服务通知"），
     * 曾经把"服务通知"放进黑名单，导致所有微信支付通知在进入规则前就被
     * 整体误杀，自动记账几个月完全不工作。泛化标题改由 [GENERIC_TITLES] 处理。
     */
    private val IGNORE_KEYWORDS = listOf(
        // 微信：聊天与群消息
        "群聊", "邀请你", "拍了拍", "语音通话", "视频通话", "通话中",
        // 微信：服务号 / 营销
        "订阅号", "公众号", "点击领取", "立即参与",
        // 支付宝：营销与理财
        "蚂蚁森林", "能量收取", "芭芭农场", "余额宝收益", "基金", "集五福",
        "理财收益", "体验金", "红包已领取", "消费券",
    )

    /** 泛化标题：这些标题本身不携带任何支付信息（只是消息来源的容器名），
     * 拼进 rawText 只会干扰黑名单判断与商户名提取，因此组装时直接跳过，
     * 只用正文解析。 */
    private val GENERIC_TITLES = setOf(
        "服务通知", "微信支付", "支付助手", "微信", "支付宝", "收款助手", "微信收款助手",
    )

    /**
     * 否定/未完成词：出现即认定支付未完成，一律不记账。
     *
     * "已支付失败"含规则词"已支付"、"退款申请已提交"含收入词"退款"，
     * 子串匹配的规则表自身无法区分完成态——靠这层在进规则前整体拦截。
     */
    private val UNFINISHED_TOKENS = listOf(
        "失败", "已取消", "处理中", "申请", "待入账", "待支付", "预计", "工作日",
    )

    /** 单条匹配规则：命中 [keywords] 任一即适用，收支方向由 [type] 指定 */
    private data class Rule(
        val name: String,
        val packages: Set<String>,
        val keywords: List<String>,
        val type: TransactionType,
    )

    /**
     * 有序规则表：**从上往下第一个命中者生效**。
     *
     * 排序原则：收入规则必须排在支出规则之前，因为两者的关键词可能同时出现在一条文案里
     * （例如「收款成功」里含「成功」），而收入侧的关键词更具体，先判更安全。
     */
    private val RULES: List<Rule> = listOf(
        // ---------- 收入 ----------
        Rule(
            name = "微信-转账收款",
            packages = setOf(PaymentPackages.WECHAT),
            // 「待入账」是未完成状态，已被 UNFINISHED_TOKENS 拦截，不进关键词
            keywords = listOf("已存入零钱", "转账到账", "已收款"),
            type = TransactionType.INCOME,
        ),
        Rule(
            name = "微信-收款到账",
            packages = setOf(PaymentPackages.WECHAT),
            keywords = listOf("收款到账", "收款成功", "成功收款"),
            type = TransactionType.INCOME,
        ),
        Rule(
            name = "微信-收款助手",
            packages = setOf(PaymentPackages.WECHAT),
            // 商家收款通知就是光秃秃的「收款￥88.00」——关键词带上货币符号，
            // 精确匹配金额形态，避免误伤「收款码」「待收款」类文案
            keywords = listOf("收款￥", "收款¥"),
            type = TransactionType.INCOME,
        ),
        Rule(
            name = "支付宝-收款成功",
            packages = setOf(PaymentPackages.ALIPAY),
            keywords = listOf("成功收款", "收款成功", "收款到账", "已收款"),
            type = TransactionType.INCOME,
        ),
        Rule(
            name = "通用-退款",
            packages = PaymentPackages.WATCHED,
            // 只认完成态："您的退款申请已提交"这类过程描述不含以下任何词，
            // 会被 UNFINISHED_TOKENS（申请/预计/工作日）先行拦截
            keywords = listOf("退款成功", "已退还", "退款已到账", "已退回"),
            type = TransactionType.INCOME,
        ),
        // ---------- 支出 ----------
        Rule(
            name = "微信-支付成功",
            packages = setOf(PaymentPackages.WECHAT),
            keywords = listOf("已支付", "支付成功", "已付款", "付款成功", "支付完成"),
            type = TransactionType.EXPENSE,
        ),
        Rule(
            name = "支付宝-付款成功",
            packages = setOf(PaymentPackages.ALIPAY),
            // 「成功付款」与「付款成功」是两种真实存在的词序，都要覆盖
            keywords = listOf(
                "付款成功", "支付成功", "已成功付款", "交易成功", "已支付",
                "成功付款", "新的付款",
            ),
            type = TransactionType.EXPENSE,
        ),
        Rule(
            name = "通用-扣款",
            packages = PaymentPackages.WATCHED,
            keywords = listOf("已扣款", "扣款成功", "自动扣款"),
            type = TransactionType.EXPENSE,
        ),
        Rule(
            name = "通用-转账给",
            packages = PaymentPackages.WATCHED,
            // 付款人视角的转账成功页面：「已转账给张三」「转账成功」。
            // 收款人视角（「转账到账」「已收款」）已被前面的收入规则先命中，不会走到这里
            keywords = listOf("已转账给", "转账成功"),
            type = TransactionType.EXPENSE,
        ),
    )

    /**
     * 紧邻方向关键词的金额，优先级最高。
     *
     * 例：「余额 ¥1000.00，已支付 ¥35.00」——通用 ¥ 正则会先抓到 1000.00（错），
     * 而本正则锚定在「已支付」之后，能正确抓到 35.00。
     */
    private val AMOUNT_NEAR_KEYWORD = Regex(
        "(?:已支付|支付成功|付款成功|已付款|支付完成|已成功付款|交易成功" +
            "|收款到账|收款成功|成功收款|已收款|已存入零钱|已扣款|扣款成功|自动扣款|退款|已退还" +
            "|成功付款|新的付款)" +
            "[^0-9¥￥]{0,8}[¥￥]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)",
    )

    /** 通用金额兜底：¥ 前缀 或 「N 元」 */
    private val AMOUNT_FALLBACK = listOf(
        Regex("[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)"),
        Regex("([0-9]+(?:\\.[0-9]{1,2})?)\\s*元"),
    )

    /** 商户 / 对方名称的尽力提取，命中不到就留空 */
    private val MERCHANT_PATTERNS = listOf(
        Regex("向\\s*(.{2,20}?)\\s*(?:付款|转账|支付)"),
        Regex("在\\s*(.{2,20}?)\\s*(?:消费|支付|付款)"),
        Regex("(?:收款方|商户名称|付款给|收款人)[:：]?\\s*(.{2,20})"),
        // 转账成功页的「转账给XX」：后面常直接跟金额，用前瞻截断避免把 ¥ 金额带进商户名
        Regex("(?:已转账给|转账给)[:：]?\\s*(.{2,12}?)(?=\\s|¥|￥|\\.|$)"),
        Regex("(.{2,20}?)\\s*(?:收款|已收款)"),
    )

    /**
     * 支付方式标签 → 匹配正则，按**具体到泛化**排序（零钱通先于零钱、余额宝先于余额）。
     *
     * 「余额」一条做了双重防护：负向前瞻排除「余额宝」，负向后顾排除
     * 「当前/账户/可用余额」——那是文案里夹带的**账户余额描述**，不是支付方式，
     * 若不排除，「当前余额¥1000，已支付¥35」会被误判成用支付宝余额付款。
     */
    private val PAY_CHANNEL_PATTERNS: List<Pair<String, Regex>> = listOf(
        "零钱通" to Regex("零钱通"),
        "零钱" to Regex("零钱"),
        "花呗" to Regex("花呗"),
        "余额宝" to Regex("余额宝"),
        "余额" to Regex("(?<!当前)(?<!账户)(?<!可用)余额(?!宝)"),
        "信用卡" to Regex("信用卡"),
        "储蓄卡" to Regex("储蓄卡"),
        "银行卡" to Regex("银行卡"),
    )

    /** 银行卡尾号的两种真实形态：「尾号1234」「储蓄卡(1234)」 */
    private val CARD_TAIL_PATTERNS = listOf(
        Regex("尾号\\s*[:：]?\\s*(\\d{4})"),
        Regex("(?:储蓄卡|信用卡|银行卡)\\s*[（(]\\s*(\\d{4})\\s*[）)]"),
    )

    /**
     * 商户关键词 → 分类名（按应用内置的分类名匹配，匹配不到则返回 null 交给用户补分类）。
     *
     * 这里只做「锦上添花」的猜测：命中就预填分类，否则留空（未分类），
     * 不强塞默认分类，避免把账记错。
     */
    private val CATEGORY_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "餐饮" to listOf(
            "餐", "饭", "美团", "饿了么", "肯德基", "麦当劳", "星巴克", "咖啡", "奶茶",
            "食堂", "烧烤", "火锅", "小吃", "外卖", "瑞幸", "蜜雪",
        ),
        "交通" to listOf(
            "滴滴", "地铁", "公交", "高铁", "火车", "机票", "航班", "加油", "停车",
            "打车", "出租车", "共享单车", "哈啰", "青桔", "ETC", "过路费",
        ),
        "购物" to listOf(
            "淘宝", "天猫", "京东", "拼多多", "超市", "便利店", "商场", "优衣库",
            "盒马", "永辉", "沃尔玛", "7-11", "全家", "罗森",
        ),
        // 个人转账（无商户语义）单独归转账，别落进未分类
        "转账" to listOf("转账给", "已转账给", "转账成功", "向XX付款", "付款给个人"),
        "娱乐" to listOf("电影", "游戏", "KTV", "演唱会", "视频会员", "音乐", "腾讯视频", "爱奇艺", "哔哩哔哩"),
        "居住" to listOf("房租", "水费", "电费", "燃气", "物业", "宽带", "取暖", "租金"),
        "医疗" to listOf("医院", "药店", "药房", "诊所", "挂号", "体检", "门诊"),
        "教育" to listOf("学费", "书店", "培训", "网课", "考试", "教材", "知识"),
        // 收入侧
        "工资" to listOf("工资", "薪资", "薪水", "代发"),
        "退款" to listOf("退款", "已退还", "退回"),
        "报销" to listOf("报销"),
        "红包礼金" to listOf("红包", "礼金", "压岁钱"),
        "理财收益" to listOf("收益", "利息", "分红"),
    )

    /**
     * 解析结果三态，供通知监听器区分「明确非支付」与「疑似支付但没识别出」：
     * 后者才值得 AI 兜底与进未识别队列，前者一概静默丢弃。
     */
    sealed interface ParseOutcome {
        /** 命中规则并解析出金额 */
        data class Matched(val payment: ParsedPayment) : ParseOutcome

        /** 明确非支付：白名单外包名 / 空文案 / 黑名单命中（营销、群聊等） */
        object Ignored : ParseOutcome

        /** 疑似支付但规则未命中（新句式）——值得 AI 兜底与人工关注 */
        object NoMatch : ParseOutcome
    }

    /**
     * 判定一段通知文案是否"疑似支付"：含金额形态或支付相关词。
     *
     * 未识别队列与 AI 兜底的前置门：聊天残余（「真觉得自己999李信无敌了」）
     * 与普通推送没有这些特征，不值得占用队列位次，更不值得发给大模型。
     */
    fun looksLikePaymentText(title: String, content: String): Boolean {
        val text = "$title $content"
        return MONEY_SHAPES.any { it.containsMatchIn(text) } ||
            PAYMENT_HINT_WORDS.any { text.contains(it) }
    }

    /** 金额形态：¥12 / 12.5元 */
    private val MONEY_SHAPES = listOf(
        Regex("[¥￥]\\s*[0-9]"),
        Regex("[0-9](?:\\.[0-9]{1,2})?\\s*元"),
    )

    /** 支付相关词：不含「转」「付」这类单字（聊天里太常见，误放行） */
    private val PAYMENT_HINT_WORDS = listOf(
        "收款", "支付", "付款", "转账", "扣款", "退款", "入账", "到账", "交易",
    )

    /**
     * 解析一条通知。
     *
     * @param packageName 通知来源包名（必须来自 [PaymentPackages.WATCHED]，否则返回 null）
     * @param title       通知标题
     * @param content     通知正文（应已按 BigText / TextLines 优先级取过完整文案）
     * @return 解析结果；非支付通知、被忽略、或解析不出金额时返回 null
     */
    fun parse(packageName: String, title: String, content: String): ParsedPayment? =
        when (val outcome = parseDetailed(packageName, title, content)) {
            is ParseOutcome.Matched -> outcome.payment
            else -> null
        }

    /** 三态版解析：通知监听器用它区分「该丢弃」与「该兜底/入队」 */
    fun parseDetailed(packageName: String, title: String, content: String): ParseOutcome {
        if (packageName !in PaymentPackages.WATCHED) return ParseOutcome.Ignored

        // 标题与正文拼起来一起看：部分通知把商户名放在标题、金额放在正文。
        // 但泛化标题（"服务通知"等）没有信息量，跳过以免干扰黑名单与商户提取。
        val rawText = listOf(
            title.takeUnless { it in GENERIC_TITLES },
            content,
        )
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .trim()
        if (rawText.isEmpty()) return ParseOutcome.Ignored

        // 1. 黑名单：命中即不是支付通知
        if (IGNORE_KEYWORDS.any { rawText.contains(it) }) return ParseOutcome.Ignored

        // 1.5 否定/未完成层：支付尚未完成或只是过程描述。
        // 规则关键词按子串匹配，"已支付失败"含"已支付"、"退款申请已提交"含"退款"，
        // 都会误命中——先于规则表整体拦截，并归为 Ignored（不进 AI 兜底与未识别队列）
        if (UNFINISHED_TOKENS.any { rawText.contains(it) }) return ParseOutcome.Ignored

        // 2. 有序规则表
        val rule = RULES.firstOrNull { r ->
            packageName in r.packages && r.keywords.any { rawText.contains(it) }
        } ?: return ParseOutcome.NoMatch

        // 3. 金额：先锚定方向关键词，再通用兜底
        val amountCents = extractAmountCents(rawText) ?: return ParseOutcome.NoMatch
        if (amountCents <= 0L) return ParseOutcome.NoMatch

        return ParseOutcome.Matched(
            ParsedPayment(
                amountCents = amountCents,
                type = rule.type,
                merchant = extractMerchant(rawText),
                rawText = rawText,
                matchedRule = rule.name,
                payChannel = extractPayChannel(rawText),
                cardTail = extractCardTail(rawText),
            ),
        )
    }

    /**
     * 极简支付成功页解析（无障碍专用兜底）。
     *
     * 真机观测（2026-10，微信 8.x）：向个人付款的支付成功页整页只有
     * 「¥0.01 桃桃乐（**悦）」两三个词——"支付成功"等方向词是图片渲染，
     * 不暴露给无障碍，标准规则必然未命中。
     *
     * **安全前提**：只在无障碍服务确认用户处于支付流程（放行窗口内）时调用——
     * 上下文本身已提供"这是支付结果页"的证据，因此不要求方向关键词。
     * 防误读改为三道窄门：
     * 1. 文本必须极短（≤ [MINIMAL_MAX_TOKENS] 个词）：金额输入键盘页有十几个词，必然被挡；
     * 2. 必须恰好含一个带（或不带）货币前缀的金额词；
     * 3. 命中 [IGNORE_KEYWORDS] 黑名单或「失败 / 取消 / 超时」字样的一律不记。
     */
    fun parseMinimalSuccess(packageName: String, text: String): ParsedPayment? {
        if (packageName !in PaymentPackages.WATCHED) return null
        if (IGNORE_KEYWORDS.any { text.contains(it) }) return null

        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size !in 2..MINIMAL_MAX_TOKENS) return null
        if (tokens.any { FAIL_TOKENS.any(it::contains) }) return null

        val amountToken = tokens.firstOrNull { MINIMAL_AMOUNT_TOKEN.matches(it) } ?: return null
        val amountCents = runCatching {
            BigDecimal(amountToken.trim('¥', '￥', '元'))
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
        }.getOrNull() ?: return null
        if (amountCents <= 0L) return null

        // 商户两级提取（真机观测：成功页可能是「¥0.01 摇一摇，有优惠 桃桃乐（**悦）」，
        // 营销文案插在金额和收款人之间，取"第一个文字词"会拿到营销词）：
        // 1. 优先含微信打码括号（**x）的词——收款人实名显示的强特征；
        // 2. 否则取最后一个非噪声文字词——成功页布局收款人靠后、营销词靠前
        val merchant = tokens
            .firstOrNull { MASKED_NAME_TOKEN.containsMatchIn(it) }
            ?: tokens.lastOrNull {
                it != amountToken && !isMinimalNoise(it) && it.any(Char::isLetter)
            }
            .orEmpty()

        return ParsedPayment(
            amountCents = amountCents,
            type = TransactionType.EXPENSE,
            merchant = merchant,
            rawText = text,
            matchedRule = "极简成功页",
        )
    }

    /** 极简成功页文本的词数上限：超过它更像键盘页 / 列表页而非结果页 */
    private const val MINIMAL_MAX_TOKENS = 6

    /** 支付未完成的结果词：出现即绝不记账 */
    private val FAIL_TOKENS = listOf("失败", "取消", "超时")

    /** 金额词形态：¥0.01 / ￥12 / 0.5元 等 */
    private val MINIMAL_AMOUNT_TOKEN = Regex("^[¥￥]?[0-9]+(?:\\.[0-9]{1,2})?元?$")

    /** 微信收款人的打码实名形态：桃桃乐（**悦）——括号加两个星，强特征 */
    private val MASKED_NAME_TOKEN = Regex("（\\*\\*|\\(\\*\\*")

    /** 成功页上的 UI / 营销噪声词：不能当商户名（按包含判断，挡「摇一摇，有优惠」这类文案） */
    private val MINIMAL_UI_NOISE_TOKENS = listOf(
        "微信支付", "付款", "支付", "支付成功", "完成", "返回", "确定", "浮窗",
        "摇一摇", "优惠", "领取", "活动", "返现", "立减", "红包",
    )

    private fun isMinimalNoise(token: String): Boolean =
        MINIMAL_UI_NOISE_TOKENS.any(token::contains)

    /**
     * 清洗读屏文本，供备注降级使用（商户名提取不到时才填备注）。
     *
     * 无障碍读屏读到的是整块窗口，混有大量与支付无关的系统 UI 文本——
     * 真机观测（2026-10，微信支付凭证横幅弹出的瞬间）：
     * 「11:01 10月2日 周五 微信支付 下午11:01 [7条]微信支付: 已支付¥2.00 2 已连接到 USB 调试」
     * 状态栏时间 / 日期 / 周几 / [N条] / 独立数字 / 域名 / 系统通知短语
     * 都有固定形态，按词剔除即可，无需动用大模型。
     */
    fun sanitizeForNote(text: String): String = text
        .replace(USB_DEBUG_NOTICE, " ")
        .split(Regex("\\s+"))
        // 「[7条]微信支付:」——[N条] 是粘在词上的前缀，先剥离再判噪声
        .map { token -> NOTE_COUNT_PREFIX.replace(token, "") }
        .filterNot { token ->
            token.isBlank() ||
                NOTE_TIME_TOKEN.matches(token) ||
                NOTE_DATE_TOKEN.matches(token) ||
                NOTE_WEEK_TOKEN.matches(token) ||
                token.all(Char::isDigit) ||
                NOTE_HOST_TOKEN.matches(token)
        }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()

    /**
     * 生成写入交易备注的文本：**商户名优先，其次只截取"这一笔支付"那一小段**。
     *
     * 这条规则是「备注里塞进整屏聊天消息」的根治点。无障碍读屏拿到的是整块窗口
     * （用户可能正停在聊天列表、群聊、商品详情页上），若直接把 [ParsedPayment.rawText]
     * 截断写进备注，用户看到的备注就是一堆与自己无关的窗口文字。因此：
     *
     * 1. 有商户名 → 直接用商户名（最干净，也是学习表的 key）；
     * 2. 没有商户名 → 在清洗后的文本里**以支付语义词为锚点**取前后各一个词
     *    （如「微信支付: 已支付¥2.00」），而不是取开头 N 个字符；
     * 3. 锚点也找不到（如极简成功页只有金额与收款人）→ 退回清洗后的整段，
     *    但这类文本本身就极短。
     *
     * 最终一律截到 [NOTE_MAX_CHARS] 字以内。
     */
    fun noteFor(parsed: ParsedPayment): String {
        val merchant = sanitizeForNote(parsed.merchant)
        if (merchant.isNotBlank()) return merchant.take(NOTE_MAX_CHARS)
        val cleaned = sanitizeForNote(parsed.rawText)
        if (cleaned.isBlank()) return ""
        return bestNoteSegment(cleaned).take(NOTE_MAX_CHARS).trim()
    }

    /**
     * 在长文本里截出与「这一笔支付」最相关的一小段：以第一个支付语义词为锚点，
     * 取前 [NOTE_CONTEXT_BEFORE] 个词与后 [NOTE_CONTEXT_AFTER] 个词。
     *
     * 找不到锚点词时原样返回（调用方会再截长度）。
     */
    private fun bestNoteSegment(text: String): String {
        val tokens = text.split(' ').filter { it.isNotBlank() }
        val anchor = tokens.indexOfFirst { token -> NOTE_ANCHOR_KEYWORDS.any(token::contains) }
        if (anchor < 0) return text
        // 金额常与锚点词同在一个词里（"已支付¥2.00"）：此时不再向后多取一个词，
        // 否则紧跟其后的聊天内容（"好的"）会被带进备注
        val after = if (tokens[anchor].any(Char::isDigit)) 0 else NOTE_CONTEXT_AFTER
        val from = (anchor - NOTE_CONTEXT_BEFORE).coerceAtLeast(0)
        val to = (anchor + after).coerceAtMost(tokens.lastIndex)
        return tokens.subList(from, to + 1).joinToString(" ")
    }

    /** 备注最大长度：只够描述一笔支付，装不下整屏文本 */
    private const val NOTE_MAX_CHARS = 40

    /** 锚点前后各取几个词 */
    private const val NOTE_CONTEXT_BEFORE = 1
    private const val NOTE_CONTEXT_AFTER = 1

    /**
     * 备注锚点词：与 [RULES] 的方向词保持同一套语义（支付/收款/转账/退款），
     * 用于在整屏文本中定位「这一笔」。
     */
    private val NOTE_ANCHOR_KEYWORDS = listOf(
        "已支付", "支付成功", "付款成功", "已付款", "支付完成", "已成功付款", "成功付款",
        "交易成功", "已扣款", "扣款成功", "自动扣款", "已转账给", "转账成功",
        "收款到账", "收款成功", "成功收款", "已收款", "已存入零钱",
        "退款成功", "已退还", "退款已到账", "已退回",
    )

    /** 调试期开 USB 时系统横幅会混进读屏文本，整段剔除 */
    private val USB_DEBUG_NOTICE = Regex("已连接到\\s*USB\\s*调试")

    /** 11:01 / 下午11:01 / 上午9:05:30 */
    private val NOTE_TIME_TOKEN = Regex("^([上下]午|凌晨|早上|中午)?\\d{1,2}:\\d{2}(?::\\d{2})?$")

    /** 10月2日 / 2026年10月2日 */
    private val NOTE_DATE_TOKEN = Regex("^(\\d{4}年)?\\d{1,2}月\\d{1,2}日$")

    /** 周五 / 星期五 */
    private val NOTE_WEEK_TOKEN = Regex("^(周|星期)[一二三四五六日天]$")

    /** [7条] 微信消息计数前缀（粘在词首，剥离子用） */
    private val NOTE_COUNT_PREFIX = Regex("^\\[\\d+条]")

    /** 域名 / URL：h5.jrywl.com、https://pay.xxx.com/q */
    private val NOTE_HOST_TOKEN = Regex("^(https?://)?[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?$", RegexOption.IGNORE_CASE)

    /** 提取金额并转成「分」；解析不出返回 null */
    private fun extractAmountCents(text: String): Long? {
        val matched = AMOUNT_NEAR_KEYWORD.find(text)?.groupValues?.getOrNull(1)
            ?: AMOUNT_FALLBACK.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }
            ?: return null
        return runCatching {
            // 用 BigDecimal 直接进位，避免 Double 的二进制误差
            BigDecimal(matched).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        }.getOrNull()
    }

    /** 提取商户名，失败返回空串 */
    private fun extractMerchant(text: String): String {
        MERCHANT_PATTERNS.forEach { regex ->
            val m = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!m.isNullOrBlank()) return m
        }
        return ""
    }

    /** 提取支付方式标签（零钱/花呗/银行卡等），文案未提及时返回 null */
    private fun extractPayChannel(text: String): String? =
        PAY_CHANNEL_PATTERNS.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first

    /**
     * 提取银行卡尾号（4 位）。public 供 AI 解析路径复用
     * （模型返回的 pay_channel 可能是「银行卡(1234)」这类复合串）。
     */
    fun extractCardTail(text: String): String? =
        CARD_TAIL_PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }

    /**
     * 按商户关键词猜测分类名。
     *
     * 返回的是**分类名称**而非 id，由调用方在自己的分类表里按名称匹配——
     * 这样解析器不需要依赖数据库，也便于单测。
     *
     * @param text        用于匹配的文本（建议传解析出的商户名 + 原始文案）
     * @param type        收支方向，用于优先匹配同类关键词
     * @return 命中的分类名；无命中返回 null
     */
    fun guessCategoryName(text: String, type: TransactionType): String? {
        // 收入侧只匹配收入类关键词，支出侧只匹配支出类关键词，避免「退款」被算进餐饮
        val incomeOnly = listOf("工资", "退款", "报销", "红包礼金", "理财收益")
        return CATEGORY_KEYWORDS
            .filter { (name, _) ->
                if (type == TransactionType.INCOME) name in incomeOnly else name !in incomeOnly
            }
            .firstOrNull { (_, keys) -> keys.any { text.contains(it) } }
            ?.first
    }
}
