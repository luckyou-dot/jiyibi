#!/usr/bin/env bash
# ============================================================================
# 自动记账不生效 · 一键诊断脚本
#
# 用法：手机连上 USB（打开 USB 调试）后，在项目根目录执行：
#   bash scripts/diagnose-auto-record.sh
#
# 可选：先做一笔真实支付（微信/支付宝扫 0.01 元），再运行本脚本，
# 日志区会显示通知是否到达、解析是否命中。
#
# 输出每一节都有 [OK] / [FAIL] / [WARN] 结论，照着 FAIL 的修就行。
# ============================================================================
set -u

# adb 兜底：PATH 里没有就用本项目 local.properties 指向的 SDK
ADB="$(command -v adb || true)"
if [ -z "$ADB" ]; then
    for candidate in \
        "/d/Tools/AndroidStdioSdk/platform-tools/adb.exe" \
        "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" \
        "/c/platform-tools/adb.exe"; do
        if [ -x "$candidate" ]; then ADB="$candidate"; break; fi
    done
fi
if [ -z "$ADB" ]; then
    echo "[FAIL] 找不到 adb，请确认 Android SDK platform-tools 已安装"
    exit 1
fi

PKG="com.jiyibi.app"
EXPECT_LISTENER="$PKG/com.jiyibi.app.core.notify.PaymentNotificationListener"
EXPECT_A11Y="$PKG/com.jiyibi.app.core.notify.PaymentAccessibilityService"

hr() { echo; echo "──────── $1 ────────"; }

"$ADB" start-server >/dev/null 2>&1
if [ -z "$("$ADB" devices | grep -w 'device')" ]; then
    echo "[FAIL] 没有已连接的设备。请插好 USB 线并在手机上允许「USB 调试」"
    "$ADB" devices -l
    exit 1
fi

hr "1. 设备与已安装版本"
"$ADB" shell getprop ro.build.version.release 2>/dev/null | xargs echo "Android 版本:"
"$ADB" shell getprop ro.product.model 2>/dev/null | xargs echo "机型:"
VER="$("$ADB" shell dumpsys package $PKG 2>/dev/null | grep -m1 'versionName')"
if [ -z "$VER" ]; then
    echo "[FAIL] 手机上没有安装 $PKG —— 先安装最新 APK 再排查"
    exit 1
fi
echo "已装版本: $VER"
echo "（当前源码是 1.08 / versionCode 9，版本落后说明装的是旧包，先重装）"

hr "2. 通知使用权（通知自动记账的前提）"
LISTENERS="$("$ADB" shell settings get secure enabled_notification_listeners 2>/dev/null | tr -d '\r')"
echo "系统已授权列表: ${LISTENERS:-<空>}"
if echo "$LISTENERS" | grep -q "$PKG"; then
    echo "[OK] 通知使用权已授权给记一笔"
else
    echo "[FAIL] 通知使用权未授权 → 手机「设置 → 通知 → 通知使用权」里打开「记一笔」"
fi

hr "3. 无障碍服务（App 内支付场景的识别通道）"
A11Y_ON="$("$ADB" shell settings get secure accessibility_enabled 2>/dev/null | tr -d '\r')"
A11Y_SVC="$("$ADB" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')"
echo "无障碍总开关: $A11Y_ON    已启用服务: ${A11Y_SVC:-<空>}"
if echo "$A11Y_SVC" | grep -q "PaymentAccessibilityService"; then
    echo "[OK] 支付页面识别服务已启用"
else
    echo "[WARN] 无障碍未开启 → 「设置 → 无障碍」里开启「记一笔 · 支付页面识别」（前台扫码/转账场景必须开这个）"
fi

hr "4. 两个系统服务是否活着"
SVC="$("$ADB" shell dumpsys activity services $PKG 2>/dev/null)"
if echo "$SVC" | grep -q "PaymentNotificationListener"; then
    echo "[OK] 通知监听服务正在运行"
else
    echo "[FAIL] 通知监听服务未在运行（被 ROM 杀了或未授权）→ 给记一笔加后台锁/允许自启动后重启 App"
fi
if echo "$SVC" | grep -q "PaymentAccessibilityService"; then
    echo "[OK] 无障碍服务正在运行"
else
    echo "[WARN] 无障碍服务未在运行"
fi

hr "5. App 是否被 ROM 限流（国产 ROM 常见杀手）"
BUCKET="$("$ADB" shell am get-standby-bucket $PKG 2>/dev/null | tr -d '\r')"
echo "App standby bucket: $BUCKET (10=活跃 20=频繁 30=很少 45=受限)"
[ "$BUCKET" = "45" ] && echo "[WARN] App 被系统限制后台 → 关闭电池优化限制/加锁后台"

hr "6. 最近的自动记账日志（做过支付后再跑最有价值）"
LOG="$("$ADB" logcat -d -s JiYiBiNotify:V 2>/dev/null | tail -40)"
if [ -z "$LOG" ]; then
    echo "（暂无日志。若刚支付过一笔却什么都没有：通知没来或服务已死，回看第 2/4 节）"
else
    echo "$LOG"
    echo
    echo "日志关键词对照:"
    echo "  通知监听已连接   → 服务正常"
    echo "  已自动记账       → 成功落库（去 App「我的→自动记账」核对）"
    echo "  未命中 [...]     → 通知收到了但规则没识别，把文案发开发者补规则"
    echo "  自动记账已关闭   → App 内开关被关了，「我的→自动记账」里打开"
    echo "  跳过重复支付事件 → 记过账被去重挡了，属正常"
fi

hr "完成。把 FAIL 项修掉后再试一笔小额支付"
