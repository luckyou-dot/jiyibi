package com.jiyibi.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiPaymentParser.parseAiReview] 的解析测试。
 *
 * 与 [AiPaymentParser.parseAiPaymentResponse] 的关键区别：审核任务允许模型**否决**
 * （`is_payment=false`），所以 false 必须被解析出来而不是当成解析失败。
 * 只有"连 is_payment 字段都没有"才算审核不可用（返回 null → 调用方按原样记账）。
 */
class AiReviewParsingTest {

    @Test
    fun `正常审核结论`() {
        val review = AiPaymentParser.parseAiReview(
            """{"is_payment": true, "merchant": "星巴克", "note": "咖啡 35 元"}""",
        )
        assertEquals(AiReview(isPayment = true, merchant = "星巴克", note = "咖啡 35 元"), review)
    }

    @Test
    fun `否决也必须解析出来而不是当成失败`() {
        val review = AiPaymentParser.parseAiReview("""{"is_payment": false, "merchant": "", "note": ""}""")
        assertFalse("否决结论必须被解析出来", review == null)
        assertEquals(false, review?.isPayment)
        assertEquals("", review?.merchant)
        assertEquals("", review?.note)
    }

    @Test
    fun `缺字段时补空串`() {
        val review = AiPaymentParser.parseAiReview("""{"is_payment": true}""")
        assertEquals(AiReview(isPayment = true, merchant = "", note = ""), review)
    }

    @Test
    fun `markdown 包裹的输出也能解析`() {
        val review = AiPaymentParser.parseAiReview(
            "```json\n{\"is_payment\": true, \"merchant\": \"滴滴\", \"note\": \"打车\"}\n```",
        )
        assertEquals("滴滴", review?.merchant)
        assertEquals("打车", review?.note)
    }

    @Test
    fun `没有 is_payment 字段视为审核不可用`() {
        assertNull(AiPaymentParser.parseAiReview("""{"merchant": "星巴克"}"""))
        assertNull(AiPaymentParser.parseAiReview("模型今天不想返回 JSON"))
        assertNull(AiPaymentParser.parseAiReview(""))
    }

    @Test
    fun `超长商户名与备注被截断`() {
        val review = AiPaymentParser.parseAiReview(
            """{"is_payment": true, "merchant": "${"商".repeat(50)}", "note": "${"备".repeat(50)}"}""",
        )
        assertEquals(20, review?.merchant?.length)
        assertEquals(24, review?.note?.length)
    }

    @Test
    fun `extractJsonObject 的既有行为不受影响`() {
        // 审核解析复用了同一个抠 JSON 的工具，这里顺带锁死它的边界行为
        assertNull(AiPaymentParser.extractJsonObject("没有花括号"))
        assertNull(AiPaymentParser.extractJsonObject("}"))
        assertTrue(AiPaymentParser.extractJsonObject("前缀 {\"a\":1} 后缀")!!.startsWith("{"))
    }
}
