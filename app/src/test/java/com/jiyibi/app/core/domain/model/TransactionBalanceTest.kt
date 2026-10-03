package com.jiyibi.app.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Transaction.balanceDeltas] / [Transaction.reversedBalanceDeltas] 的规则测试。
 *
 * 这是「一笔交易如何改变账户余额」的**唯一权威定义**：账户余额 = 初始余额 + 所有交易影响之和。
 * 历史上这段规则在 4 个地方各写了一遍 `when (type)`，其中首页与搜索页的删除干脆漏了回滚，
 * 导致余额与流水脱节。用例把三种交易类型与「取反」钉住，防止再写歪。
 */
class TransactionBalanceTest {

    @Test
    fun `支出从付款账户扣减`() {
        val tx = tx(type = TransactionType.EXPENSE, amount = 1_234, accountId = 7)
        assertEquals(listOf(AccountBalanceDelta(7, -1_234)), tx.balanceDeltas())
    }

    @Test
    fun `收入加到收款账户`() {
        val tx = tx(type = TransactionType.INCOME, amount = 5_000, accountId = 7)
        assertEquals(listOf(AccountBalanceDelta(7, 5_000)), tx.balanceDeltas())
    }

    @Test
    fun `转账同时动两个账户`() {
        val tx = tx(type = TransactionType.TRANSFER, amount = 300, accountId = 1, toAccountId = 2)
        assertEquals(
            listOf(AccountBalanceDelta(1, -300), AccountBalanceDelta(2, 300)),
            tx.balanceDeltas(),
        )
    }

    @Test
    fun `转账缺目标账户时只动出账方而不抛异常`() {
        val tx = tx(type = TransactionType.TRANSFER, amount = 300, accountId = 1, toAccountId = null)
        assertEquals(listOf(AccountBalanceDelta(1, -300)), tx.balanceDeltas())
    }

    @Test
    fun `取反后每个账户的增减都反向且金额不变`() {
        TransactionType.entries.forEach { type ->
            val tx = tx(type = type, amount = 880, accountId = 1, toAccountId = 2)
            val applied = tx.balanceDeltas()
            val reversed = tx.reversedBalanceDeltas()

            assertEquals("账户数应一致（$type）", applied.size, reversed.size)
            applied.zip(reversed).forEach { (a, r) ->
                assertEquals("账户 id 不应变（$type）", a.accountId, r.accountId)
                assertEquals("增减额应取反（$type）", -a.delta, r.delta)
            }
        }
    }

    @Test
    fun `先应用再撤销余额回到原点`() {
        TransactionType.entries.forEach { type ->
            val tx = tx(type = type, amount = 1_999, accountId = 1, toAccountId = 2)
            val net = (tx.balanceDeltas() + tx.reversedBalanceDeltas())
                .groupBy { it.accountId }
                .mapValues { (_, deltas) -> deltas.sumOf { it.delta } }

            net.forEach { (accountId, sum) ->
                assertEquals("账户 $accountId 的净变化应为 0（$type）", 0L, sum)
            }
        }
    }

    private fun tx(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        toAccountId: Long? = null,
    ): Transaction = Transaction(
        type = type,
        amount = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        date = 0L,
    )
}
