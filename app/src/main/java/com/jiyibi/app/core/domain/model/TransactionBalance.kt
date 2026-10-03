package com.jiyibi.app.core.domain.model

/**
 * 一笔交易造成的单个账户余额变动（单位：分，正数为增加、负数为减少）。
 *
 * @property accountId 受影响的账户
 * @property delta     余额增减额（分）
 */
data class AccountBalanceDelta(
    val accountId: Long,
    val delta: Long,
)

/**
 * 这笔交易对账户余额的**全部**影响。
 *
 * 规则（唯一权威定义，命中前请勿在各处再写一遍 `when (type)`）：
 * - 支出：出账账户 -amount
 * - 收入：入账账户 +amount
 * - 转账：出账账户 -amount，入账账户 +amount（两个账户都动）
 *
 * 转账的 `toAccountId` 为空（脏数据 / 历史数据）时只回退出账方，不抛异常——
 * 余额少回滚一边只是数字不准，抛异常会让用户连删除都做不了。
 */
fun Transaction.balanceDeltas(): List<AccountBalanceDelta> = when (type) {
    TransactionType.EXPENSE -> listOf(AccountBalanceDelta(accountId, -amount))
    TransactionType.INCOME -> listOf(AccountBalanceDelta(accountId, amount))
    TransactionType.TRANSFER -> buildList {
        add(AccountBalanceDelta(accountId, -amount))
        toAccountId?.let { add(AccountBalanceDelta(it, amount)) }
    }
}

/**
 * 撤销这笔交易的影响：每个 [AccountBalanceDelta] 取反。
 *
 * 删除交易、以及编辑交易时「先撤旧值再应用新值」都走这里，
 * 保证撤销永远等于「原影响的相反数」而不会各写一套写歪。
 */
fun Transaction.reversedBalanceDeltas(): List<AccountBalanceDelta> =
    balanceDeltas().map { it.copy(delta = -it.delta) }
