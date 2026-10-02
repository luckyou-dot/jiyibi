package com.jiyibi.app.core.common

import android.content.Context

/**
 * 运行时读取应用版本号。
 *
 * **为什么不用 BuildConfig.VERSION_NAME**：它是编译期常量，Kotlin 会把值
 * 直接内联进调用处的字节码——只要那个调用方源文件之后没有被改动，
 * 增量编译就一直复用旧产物，UI 会永远显示编译时的旧版本号，哪怕
 * manifest（随 gradle 每次构建刷新）已经是新的。
 * 真机事故（2026-10）：包管理器显示 1.16，界面却显示 v1.05——两个
 * 版本页自 7 月（当时工作区版本 1.05，未提交故 git 历史里查无此版本）
 * 之后从未重编。
 *
 * PackageManager 读的是**已安装包的 manifest**，与系统设置、包管理器
 * 同源，永远一致，对任何编译缓存免疫。
 */
object AppVersion {

    /** 版本名（如 "1.17"）；读取失败返回空串（调用方自行决定展示形态） */
    fun name(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
