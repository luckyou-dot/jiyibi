package com.jiyibi.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MerchantCategoryRepository.normalizeMerchant] 的归一化测试。
 *
 * 这个函数是「商户→分类」学习表的 key 生成器：归一化不彻底，同一个商户会以
 * 「星巴克」「星巴克 」「星巴克 35.00」等多种形态各占一条，学习表很快被重复项填满
 * （上限 200 条，FIFO 淘汰意味着真实条目会被挤掉）。
 *
 * 用例只覆盖当前实现承诺的行为，不覆盖已知不完美的边界（例如「7-11」这类
 * 以数字结尾的商号会把尾部数字当金额剥掉）。
 */
class MerchantCategoryRepositoryTest {

    @Test
    fun `去掉首尾空白`() {
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("  星巴克  "))
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("\t星巴克\n"))
    }

    @Test
    fun `去掉尾部金额片段`() {
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("星巴克 35.00"))
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("星巴克￥35"))
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("星巴克 35 元"))
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("星巴克¥35.5元"))
    }

    @Test
    fun `去掉尾部标点`() {
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("星巴克，"))
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("星巴克。"))
        assertEquals("星巴克", MerchantCategoryRepository.normalizeMerchant("星巴克："))
    }

    @Test
    fun `统一小写以防同一商户分裂成两条`() {
        assertEquals("starbucks", MerchantCategoryRepository.normalizeMerchant("Starbucks"))
        assertEquals(
            MerchantCategoryRepository.normalizeMerchant("STARBUCKS"),
            MerchantCategoryRepository.normalizeMerchant("starbucks "),
        )
    }

    @Test
    fun `空与纯空白输入返回空串`() {
        assertEquals("", MerchantCategoryRepository.normalizeMerchant(""))
        assertEquals("", MerchantCategoryRepository.normalizeMerchant("   "))
    }

    @Test
    fun `不含金额的正常商户名保持原样`() {
        assertEquals("美团外卖", MerchantCategoryRepository.normalizeMerchant("美团外卖"))
        assertEquals("肯德基(中关村店)", MerchantCategoryRepository.normalizeMerchant("肯德基(中关村店)"))
    }
}
