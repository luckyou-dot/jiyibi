package com.jiyibi.app.core.common

import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [buildDayGroups] / [startOfDay] 的分组与合计语义测试。
 *
 * 这两个函数决定首页「最近交易」与搜索页「查看全部」列表的分天展示，
 * 有两个容易写错、且只在真机上暴露的点，这里各用一组用例钉住：
 * 1. 分组 key 必须是**本地** 0 点。用 `date / 86400000 * 86400000` 按 UTC 切分，
 *    在东八区会把当天 0:00~8:00 的账算到前一天。
 * 2. 出/入合计必须**排除转账**——转账只是账户间挪动，不是真实收支。
 *
 * 用例显式把默认时区固定为 Asia/Shanghai（东八区），否则结果会随跑测试的机器时区变化。
 */
class TransactionGroupingTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun fixTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `同一本地日的凌晨与深夜归入同一组`() {
        val morning = millisAt(2026, Calendar.MARCH, 8, 0, 30)
        val night = millisAt(2026, Calendar.MARCH, 8, 23, 30)

        val groups = buildDayGroups(
            listOf(
                row(date = morning, amount = 100),
                row(date = night, amount = 200),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().rows.size)
        assertEquals(300L, groups.single().expenseTotal)
    }

    @Test
    fun `分组键是本地零点而不是 UTC 零点`() {
        val morning = millisAt(2026, Calendar.MARCH, 8, 0, 30)

        // 东八区的本地 0 点与 UTC 切分（date / 86400000 * 86400000）相差 8 小时
        val utcBucketed = morning / MILLIS_PER_DAY * MILLIS_PER_DAY
        assertTrue(
            "用例前提：该时刻的本地 0 点与 UTC 0 点不同，否则这条测试证明不了任何事",
            startOfDay(morning) != utcBucketed,
        )

        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(morning) }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
        assertTrue("本地 0 点必然不晚于该时刻", startOfDay(morning) <= morning)
    }

    @Test
    fun `出人合计排除转账`() {
        val day = millisAt(2026, Calendar.MARCH, 8, 12, 0)
        val groups = buildDayGroups(
            listOf(
                row(date = day, type = TransactionType.EXPENSE, amount = 1_000),
                row(date = day, type = TransactionType.INCOME, amount = 2_000),
                // 转账只改账户余额，两边合计都不该算进去
                row(date = day, type = TransactionType.TRANSFER, amount = 5_000),
            ),
        )

        val group = groups.single()
        assertEquals(1_000L, group.expenseTotal)
        assertEquals(2_000L, group.incomeTotal)
        assertEquals(3, group.rows.size)
    }

    @Test
    fun `天按倒序、组内按时间倒序`() {
        val day7 = millisAt(2026, Calendar.MARCH, 7, 9, 0)
        val day8Early = millisAt(2026, Calendar.MARCH, 8, 9, 0)
        val day8Late = millisAt(2026, Calendar.MARCH, 8, 20, 0)

        val groups = buildDayGroups(
            listOf(
                row(date = day7, amount = 1),
                row(date = day8Late, amount = 2),
                row(date = day8Early, amount = 3),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(startOfDay(day8Early), groups[0].dayStart)
        assertEquals(startOfDay(day7), groups[1].dayStart)
        assertEquals(listOf(2L, 3L), groups[0].rows.map { it.tx.amount })
        assertEquals(listOf(1L), groups[1].rows.map { it.tx.amount })
    }

    @Test
    fun `空列表返回空分组`() {
        assertEquals(emptyList<TransactionDayGroup>(), buildDayGroups(emptyList()))
    }

    // ------------------------------------------------------------------
    // 构造辅助
    // ------------------------------------------------------------------

    private fun row(
        date: Long,
        amount: Long = 0,
        type: TransactionType = TransactionType.EXPENSE,
    ): TransactionRowUi = TransactionRowUi(
        tx = Transaction(type = type, amount = amount, accountId = 1L, date = date),
        category = null,
    )

    private fun millisAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
