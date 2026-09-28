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
 */
data class ParsedPayment(
    val amountCents: Long,
    val type: TransactionType,
    val merchant: String,
    val rawText: String,
    val matchedRule: String,
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

    /**
     * 泛化标题：这些标题本身不携带任何支付信息（只是消息来源的容器名），
     * 拼进 rawText 只会干扰黑名单判断与商户名提取，因此组装时直接跳过，
     * 只用正文解析。
     */
    private val GENERIC_TITLES = setOf(
        "服务通知", "微信支付", "支付助手", "微信", "支付宝", "收款助手", "微信收款助手",
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
            keywords = listOf("已存入零钱", "转账到账", "已收款", "待入账"),
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
            keywords = listOf("退款", "已退还", "退回", "退款成功"),
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
     * 解析一条通知。
     *
     * @param packageName 通知来源包名（必须来自 [PaymentPackages.WATCHED]，否则返回 null）
     * @param title       通知标题
     * @param content     通知正文（应已按 BigText / TextLines 优先级取过完整文案）
     * @return 解析结果；非支付通知、被忽略、或解析不出金额时返回 null
     */
    fun parse(packageName: String, title: String, content: String): ParsedPayment? {
        if (packageName !in PaymentPackages.WATCHED) return null

        // 标题与正文拼起来一起看：部分通知把商户名放在标题、金额放在正文。
        // 但泛化标题（"服务通知"等）没有信息量，跳过以免干扰黑名单与商户提取。
        val rawText = listOf(
            title.takeUnless { it in GENERIC_TITLES },
            content,
        )
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .trim()
        if (rawText.isEmpty()) return null

        // 1. 黑名单：命中即不是支付通知
        if (IGNORE_KEYWORDS.any { rawText.contains(it) }) return null

        // 2. 有序规则表
        val rule = RULES.firstOrNull { r ->
            packageName in r.packages && r.keywords.any { rawText.contains(it) }
        } ?: return null

        // 3. 金额：先锚定方向关键词，再通用兜底
        val amountCents = extractAmountCents(rawText) ?: return null
        if (amountCents <= 0L) return null

        return ParsedPayment(
            amountCents = amountCents,
            type = rule.type,
            merchant = extractMerchant(rawText),
            rawText = rawText,
            matchedRule = rule.name,
        )
    }

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
