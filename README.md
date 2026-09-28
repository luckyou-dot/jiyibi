# 记一笔 (JiYiBi)

一款简洁优雅的 Android 个人记账应用，基于 Kotlin + Jetpack Compose 构建，采用 Material Design 3 设计语言，支持多种记账场景和丰富的数据可视化。

## 功能特性
    
### 核心记账
- **收支记录** — 支持支出、收入、转账三种交易类型
- **分类管理** — 自定义收支分类，支持自定义图标和颜色
- **账户管理** — 现金、银行卡、支付宝、微信、信用卡等多种账户类型
- **标签系统** — 为交易添加自定义标签，便于分类检索

### 智能功能
- **OCR 小票识别** — 基于 ML Kit 中文文字识别，拍照自动提取金额
- **周期性记账** — 设置每日/每周/每月/每年自动记录规则
- **智能通知解析** — 自动识别微信、支付宝支付通知，入库后弹横幅提醒，未识别文案可在应用内查看

### 数据可视化
- **日历热力图** — 按月展示每日支出强度，颜色深浅直观反映消费情况
- **统计图表** — 日/周/月/年多维度统计，分类占比、账户占比、趋势折线图
- **预算追踪** — 实时预算进度，超支提醒
- **年度回顾** — 年度消费总结与回顾

### 数据管理
- **备份导出** — 支持 CSV 格式数据导出，UTF-8 BOM 确保 Excel 兼容
- **搜索功能** — 全文搜索交易记录
- **借贷追踪** — 记录借贷往来，支持还款管理

### 个性化
- **多主题切换** — 薄荷绿、活力紫、日落橙、莫兰迪灰 4 种主题风格
- **渐变 Hero 设计** — 首页、统计、我的页面采用渐变背景 + 毛玻璃卡片
- **流畅动画** — 数字滚动、进度条动效、列表入场动画、页面转场

## 技术栈

| 分类 | 技术 |
|------|------|
| **语言** | Kotlin |
| **UI 框架** | Jetpack Compose + Material 3 |
| **架构模式** | MVVM + Clean Architecture |
| **依赖注入** | Hilt + KSP |
| **本地数据库** | Room (v2) |
| **异步处理** | Kotlin Coroutines + Flow |
| **后台任务** | WorkManager |
| **OCR 识别** | ML Kit Chinese Text Recognition |
| **图表库** | Vico Charts |
| **图片加载** | Coil |
| **桌面小组件** | Glance |
| **数据存储** | DataStore Preferences |
| **导航** | Navigation Compose |

## 项目结构

```
app/src/main/java/com/jiyibi/app/
├── core/                          # 核心业务层
│   ├── backup/                    # 数据备份导出
│   ├── common/                    # 通用工具（TimeRange）
│   ├── data/                      # 数据层
│   │   ├── repository/            # Repository 实现
│   │   └── Mapper.kt              # Entity ↔ Domain 映射
│   ├── database/                  # Room 数据库
│   │   ├── dao/                   # 数据访问对象
│   │   └── entity/                # 数据库实体
│   ├── designsystem/              # 设计系统
│   │   ├── component/             # 统一组件（GlassCard, AnimatedNumber 等）
│   │   └── theme/                 # 主题、颜色、字体
│   ├── domain/                    # 领域层
│   │   ├── model/                 # 领域模型 & 枚举
│   │   └── repository/            # Repository 接口
│   ├── ml/                        # 机器学习（OCR）
│   └── work/                      # WorkManager 任务
├── di/                            # Hilt 依赖注入模块
├── nav/                           # 导航路由定义
└── ui/                            # 界面层
    ├── account/                   # 账户管理
    ├── backup/                    # 备份导出
    ├── budget/                    # 预算管理
    ├── category/                  # 分类管理
    ├── debt/                      # 借贷记录
    ├── home/                      # 首页
    ├── recurring/                 # 周期性记账
    ├── search/                    # 搜索
    ├── settings/                  # 设置 & 个人中心
    ├── statistics/                # 统计分析
    ├── tag/                       # 标签管理
    ├── transaction/               # 交易编辑
    └── yearreview/                # 年度回顾
```

## 数据库设计

应用使用 Room 数据库（版本 2），包含 6 张核心表：

| 表名 | 说明 |
|------|------|
| `transactions` | 交易记录（金额以分存储） |
| `accounts` | 账户信息 |
| `categories` | 收支分类 |
| `budgets` | 预算设置 |
| `recurring_rules` | 周期性记账规则 |
| `debts` | 借贷记录 |

金额统一以「分」（Long 类型）存储，通过 `centsToYuan()` / `yuanToCents()` 进行元分转换，避免浮点精度问题。

## 导航结构

应用采用底部导航栏 + 二级页面的经典结构：

**底部 Tab（4个）：**
- 首页 — 日历热力图、最近交易、预算进度
- 统计 — 日/周/月/年多维度数据分析
- 预算 — 预算设置与进度追踪
- 我的 — 资产看板、功能入口、主题设置

**二级页面（9个）：**
交易编辑、搜索、分类管理、账户管理、周期记账、借贷记录、标签管理、备份导出、年度回顾

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- 最低支持 Android 8.0 (API 26)

## 构建与运行

```bash
# 克隆项目
git clone https://github.com/luckyou-dot/-.git

# 使用 Android Studio 打开项目
# 或命令行构建
./gradlew assembleDebug
```

## 主要依赖版本

| 依赖 | 版本 |
|------|------|
| Kotlin | 2.0.0 |
| Compose BOM | 2024.06.00 |
| Room | 2.6.1 |
| Hilt | 2.51.1 |
| AGP | 8.13.2 |
| KSP | 2.0.0-1.0.21 |
| ML Kit Text | 16.0.0 |
| Vico Charts | 2.0.0-alpha.21 |
| Glance | 1.1.0 |

## 设计亮点

- **渐变 Hero 区域** — 首页、统计、我的页面顶部采用主题渐变背景，搭配毛玻璃（GlassCard）汇总卡片
- **AnimatedNumber** — 金额数字采用等宽字体 + 滚动动效，视觉流畅
- **热力图日历** — 7 列网格按周排列，5 档主题色梯度反映支出强度
- **贝塞尔曲线趋势图** — 平滑折线 + 渐变填充 + 光晕数据点，支持入场动画
- **滑动删除** — 最近交易列表支持左滑删除

## 许可证

本项目为个人学习项目。
