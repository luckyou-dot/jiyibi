package com.jiyibi.app.core.notify

import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.AccountType

/**
 * 「支付方式 → 记账账户」匹配器（纯函数，便于单测）。
 *
 * 微信 / 支付宝的通知与支付页文案常写明资金出处（零钱、花呗、储蓄卡(尾号)），
 * 用它选账户比「一律记到默认账户」更贴近事实——否则用花呗付款会错扣现金余额。
 *
 * ## 匹配优先级（置信度从高到低）
 * 1. **卡尾号**：文案里的 4 位尾号命中账户名（如用户自建的「招商银行(1234)」）——精确；
 * 2. **账户名包含支付方式**：「花呗」「零钱通」等直接对上同/近名账户；
 * 3. **零钱通退化为零钱**：没建「零钱通」账户时，含「零钱」的账户可承接；
 * 4. **按账户类型映射**：零钱/零钱通→微信、余额/余额宝/花呗→支付宝、
 *    银行卡/储蓄卡→银行卡、信用卡→信用卡。**同类型账户只有一个时才用**，
 *    有多个（用户建了两张银行卡）时无法区分，宁可落默认账户也不错配。
 *
 * 全部落空返回 null，调用方退回「默认账户」逻辑，行为与未上本功能时一致。
 */
object PayMethodAccountMatcher {

    /** 返回匹配置信度最高的账户；无可靠匹配返回 null */
    fun match(payChannel: String?, cardTail: String?, accounts: List<Account>): Account? {
        if (payChannel == null && cardTail == null) return null

        // 1. 卡尾号精确命中账户名
        if (cardTail != null) {
            accounts.firstOrNull { it.name.contains(cardTail) }?.let { return it }
        }

        val channel = payChannel ?: return null

        // 2. 账户名包含支付方式（用户自建的「花呗」「零钱通」等账户优先精确对上）
        accounts.firstOrNull { it.name.contains(channel) }?.let { return it }

        // 3. 零钱通退化：未单独建「零钱通」账户时，含「零钱」的账户承接
        if (channel == "零钱通") {
            accounts.firstOrNull { it.name.contains("零钱") }?.let { return it }
        }

        // 4. 按账户类型映射；同类型多个时放弃（无法区分，宁落默认也不错配）
        val wantedType = when (channel) {
            "零钱", "零钱通" -> AccountType.WECHAT
            "余额", "余额宝", "花呗" -> AccountType.ALIPAY
            "银行卡", "储蓄卡" -> AccountType.BANK
            "信用卡" -> AccountType.CREDIT_CARD
            else -> return null
        }
        return accounts.filter { it.type == wantedType }.takeIf { it.size == 1 }?.first()
    }
}
