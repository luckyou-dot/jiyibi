package com.jiyibi.app.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AutoRecordHealth] 的自检判定测试。
 *
 * 这层纯逻辑决定了「自检卡显示几项待处理」「要不要弹『自动记账已失效』的强提示」
 * 「后台巡检要不要发通知」，因此把边界钉住：**电池优化与通知权限属于体验项，
 * 不该被算成"记账失效"**（否则用户会被反复提醒一堆不影响记账的设置）。
 */
class AutoRecordHealthTest {

    @Test
    fun `全部就绪时没有任何缺项`() {
        val health = health()
        assertEquals(emptyList<AutoRecordIssue>(), health.issues())
        assertTrue(health.channelsReady)
        assertTrue(health.anyChannelReady)
    }

    @Test
    fun `两条抓取通道都断时才算完全失效`() {
        val broken = health(notificationAccess = false, accessibility = false)
        assertFalse(broken.anyChannelReady)
        assertFalse(broken.channelsReady)

        // 只断一条：仍能记账，只是漏掉部分场景
        val halfBroken = health(accessibility = false)
        assertTrue("只剩通知通道也应能记账", halfBroken.anyChannelReady)
        assertFalse("但不算两条通道都就绪", halfBroken.channelsReady)
    }

    @Test
    fun `缺项顺序固定为先记账后体验`() {
        val health = health(
            notificationAccess = false,
            accessibility = false,
            notificationsAllowed = false,
            batteryUnrestricted = false,
        )
        assertEquals(
            listOf(
                AutoRecordIssue.NOTIFICATION_ACCESS,
                AutoRecordIssue.ACCESSIBILITY,
                AutoRecordIssue.NOTIFICATIONS_PERMISSION,
                AutoRecordIssue.BATTERY_OPTIMIZATION,
            ),
            health.issues(),
        )
    }

    @Test
    fun `captureIssues 只包含影响记账的两项`() {
        // 通知权限与电池优化缺失：只影响提醒与后台存活，不影响"能不能自动记账"
        val experienceOnly = health(notificationsAllowed = false, batteryUnrestricted = false)
        assertEquals(emptyList<AutoRecordIssue>(), experienceOnly.captureIssues())
        assertEquals(2, experienceOnly.issues().size)

        val capture = health(notificationAccess = false)
        assertEquals(listOf(AutoRecordIssue.NOTIFICATION_ACCESS), capture.captureIssues())
    }

    /**
     * 回归：**权限被开回来之后，那条「自动记账已失效」提醒必须撤得掉**。
     *
     * 真机踩过的坑：撤销此前只挂在后台巡检上，而 `checkNow()` 的 WorkManager 一次性任务
     * 会被系统推迟（实测下次执行在数小时后）。于是用户照引导把无障碍开回来、自检卡已全绿，
     * 通知栏那条失效提醒还挂着，看起来像没修好。
     * 所以「用户看得见自检结果的时刻」要能凭这个判定当场撤销。
     */
    @Test
    fun `恢复健康后应撤销失效提醒`() {
        assertTrue("两条通道都在，旧的失效提醒该撤", health().shouldClearAlert())

        // 只缺体验项（通知权限 / 电池白名单）不影响记账，同样该撤
        assertTrue(health(notificationsAllowed = false, batteryUnrestricted = false).shouldClearAlert())
    }

    @Test
    fun `任一抓取通道仍断着就不该撤提醒`() {
        assertFalse(health(accessibility = false).shouldClearAlert())
        assertFalse(health(notificationAccess = false).shouldClearAlert())
        assertFalse(
            health(notificationAccess = false, accessibility = false).shouldClearAlert(),
        )
    }

    private fun health(
        notificationAccess: Boolean = true,
        accessibility: Boolean = true,
        notificationsAllowed: Boolean = true,
        batteryUnrestricted: Boolean = true,
    ): AutoRecordHealth = AutoRecordHealth(
        notificationAccess = notificationAccess,
        accessibility = accessibility,
        notificationsAllowed = notificationsAllowed,
        batteryUnrestricted = batteryUnrestricted,
    )
}
