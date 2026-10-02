package com.jiyibi.app.core.notify

import com.jiyibi.app.core.notify.PaymentWindowHints.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 支付窗口类名分类回归测试。
 *
 * fixture 是微信 / 支付宝支付链路在真机上的真实 Activity 类名
 * （无障碍事件里 className 的原样值，含 2026-10 真机观测到的
 * 混淆类名 dialog.k2 与通用容器 UIPageFragmentActivity）。
 * 窗口类名随 App 版本漂移，改规则前先跑这里，防止把已覆盖的链路改丢。
 */
class PaymentWindowHintsTest {

    // ---------- PAYMENT：疑似支付窗口 ----------

    @Test
    fun `微信扫商户码付款页`() {
        assertEquals(
            Kind.PAYMENT,
            PaymentWindowHints.classify("com.tencent.mm.plugin.offline.ui.OfflineScanPayUI"),
        )
    }

    @Test
    fun `微信向个人付款页`() {
        // 真机观测（2026-10）：扫个人收款码的金额输入页
        assertEquals(
            Kind.PAYMENT,
            PaymentWindowHints.classify("com.tencent.mm.plugin.remittance.ui.RemittanceBusiUI"),
        )
    }

    @Test
    fun `微信向个人付款页旧类名`() {
        assertEquals(
            Kind.PAYMENT,
            PaymentWindowHints.classify("com.tencent.mm.plugin.remittance.ui.RemittanceUI"),
        )
    }

    @Test
    fun `微信转账页`() {
        assertEquals(
            Kind.PAYMENT,
            PaymentWindowHints.classify("com.tencent.mm.plugin.trans.ui.TransUI"),
        )
    }

    @Test
    fun `微信内嵌支付网页`() {
        assertEquals(
            Kind.PAYMENT,
            PaymentWindowHints.classify("com.tencent.mm.plugin.webview.ui.tools.WebViewUI"),
        )
    }

    @Test
    fun `支付宝收银台`() {
        assertEquals(
            Kind.PAYMENT,
            PaymentWindowHints.classify("com.eg.android.AlipayGphone.MspContainerActivity"),
        )
    }

    @Test
    fun `支付宝收款码页`() {
        assertEquals(
            Kind.PAYMENT,
            PaymentWindowHints.classify("com.alipay.mobile.payee.ui.PayeeActivity"),
        )
    }

    // ---------- NEUTRAL：支付流程经过的弹窗 / 容器（不清零放行窗口） ----------

    @Test
    fun `微信支付密码弹窗混淆类名`() {
        // 真机观测：密码弹窗类名被混淆成 dialog.k2，无任何支付语义。
        // 曾因「未命中白名单即清零」掐断链路，导致支付成功页读不到
        assertEquals(
            Kind.NEUTRAL,
            PaymentWindowHints.classify("com.tencent.mm.ui.widget.dialog.k2"),
        )
    }

    @Test
    fun `微信支付成功页通用容器`() {
        // 真机观测：向个人付款的支付成功页跑在通用容器 UIPageFragmentActivity 里
        assertEquals(
            Kind.NEUTRAL,
            PaymentWindowHints.classify("com.tencent.mm.framework.app.UIPageFragmentActivity"),
        )
    }

    @Test
    fun `空类名归中性`() {
        assertEquals(Kind.NEUTRAL, PaymentWindowHints.classify(""))
    }

    // ---------- UNRELATED：明确无关页（清零放行窗口） ----------

    @Test
    fun `微信聊天主页`() {
        assertEquals(
            Kind.UNRELATED,
            PaymentWindowHints.classify("com.tencent.mm.ui.LauncherUI"),
        )
    }

    @Test
    fun `支付宝主页`() {
        // AlipayLogin 剔除品牌词后只剩 "Login"：既清零窗口，又不会因含 pay 误命中
        assertEquals(
            Kind.UNRELATED,
            PaymentWindowHints.classify("com.eg.android.AlipayGphone.AlipayLogin"),
        )
    }

    @Test
    fun `支付宝新版主页`() {
        assertEquals(
            Kind.UNRELATED,
            PaymentWindowHints.classify("com.alipay.mobile.quinox.LauncherActivity"),
        )
    }

    @Test
    fun `微信扫一扫取景页`() {
        // 真机观测：扫一扫入口，尚未进入付款流程
        assertEquals(
            Kind.UNRELATED,
            PaymentWindowHints.classify("com.tencent.mm.plugin.scanner.ui.BaseScanUI"),
        )
    }

    @Test
    fun `微信小程序不命中支付`() {
        // 刻意不放行：小程序内自定义控件渲染金额，误读风险大于漏记
        assertEquals(
            Kind.NEUTRAL,
            PaymentWindowHints.classify("com.tencent.mm.plugin.appbrand.ui.AppBrandUI"),
        )
    }

    @Test
    fun `支付宝账单列表`() {
        // 账单页满是「历史支付金额」，不放行读屏，防止浏览记录被误记成账
        assertEquals(
            Kind.NEUTRAL,
            PaymentWindowHints.classify("com.alipay.mobile.bill.billlist.BillListActivity"),
        )
    }
}
