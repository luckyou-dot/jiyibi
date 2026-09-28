package com.jiyibi.app.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [yuanToCents] 的边界测试。
 *
 * 这个函数被「记一笔」「账户余额」「预算」「借据」「周期规则」「搜索金额筛选」等处直接调用，
 * 旧实现用 `longValueExact()`，输入三位小数（12.345）就抛 ArithmeticException —— 点保存闪退。
 * 因此这里把「任何输入都不抛异常」本身当作被测行为。
 */
class MoneyConversionTest {

    @Test
    fun `常规金额精确到分`() {
        assertEquals(0L, 0.0.yuanToCents())
        assertEquals(1234L, 12.34.yuanToCents())
        assertEquals(100000L, 1000.0.yuanToCents())
        assertEquals(-1234L, (-12.34).yuanToCents())
    }

    @Test
    fun `浮点表示误差不串位`() {
        // 0.1 + 0.2 在 Double 里是 0.30000000000000004，不能记成 30.000000000000004 分
        assertEquals(30L, (0.1 + 0.2).yuanToCents())
        assertEquals(1L, 0.005.yuanToCents())
        assertEquals(8L, 0.075.yuanToCents())
    }

    @Test
    fun `三位小数四舍五入而不是抛异常`() {
        assertEquals(1235L, 12.345.yuanToCents())
        assertEquals(1234L, 12.344.yuanToCents())
        assertEquals(-1235L, (-12.345).yuanToCents())
        // 这一笔在旧实现里会直接抛 ArithmeticException；0.001 元 = 0.1 分，四舍五入后是 0 分
        assertEquals(0L, 0.001.yuanToCents())
        assertEquals(1L, 0.006.yuanToCents())
    }

    @Test
    fun `非法与溢出输入被兜住`() {
        assertEquals(0L, Double.NaN.yuanToCents())
        assertEquals(0L, Double.POSITIVE_INFINITY.yuanToCents())
        assertEquals(0L, Double.NEGATIVE_INFINITY.yuanToCents())
        assertEquals(MAX_AMOUNT_CENTS, 1e18.yuanToCents())
        assertEquals(-MAX_AMOUNT_CENTS, (-1e18).yuanToCents())
        // 长按粘贴出一长串 9，也不能写出天文数字
        assertEquals(MAX_AMOUNT_CENTS, 99999999999999.99.yuanToCents())
    }

    @Test
    fun `分转元再转回分保持不变`() {
        val cents = 123456L
        assertEquals(cents, cents.centsToYuan().toDouble().yuanToCents())
    }
}
