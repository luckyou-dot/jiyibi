# 记一笔 (JiYiBi)

一款简洁优雅的 Android 个人记账应用，基于 Kotlin + Jetpack Compose 构建，采用 Material Design 3 设计语言，支持多种记账场景、自动记账（支付通知 / 无障碍读屏 / AI 兜底）与丰富的数据可视化。

> 本文档描述的是**当前代码真实实现**的功能。历史遗留的夸大描述（小票 OCR、桌面小组件、Vico 图表库、Coil 图片加载）已在 2026-10 重构中一并修正，详见 [重构记录.md](重构记录.md)。

## 功能特性

### 核心记账
- **收支记录** — 支持支出、收入、转账三种交易类型
- **分类管理** — 自定义收支分类，支持自定义图标和颜色
- **账户管理** — 现金、银行卡、支付宝、微信、信用卡等多种账户类型，支持默认收支账户
- **标签系统** — 为交易添加自定义标签，便于分类检索

### 自动记账
- **支付通知解析** — `NotificationListenerService` 监听微信 / 支付宝支付通知，本地规则表（包名白名单 + 有序规则 + 三段式金额提取）解析后自动入库
- **无障碍读屏补足** — `AccessibilityService` 只在微信 / 支付宝的**支付成功页面**读取屏幕文字，补上「用户正在前台付款时系统不发通知」的盲区
- **AI 兜底识别** — 本地规则啃不动的文案交给 OpenAI 兼容接口（默认地址可改，Key 由用户自己填），规则永远优先，AI 失败即放弃、不影响记账主流程
- **AI 内容审核** — 无障碍读屏读到的是**整屏窗口文字**（用户可能停在聊天/商品页），本地规则只能判断"屏里出现了支付语义词"。审核环节让模型复核这一屏是否确为一笔已完成的收付款，并给出干净备注；审核不通过的原文进未识别队列，不会静默丢弃
- **备注只写这一笔** — 商户名优先；没有商户名时以支付语义词为锚点只截前后各一个词，**不会把整屏窗口文字写进备注**（AI 审核可用时用模型给出的短备注）
- **分类学习** — 用户手动纠正自动记账的分类后记住「商户 → 分类」，下次同商户直接采用，越用越准（可一键重置）
- **未识别队列** — 没解析出来的支付通知原文留在应用内可见，便于按真机文案补规则，而不是静默丢弃
- **横幅提醒** — 自动记账成功 / 需要人工确认时弹横幅通知

### 数据可视化
- **日历热力图** — 按月展示每日支出强度，5 档主题色梯度
- **统计图表** — 日 / 周 / 月 / 年多维度统计，分类占比、账户占比、趋势折线（**手写 Canvas 绘制**，未使用图表库）
- **预算追踪** — 支持月度总预算与分类预算，实时进度与超支提醒（本月默认预置 ¥1200 总预算）
- **年度回顾** — 年度消费总结，支持导出分享海报（Canvas 出图，走 FileProvider 分享）

### 数据管理
- **备份导出** — CSV 导出（带 UTF-8 BOM，Excel 直接打开不乱码），敏感配置不参与导出
- **备份恢复** — JSON 全量备份与恢复（恢复前二次确认，覆盖前有警告）
- **搜索** — 关键词、日期范围、金额区间、收支类型、分类多条件组合筛选

### 个性化
- **多主题切换** — 薄荷绿、活力紫、日落橙、莫兰迪灰 4 种主题风格
- **渐变 Hero 设计** — 首页、统计、我的页面采用渐变背景 + 毛玻璃卡片
- **流畅动画** — 数字滚动、进度条动效、列表入场动画（`Modifier.Node` 实现）、页面转场

## 技术栈

| 分类 | 技术 |
|------|------|
| **语言** | Kotlin 2.0.0 |
| **UI 框架** | Jetpack Compose + Material 3 |
| **架构模式** | MVVM + 分层（core/domain/data/ui） |
| **依赖注入** | Hilt + KSP |
| **本地数据库** | Room（version 3，显式 Migration） |
| **异步处理** | Kotlin Coroutines + Flow |
| **后台任务** | WorkManager（周期记账） |
| **偏好存储** | DataStore Preferences |
| **导航** | Navigation Compose |
| **网络** | OkHttp（仅 AI 兜底识别使用） |
| **图表 / 图片** | 无第三方库，全部为原生 Canvas 绘制 |

## 项目结构

```
app/src/main/java/com/jiyibi/app/
├── core/                          # 核心业务层
│   ├── ai/                        # AI 兜底识别（OpenAI 兼容接口）
│   ├── backup/                    # CSV 导出 / JSON 备份恢复
│   ├── common/                    # 通用工具（TimeRange、AppVersion、按天分组）
│   ├── data/                      # 数据层
│   │   ├── repository/            # Repository 实现 + DataStore 偏好仓库
│   │   └── Mapper.kt              # Entity ↔ Domain 映射
│   ├── database/                  # Room 数据库
│   │   ├── dao/                   # 数据访问对象
│   │   └── entity/                # 数据库实体
│   ├── designsystem/              # 设计系统
│   │   ├── component/             # 统一组件（UnifiedCard / GlassCard / 交易列表 / 动效）
│   │   └── theme/                 # 主题、颜色、字体
│   ├── domain/                    # 领域层
│   │   ├── model/                 # 领域模型 & 枚举 & 金额换算
│   │   └── repository/            # Repository 接口
│   ├── notify/                    # 自动记账：通知监听 / 无障碍服务 / 规则解析 / 落库
│   └── work/                      # WorkManager 周期记账任务
├── di/                            # Hilt 依赖注入模块
├── nav/                           # 导航路由定义与 NavHost
└── ui/                            # 界面层
    ├── account/                   # 账户管理
    ├── autorecord/                # 自动记账（授权状态 / 开关 / 学习表 / AI 配置）
    ├── backup/                    # 备份导出
    ├── budget/                    # 预算管理
    ├── category/                  # 分类管理
    ├── debt/                      # 借贷记录
    ├── home/                      # 首页
    ├── recurring/                 # 周期性记账
    ├── search/                    # 搜索
    ├── settings/                  # 设置 & 个人中心 & 关于 & 反馈
    ├── statistics/                # 统计分析（含年度回顾入口）
    ├── tag/                       # 标签管理
    ├── transaction/               # 交易编辑
    └── yearreview/                # 年度回顾（含海报出图与分享）
```

## 数据库设计

Room 数据库当前 **version 3**，包含 6 张表：

| 表名 | 说明 |
|------|------|
| `transactions` | 交易记录（金额以分存储） |
| `accounts` | 账户信息 |
| `categories` | 收支分类 |
| `budgets` | 预算设置 |
| `recurring_rules` | 周期性记账规则 |
| `debts` | 借贷记录 |

金额统一以「分」（Long 类型）存储，通过 `centsToYuan()` / `yuanToCents()` 进行元分转换，避免浮点精度问题。

### 迁移策略（重要）

本项目**刻意不使用** `fallbackToDestructiveMigration()`：那个开关一旦生效，只要版本号提升而没有配好 Migration，用户全部账目会被静默清空。

- 缺 Migration 时 Room 直接抛 `IllegalStateException`（启动失败，logcat 有明确原因）——坏在开发期比坏在用户手机上便宜；
- `DatabaseModule` 会在**每个应用版本首次打开数据库前**把 `.db` 复制到 `filesDir/db_backup/`（保留最近 5 份，含 `-wal`），迁移真写错了还能捞回来；
- schema JSON 提交在 `app/schemas/`（`2.json` / `3.json`），是写下一个 Migration 的唯一依据。

升版本三步走：改实体并提 `version` → 跑 `./gradlew :app:kspDebugKotlin` 生成新 schema → 对照差异写 `Migration(N-1, N)`，用旧版 APK 造数据覆盖安装验证。

## 导航结构

底部 Tab（4 个）：

| Tab | 内容 |
|-----|------|
| **首页** | 日历热力图、按天分组的最近交易、预算进度 |
| **统计** | 日 / 周 / 月 / 年多维度分析、分类与账户占比、年度回顾入口 |
| **预算** | 总预算与分类预算的设置与进度追踪 |
| **我的** | 资产看板、主题风格、功能入口、关于、意见反馈 |

二级页面：交易编辑、搜索、分类管理、账户管理、默认账户、周期记账、借贷记录、标签管理、自动记账、备份与恢复、预算编辑、关于应用、意见反馈、年度回顾。

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- 最低支持 Android 8.0 (API 26)

## 构建与运行

```bash
# 命令行构建 debug 包
./gradlew assembleDebug

# 单元测试（纯逻辑：解析规则、金额换算、分组、归一化等）
./gradlew :app:testDebugUnitTest

# release 包（当前复用 debug 签名，便于覆盖安装对比；正式发布前必须换成自有 keystore）
./gradlew assembleRelease
```

> **构建环境提示**：Android Studio 开着时，其 Gradle 守护进程会占用项目 `.gradle/` 下的锁文件，命令行构建可能报「拒绝访问」。绕法：`./gradlew ... --project-cache-dir="$TEMP/jiyibi-gradle-cli"`，或先关闭 Studio 的构建。

## 主要依赖版本

| 依赖 | 版本 |
|------|------|
| Kotlin | 2.0.0 |
| KSP | 2.0.0-1.0.21 |
| AGP | 8.13.2 |
| Compose BOM | 2024.06.00 |
| Room | 2.6.1 |
| Hilt | 2.51.1 |
| Navigation Compose | 2.7.7 |
| DataStore | 1.1.7 |
| WorkManager | 2.9.0 |
| OkHttp | 4.12.0 |

版本统一在 `gradle/libs.versions.toml` 管理。

## 设计亮点

- **渐变 Hero 区域** — 首页、统计、我的页面顶部采用主题渐变背景，搭配毛玻璃（GlassCard）汇总卡片
- **AnimatedNumber** — 金额数字采用等宽字体 + 滚动动效，动画期间逐帧重组范围仅限组件自身
- **热力图日历** — 7 列网格按周排列，5 档主题色梯度反映支出强度
- **贝塞尔曲线趋势图** — 平滑折线 + 渐变填充 + 光晕数据点，支持入场动画（原生 Canvas）
- **按天分组流水** — 首页与搜索页共用组件，每天一张卡，卡头显示当日出 / 入合计（转账不计入合计）
- **列表入场动画** — 用 `Modifier.Node` 实现，不阻塞列表项 skip（见性能诊断报告 P2-3）
- **滑动删除二次确认** — 左滑不直接删，弹确认框，避免误触丢数据

## 已移除 / 未实现（诚实清单）

| 项 | 状态 | 说明 |
|---|---|---|
| 小票 OCR（拍照识别金额） | **已移除** | 入口已在早期版本从记账页删除；2026-10 重构把不可达的 `ReceiptOcr` / `OcrResultHandler`、ML Kit 依赖与 CAMERA 权限一并清理 |
| 桌面小组件（Glance） | **未实现** | 从未写过 `GlanceAppWidget` 与 receiver，依赖已移除；如需实现需重新引入 Glance |
| 图表库（Vico） | **未使用** | 统计图表一直是手写 Canvas，依赖已移除 |
| 图片加载（Coil） | **未使用** | 全项目零引用，依赖已移除 |
| 二维码（ZXing） | **未使用** | 海报分享走原生 Canvas 出图，依赖已移除 |
| 支付宝 / 微信账单 CSV 导入 | **未实现** | 目前只有「导出 CSV」与「JSON 备份恢复」，没有账单文件导入 |
| 分账（AA） | **未实现** | 借贷页可手工记应收应付，但「记一笔」里拆分参与人、自动生成借据的链路只有半截代码（只有 ViewModel 方法、没有界面与调用方），2026-10 重构已删除该死代码 |
| 记账提醒 | **未实现** | 只有周期记账（WorkManager 自动写入），没有到点提醒 |

## 文档索引

| 文档 | 内容 |
|---|---|
| [README.md](README.md) | 项目总览、功能、构建方式 |
| [重构记录.md](重构记录.md) | 代码重构记录：删了什么、为什么删、如何验证、如何恢复 |
| [性能诊断报告.md](性能诊断报告.md) | 卡顿诊断与修复记录（P0/P1/P2 问题清单与状态） |

## 关于文件组织

三个页面曾经是单文件 900 行 / 37 KB 的「巨型文件」，现已按「编排留在 Screen、区块拆成组件」拆开（详见 [重构记录.md](重构记录.md)）：

| 页面 | 主文件 | 拆出的区块文件 |
|---|---|---|
| 记一笔 | `TransactionEditScreen.kt` | `TransactionEditSections.kt`（各分区卡）、`TransactionEditComponents.kt`（分类网格 / 账户选择 / 表单构造） |
| 我的 | `SettingsScreen.kt` | `SettingsHeroCards.kt`（Hero + 资产看板）、`SettingsDialogs.kt`（各类选择弹窗） |
| 统计 | `StatisticsScreen.kt` | `StatisticsCategorySection.kt`（分类占比与下钻）、`StatisticsTrendChart.kt`（趋势折线图） |

## 许可证

本项目为个人学习项目。
