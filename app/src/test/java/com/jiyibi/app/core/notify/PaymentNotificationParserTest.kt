package com.jiyibi.app.core.notify

import com.jiyibi.app.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---------- 极简成功页（无障碍放行窗口内专用兜底） ----------

    @Test
    fun `极简成功页金额加收款人`() {
        // 真机观测（2026-10，微信 8.x 向个人付款成功页的完整读屏文本）：
        // 整页没有方向词（"支付成功"是图片渲染），标准规则必然未命中
        val parsed = PaymentNotificationParser.parseMinimalSuccess(
            PaymentPackages.WECHAT,
            "¥0.01 桃桃乐（**悦）",
        )
        assertEquals(1L, parsed?.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("桃桃乐（**悦）", parsed?.merchant)
    }

    @Test
    fun `极简成功页营销文案不抢占商户名`() {
        // 真机观测：成功页为「¥0.01 摇一摇，有优惠 桃桃乐（**悦）」——
        // 营销词插在金额与收款人之间，商户必须取打码实名而非营销文案
        val parsed = PaymentNotificationParser.parseMinimalSuccess(
            PaymentPackages.WECHAT,
            "¥0.01 摇一摇，有优惠 桃桃乐（**悦）",
        )
        assertEquals(1L, parsed?.amountCents)
        assertEquals("桃桃乐（**悦）", parsed?.merchant)
    }

    @Test
    fun `极简成功页无打码形态时商户取末位文字词`() {
        // 收款人无打码括号时退化为"最后一个非噪声文字词"（布局上收款人靠后）
        val parsed = PaymentNotificationParser.parseMinimalSuccess(
            PaymentPackages.ALIPAY,
            "¥12.00 立减优惠 某某超市",
        )
        assertEquals(1200L, parsed?.amountCents)
        assertEquals("某某超市", parsed?.merchant)
    }

    @Test
    fun `极简成功页不带货币符号的金额`() {
        val parsed = PaymentNotificationParser.parseMinimalSuccess(
            PaymentPackages.WECHAT,
            "12.5元 某某",
        )
        assertEquals(1250L, parsed?.amountCents)
    }

    @Test
    fun `金额输入键盘页不误判`() {
        // 真机观测：输入页有十几个词（数字键盘 + 标签），词数门槛必须挡住
        assertNull(
            PaymentNotificationParser.parseMinimalSuccess(
                PaymentPackages.WECHAT,
                "付款 1 2 3 4 5 6 7 8 9 0 . 付款 付款给个人 桃桃乐（**悦） 金额 添加备注 0.01",
            ),
        )
    }

    @Test
    fun `指纹与加载提示不误判`() {
        // 真机观测：流程中的弹层文本，均无金额词
        assertNull(PaymentNotificationParser.parseMinimalSuccess(PaymentPackages.WECHAT, "请验证指纹"))
        assertNull(PaymentNotificationParser.parseMinimalSuccess(PaymentPackages.WECHAT, "正在加载…"))
        assertNull(PaymentNotificationParser.parseMinimalSuccess(PaymentPackages.WECHAT, "微信支付"))
    }

    @Test
    fun `失败取消结果不记账`() {
        assertNull(
            PaymentNotificationParser.parseMinimalSuccess(
                PaymentPackages.WECHAT,
                "支付失败 ¥0.01 桃桃乐",
            ),
        )
        assertNull(
            PaymentNotificationParser.parseMinimalSuccess(
                PaymentPackages.WECHAT,
                "已取消 ¥0.01",
            ),
        )
    }

    @Test
    fun `极简页黑名单词不记账`() {
        // 营销 / 理财类短文案即使带金额也不许进
        assertNull(
            PaymentNotificationParser.parseMinimalSuccess(
                PaymentPackages.ALIPAY,
                "余额宝收益 ¥1.00 已到账",
            ),
        )
    }

    @Test
    fun `极简页纯UI词无金额不记账`() {
        assertNull(
            PaymentNotificationParser.parseMinimalSuccess(
                PaymentPackages.WECHAT,
                "浮窗 完成",
            ),
        )
    }

    @Test
    fun `极简页白名单外包名不记账`() {
        assertNull(
            PaymentNotificationParser.parseMinimalSuccess(
                "com.example.other",
                "¥0.01 某某",
            ),
        )
    }

    // ---------- 疑似支付判定（未识别队列与 AI 兜底的前置门） ----------

    @Test
    fun `聊天残余不含支付特征`() {
        // 真机观测：聊天通知「真觉得自己999李信无敌了」——有数字但无金额形态无支付词
        assertFalse(
            PaymentNotificationParser.looksLikePaymentText("桃桃乐", "[2条]桃桃乐: 真觉得自己999李信无敌了"),
        )
    }

    @Test
    fun `金额符号算支付特征`() {
        assertTrue(PaymentNotificationParser.looksLikePaymentText("服务通知", "您有一笔 ¥35.00 的消费"))
    }

    @Test
    fun `N元形态算支付特征`() {
        assertTrue(PaymentNotificationParser.looksLikePaymentText("支付助手", "成功付款35.00元"))
    }

    @Test
    fun `支付词算支付特征`() {
        assertTrue(PaymentNotificationParser.looksLikePaymentText("微信", "有一笔待确认的收款"))
    }

    @Test
    fun `普通闲聊不算支付特征`() {
        assertFalse(PaymentNotificationParser.looksLikePaymentText("张三", "今晚一起吃饭吗"))
    }

    // ---------- 三态解析 ----------

    @Test
    fun `黑名单命中归为Ignored而非NoMatch`() {
        // 明确非支付（营销推送）不该进未识别队列——队列只收「疑似漏掉的支付句式」
        assertEquals(
            PaymentNotificationParser.ParseOutcome.Ignored,
            PaymentNotificationParser.parseDetailed(
                PaymentPackages.ALIPAY,
                title = "支付宝",
                content = "蚂蚁森林：你有新的能量可以收取啦",
            ),
        )
    }

    @Test
    fun `规则未命中的疑似文案归为NoMatch`() {
        assertEquals(
            PaymentNotificationParser.ParseOutcome.NoMatch,
            PaymentNotificationParser.parseDetailed(
                PaymentPackages.WECHAT,
                title = "服务通知",
                content = "您刚刚成功消费35.00元，点击查看详情",
            ),
        )
    }

    @Test
    fun `命中规则归为Matched`() {
        val outcome = PaymentNotificationParser.parseDetailed(
            PaymentPackages.WECHAT,
            title = "服务通知",
            content = "微信支付：已支付￥35.00，付款给星巴克",
        )
        assertTrue(outcome is PaymentNotificationParser.ParseOutcome.Matched)
        assertEquals(3500L, (outcome as PaymentNotificationParser.ParseOutcome.Matched).payment.amountCents)
    }

    // ---------- 备注文本清洗（读屏噪声剔除） ----------

    @Test
    fun `读屏文本剔除状态栏与系统通知噪声`() {
        // 真机观测（2026-10）：支付凭证横幅弹出的瞬间，读屏扫到整块通知栏
        val cleaned = PaymentNotificationParser.sanitizeForNote(
            "11:01 10月2日 周五 微信支付 下午11:01 [7条]微信支付: 已支付¥2.00 2 已连接到 USB 调试",
        )
        assertEquals("微信支付 微信支付: 已支付¥2.00", cleaned)
    }

    @Test
    fun `清洗剔除域名与URL`() {
        // 真机观测：H5 收银台页面读屏首词是 host
        assertEquals(
            "微信支付手机版 微信支付 ¥2.00",
            PaymentNotificationParser.sanitizeForNote(
                "h5.jrywl.com 微信支付手机版 微信支付 ¥2.00",
            ),
        )
    }

    @Test
    fun `正常通知文本清洗后不受影响`() {
        assertEquals(
            "微信支付：已支付￥35.00，付款给星巴克",
            PaymentNotificationParser.sanitizeForNote("微信支付：已支付￥35.00，付款给星巴克"),
        )
    }

    // ---------- 否定/未完成层（支付未完成不记账） ----------

    @Test
    fun `支付失败不记账`() {
        // "已支付失败"含规则词"已支付"，曾会误命中——否定层必须先行拦截
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.WECHAT,
                title = "服务通知",
                content = "微信支付：已支付失败 ¥35.00，请重试",
            ),
        )
    }

    @Test
    fun `退款申请过程描述不记为收入`() {
        // "退款申请已提交"曾命中宽泛的"退款"关键词被记成已到账收入
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.WECHAT,
                title = "服务通知",
                content = "您的退款申请已提交，预计1-3个工作日到账 ¥35.00",
            ),
        )
    }

    @Test
    fun `待入账不算已到账收入`() {
        // "待入账"是未完成状态，曾直接记为收入导致余额虚高
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.WECHAT,
                title = "服务通知",
                content = "对方已转账，金额待入账 ¥50.00",
            ),
        )
    }

    @Test
    fun `已取消订单不记账`() {
        assertNull(
            PaymentNotificationParser.parse(
                PaymentPackages.ALIPAY,
                title = "支付助手",
                content = "订单已取消，付款 ¥99.00 已原路退回",
            ),
        )
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
