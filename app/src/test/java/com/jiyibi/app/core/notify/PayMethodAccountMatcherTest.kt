package com.jiyibi.app.core.notify

import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「支付方式 → 记账账户」匹配器的行为基线。
 *
 * 核心原则：**宁落默认账户也不错配**——同类型多个账户、无任何可靠信号时返回 null。
 */
class PayMethodAccountMatcherTest {

    private fun acc(id: Long, name: String, type: AccountType) = Account(
        id = id, name = name, type = type, balance = 0, color = 0, sortOrder = 0,
    )

    @Test
    fun `卡尾号精确命中同名账户`() {
        val accounts = listOf(
            acc(1, "现金", AccountType.CASH),
            acc(2, "招商银行(1234)", AccountType.BANK),
            acc(3, "建设银行(5678)", AccountType.BANK),
        )
        assertEquals(2L, PayMethodAccountMatcher.match(null, "1234", accounts)?.id)
        assertEquals(3L, PayMethodAccountMatcher.match(null, "5678", accounts)?.id)
    }

    @Test
    fun `尾号无对应账户时返回null`() {
        val accounts = listOf(acc(1, "现金", AccountType.CASH))
        assertNull(PayMethodAccountMatcher.match(null, "9999", accounts))
    }

    @Test
    fun `账户名包含支付方式直接命中`() {
        val accounts = listOf(
            acc(1, "支付宝", AccountType.ALIPAY),
            acc(2, "花呗", AccountType.OTHER),
        )
        assertEquals(2L, PayMethodAccountMatcher.match("花呗", null, accounts)?.id)
    }

    @Test
    fun `零钱通退化为含零钱的账户`() {
        val accounts = listOf(
            acc(1, "现金", AccountType.CASH),
            acc(2, "微信零钱", AccountType.WECHAT),
        )
        assertEquals(2L, PayMethodAccountMatcher.match("零钱通", null, accounts)?.id)
    }

    @Test
    fun `按类型映射命中唯一的同类型账户`() {
        // 预置账户形态：现金/支付宝/微信/银行卡各一个
        val accounts = listOf(
            acc(1, "现金", AccountType.CASH),
            acc(2, "支付宝", AccountType.ALIPAY),
            acc(3, "微信", AccountType.WECHAT),
            acc(4, "银行卡", AccountType.BANK),
        )
        assertEquals(3L, PayMethodAccountMatcher.match("零钱", null, accounts)?.id)
        assertEquals(2L, PayMethodAccountMatcher.match("花呗", null, accounts)?.id)
        assertEquals(2L, PayMethodAccountMatcher.match("余额宝", null, accounts)?.id)
        assertEquals(4L, PayMethodAccountMatcher.match("储蓄卡", null, accounts)?.id)
    }

    @Test
    fun `同类型多个账户时放弃匹配宁落默认`() {
        val accounts = listOf(
            acc(1, "招商银行(1234)", AccountType.BANK),
            acc(2, "建设银行(5678)", AccountType.BANK),
        )
        assertNull(PayMethodAccountMatcher.match("银行卡", null, accounts))
    }

    @Test
    fun `无任何支付方式信号返回null`() {
        val accounts = listOf(acc(1, "现金", AccountType.CASH))
        assertNull(PayMethodAccountMatcher.match(null, null, accounts))
    }
}
