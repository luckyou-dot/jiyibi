package com.jiyibi.app.core.notify

import com.jiyibi.app.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PaymentNotificationParser.noteFor] 的备注生成测试。
 *
 * 这条逻辑的存在理由就是一个真实缺陷：无障碍读屏读的是**整个活动窗口**，
 * 用户当时可能停在聊天列表 / 群聊 / 商品页上，若把原始读屏文本截断写进备注，
 * 用户看到的备注就是一堆与自己无关的窗口文字（"备注详情把当前所有窗口消息都填进去了"）。
 *
 * 用例把三种情形钉住：
 * 1. 有商户名 → 备注就是商户名；
 * 2. 没有商户名但文本里有支付语义词 → 只取锚点前后的那一小段；
 * 3. 锚点也找不到 → 退回清洗后的文本并截断，绝不无限长。
 */
class PaymentNoteTest {

    @Test
    fun `有商户名时备注就是商户名`() {
        val parsed = parsed(merchant = "桃桃乐", rawText = "整屏无关文字 已支付¥2.00 更多无关文字")
        assertEquals("桃桃乐", PaymentNotificationParser.noteFor(parsed))
    }

    @Test
    fun `整屏读屏只取支付那一小段`() {
        // 真机观测形态：用户停在聊天窗口，微信支付凭证横幅叠在聊天内容之上
        val parsed = parsed(
            merchant = "",
            rawText = "张三 在吗 今晚吃什么 李四 随便 微信支付: 已支付¥35.00 好的 明天见",
        )
        val note = PaymentNotificationParser.noteFor(parsed)

        assertTrue("备注应包含这一笔支付：$note", note.contains("已支付¥35.00"))
        assertTrue("备注应带上来源前缀：$note", note.contains("微信支付"))
        assertFalse("聊天内容不应进备注：$note", note.contains("张三"))
        assertFalse("聊天内容不应进备注：$note", note.contains("明天见"))
    }

    @Test
    fun `状态栏与系统提示噪声被剔除`() {
        // 与 PaymentNotificationParser.sanitizeForNote 的 KDoc 中记录的真机文本一致
        val parsed = parsed(
            merchant = "",
            rawText = "11:01 10月2日 周五 微信支付 下午11:01 [7条]微信支付: 已支付¥2.00 2 已连接到 USB 调试",
        )
        val note = PaymentNotificationParser.noteFor(parsed)

        assertTrue("应保留支付语义：$note", note.contains("已支付¥2.00"))
        assertFalse("时间戳不应进备注：$note", note.contains("11:01"))
        assertFalse("日期不应进备注：$note", note.contains("10月2日"))
        assertFalse("USB 调试提示不应进备注：$note", note.contains("USB"))
        assertFalse("消息计数不应进备注：$note", note.contains("[7条]"))
    }

    @Test
    fun `锚点词与金额分成两个词时金额也保留`() {
        val parsed = parsed(merchant = "", rawText = "无关内容 已支付 ¥12.50 后续内容")
        val note = PaymentNotificationParser.noteFor(parsed)

        assertTrue("金额应保留：$note", note.contains("¥12.50"))
        assertFalse("锚点后的无关内容不应进备注：$note", note.contains("后续内容"))
    }

    @Test
    fun `找不到锚点词时退回清洗后的文本并截断`() {
        val parsed = parsed(
            merchant = "",
            rawText = "这是一段没有任何支付语义词的很长很长很长很长很长很长很长很长很长的文本 结束",
        )
        val note = PaymentNotificationParser.noteFor(parsed)

        assertTrue("长度必须被截断，实际 ${note.length}", note.length <= 40)
        assertTrue(note.startsWith("这是一段"))
    }

    @Test
    fun `完全没有可用文本时备注为空串`() {
        assertEquals("", PaymentNotificationParser.noteFor(parsed(merchant = "", rawText = "  ")))
        // 时间是噪声词，清洗后为空
        assertEquals("", PaymentNotificationParser.noteFor(parsed(merchant = "", rawText = "11:01 周五")))
    }

    @Test
    fun `商户名过长也会被截断`() {
        val parsed = parsed(merchant = "某".repeat(80), rawText = "")
        assertEquals(40, PaymentNotificationParser.noteFor(parsed).length)
    }

    private fun parsed(merchant: String, rawText: String): ParsedPayment = ParsedPayment(
        amountCents = 200,
        type = TransactionType.EXPENSE,
        merchant = merchant,
        rawText = rawText,
        matchedRule = "测试",
    )
}
