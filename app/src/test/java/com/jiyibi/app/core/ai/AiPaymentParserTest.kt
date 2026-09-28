package com.jiyibi.app.core.ai

import com.jiyibi.app.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AI 输出解析的回归测试（只测纯函数部分，不触网）。
 *
 * 大模型的输出不完全可控：会带 markdown 代码块、前后缀解释、甚至漏字段，
 * 这里固定住「宽容提取、严格校验」的行为。
 */
class AiPaymentParserTest {

    // ---------- extractJsonObject ----------

    @Test
    fun `提取裸JSON`() {
        assertEquals("""{"a":1}""", AiPaymentParser.extractJsonObject("""{"a":1}"""))
    }

    @Test
    fun `提取markdown代码块中的JSON`() {
        val raw = "```json\n{\"is_payment\": true, \"type\": \"expense\"}\n```"
        assertEquals(
            """{"is_payment": true, "type": "expense"}""",
            AiPaymentParser.extractJsonObject(raw),
        )
    }

    @Test
    fun `提取带前后缀解释的JSON`() {
        val raw = "好的，解析结果如下：{\"is_payment\": true} 以上。"
        assertEquals("""{"is_payment": true}""", AiPaymentParser.extractJsonObject(raw))
    }

    @Test
    fun `没有JSON时返回null`() {
        assertNull(AiPaymentParser.extractJsonObject("这笔通知无法判断"))
    }

    // ---------- parseAiPaymentResponse ----------

    @Test
    fun `正常解析支出`() {
        val parsed = AiPaymentParser.parseAiPaymentResponse(
            """{"is_payment": true, "type": "expense", "amount": "35.50", "merchant": "星巴克"}""",
        )
        assertEquals(3550L, parsed?.amountCents)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        assertEquals("星巴克", parsed?.merchant)
        assertEquals("AI", parsed?.matchedRule)
    }

    @Test
    fun `正常解析收入`() {
        val parsed = AiPaymentParser.parseAiPaymentResponse(
            """{"is_payment": true, "type": "income", "amount": "88", "merchant": ""}""",
        )
        assertEquals(8800L, parsed?.amountCents)
        assertEquals(TransactionType.INCOME, parsed?.type)
    }

    @Test
    fun `markdown包裹的输出也能解析`() {
        val parsed = AiPaymentParser.parseAiPaymentResponse(
            "```json\n{\"is_payment\": true, \"type\": \"expense\", \"amount\": \"12.34\", \"merchant\": \"美团\"}\n```",
        )
        assertEquals(1234L, parsed?.amountCents)
        assertEquals("美团", parsed?.merchant)
    }

    @Test
    fun `非支付通知返回null`() {
        assertNull(
            AiPaymentParser.parseAiPaymentResponse(
                """{"is_payment": false, "type": "expense", "amount": "35.00"}""",
            ),
        )
    }

    @Test
    fun `方向字段非法返回null`() {
        assertNull(
            AiPaymentParser.parseAiPaymentResponse(
                """{"is_payment": true, "type": "transfer", "amount": "35.00"}""",
            ),
        )
    }

    @Test
    fun `金额非法返回null`() {
        assertNull(
            AiPaymentParser.parseAiPaymentResponse(
                """{"is_payment": true, "type": "expense", "amount": "abc"}""",
            ),
        )
    }
}
