package com.jiyibi.app.core.notify

import com.jiyibi.app.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 支付通知解析器回归测试。
 *
 * fixture 全部取自微信 / 支付宝真机通知的典型形态（含曾经造成
 * 「自动记账几个月完全不工作」的「服务通知」标题场景），
 * 调整 [PaymentNotificationParser] 规则前先跑这里，防止改一处坏一处。
 */
class PaymentNotificationParserTest {

    // ---------- 支出：微信 ----------

    @Test
    fun `微信支付凭证走服务通知标题不误杀`() {
        // 曾经的致命 bug：标题"服务通知"命中黑名单导致所有微信支付通知被丢弃
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：已支付￥35.00，付款给星巴克",
        )
        assertEquals(3500L, parsed?.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("星巴克", parsed?.merchant)
    }

    @Test
    fun `微信支付成功带余额时金额取支付额`() {
        // 通用 ¥ 兜底会先抓到余额 1000，锚定"已支付"的正则必须取到 35
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：当前余额¥1000.00，已支付¥35.00",
        )
        assertEquals(3500L, parsed?.amountCents)
    }

    @Test
    fun `微信收款助手商家收款记为收入`() {
        // 商家收款通知没有「到账/成功」后缀，就是光秃秃的「收款￥88.00」
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "微信收款助手",
            content = "收款￥88.00",
        )
        assertEquals(8800L, parsed?.amountCents)
        assertEquals(TransactionType.INCOME, parsed?.type)
    }

    // ---------- 支出：支付宝 ----------

    @Test
    fun `支付宝支付助手成功付款词序`() {
        // 「成功付款」与「付款成功」是两种真实词序，旧规则只认后者
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.ALIPAY,
            title = "支付助手",
            content = "成功付款35.00元，给肯德基",
        )
        assertEquals(3500L, parsed?.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
    }

    @Test
    fun `支付宝付款成功带人民币符号`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.ALIPAY,
            title = "支付宝",
            content = "交易成功：付款￥99.90 给某某超市",
        )
        assertEquals(9990L, parsed?.amountCents)
    }

    // ---------- 支付成功页面（无障碍读屏文本，无标题） ----------

    @Test
    fun `无障碍微信支付成功页文本`() {
        // 模拟扫一扫支付成功页的屏幕文本集合
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "",
            content = "微信支付 支付成功 ¥35.00 付款给星巴克",
        )
        assertEquals(3500L, parsed?.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("星巴克", parsed?.merchant)
    }

    @Test
    fun `无障碍微信转账给个人`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "",
            content = "已转账给张三 ¥128.50",
        )
        assertEquals(12850L, parsed?.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("张三", parsed?.merchant)
    }

    @Test
    fun `无障碍支付宝转账成功页`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.ALIPAY,
            title = "",
            content = "转账成功 ¥50.00 给李四",
        )
        assertEquals(5000L, parsed?.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
    }

    @Test
    fun `浏览页面出现历史支付文案但无方向词不记账`() {
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.WECHAT,
                title = "",
                content = "账单明细 3月 1日 ¥35.00 2日 ¥128.50",
            ),
        )
    }

    // ---------- 收入 ----------

    @Test
    fun `微信收款到账记为收入`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：收款￥88.00，已存入零钱",
        )
        assertEquals(8800L, parsed?.amountCents)
        assertEquals(TransactionType.INCOME, parsed?.type)
    }

    @Test
    fun `退款文案收入规则优先于支出规则`() {
        // 「退款：已退还」与「原支付单已支付」同现，收入规则必须先命中
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：退款￥35.00已退还（原支付单已支付￥35.00）",
        )
        assertEquals(TransactionType.INCOME, parsed?.type)
        assertEquals(3500L, parsed?.amountCents)
    }

    // ---------- 支付方式提取（按支付方式选账户） ----------

    @Test
    fun `支付方式零钱`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：已支付￥35.00，支付方式：零钱",
        )
        assertEquals("零钱", parsed?.payChannel)
        assertNull(parsed?.cardTail)
    }

    @Test
    fun `支付方式储蓄卡带尾号`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：已支付￥35.00，从储蓄卡(1234)扣款",
        )
        assertEquals("储蓄卡", parsed?.payChannel)
        assertEquals("1234", parsed?.cardTail)
    }

    @Test
    fun `支付方式银行卡尾号形态`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.ALIPAY,
            title = "支付助手",
            content = "成功付款35.00元，银行卡尾号8899，给肯德基",
        )
        assertEquals("银行卡", parsed?.payChannel)
        assertEquals("8899", parsed?.cardTail)
    }

    @Test
    fun `花呗支付识别`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.ALIPAY,
            title = "支付宝",
            content = "交易成功：付款￥99.90 花呗支付 给某某超市",
        )
        assertEquals("花呗", parsed?.payChannel)
    }

    @Test
    fun `余额宝优先于余额`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.ALIPAY,
            title = "支付宝",
            content = "交易成功：付款￥10.00，余额宝",
        )
        assertEquals("余额宝", parsed?.payChannel)
    }

    @Test
    fun `文案中的账户余额不算支付方式`() {
        // 「当前余额¥1000」是余额描述不是支付方式，不能因此把账记到支付宝余额
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：当前余额¥1000.00，已支付¥35.00",
        )
        assertNull(parsed?.payChannel)
    }

    @Test
    fun `未提及支付方式时为null`() {
        val parsed = PaymentNotificationParser.parse(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：已支付￥35.00，付款给星巴克",
        )
        assertNull(parsed?.payChannel)
        assertNull(parsed?.cardTail)
    }

    // ---------- 应忽略 / 应返回 null ----------

    @Test
    fun `群聊消息被忽略`() {
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.WECHAT,
                title = "张三",
                content = "群聊：今晚一起吃饭吗",
            ),
        )
    }

    @Test
    fun `支付宝营销推送被忽略`() {
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.ALIPAY,
                title = "支付宝",
                content = "蚂蚁森林：你有新的能量可以收取啦",
            ),
        )
    }

    @Test
    fun `公众号营销点击领取被忽略`() {
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.WECHAT,
                title = "某某公众号",
                content = "点击领取新人优惠券",
            ),
        )
    }

    @Test
    fun `白名单外包名直接返回null`() {
        assertNull(
            PaymentNotificationParser.parse(
                "com.example.other",
                title = "服务通知",
                content = "已支付￥35.00",
            ),
        )
    }

    @Test
    fun `命中规则但取不出金额返回null`() {
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.WECHAT,
                title = "服务通知",
                content = "微信支付：支付成功，请查看账单详情",
            ),
        )
    }
}
