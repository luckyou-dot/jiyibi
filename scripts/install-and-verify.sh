#!/usr/bin/env bash
# ============================================================================
# 一键发版脚本：构建 release → 装机 → 恢复无障碍授权 → 验证双服务在线
#
# 为什么需要它：adb 覆盖安装后，通知监听会自动重连，但无障碍服务
# 不一定被系统重新绑定（表现为「设置里开着、实际没在跑」），必须
# 重写一次 enabled_accessibility_services 触发重绑——本脚本自动完成。
#
# 用法：手机连 USB 后在项目根目录执行
#   bash scripts/install-and-verify.sh
# ============================================================================
set -eu

ADB="$(command -v adb || true)"
if [ -z "$ADB" ]; then
    for candidate in "/d/Tools/AndroidStdioSdk/platform-tools/adb.exe" \
        "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; do
        if [ -x "$candidate" ]; then ADB="$candidate"; break; fi
    done
fi
if [ -z "$ADB" ]; then echo "[FAIL] 找不到 adb"; exit 1; fi

PKG="com.jiyibi.app"
A11Y="$PKG/com.jiyibi.app.core.notify.PaymentAccessibilityService"

"$ADB" start-server >/dev/null 2>&1
if [ -z "$("$ADB" devices | grep -w 'device')" ]; then
    echo "[FAIL] 没有已连接的设备"; exit 1
fi

echo "=== 1/4 构建 release ==="
./gradlew :app:assembleRelease --console=plain | grep -E "BUILD" || true

echo "=== 2/4 安装 ==="
"$ADB" install -r app/build/outputs/apk/release/app-release.apk | tail -1
"$ADB" shell dumpsys package $PKG | grep versionName

echo "=== 3/4 恢复授权（无障碍 settings put + 通知监听 cmd notification，均保留其他服务） ==="
CURRENT="$("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r')"
OTHERS="$(echo "$CURRENT" | tr ':' '\n' | grep -v "^$PKG/" | grep -v '^$' | paste -sd ':' -)"
"$ADB" shell settings put secure enabled_accessibility_services "${OTHERS:-"none"}"
sleep 1
if [ -n "$OTHERS" ]; then
    "$ADB" shell settings put secure enabled_accessibility_services "$A11Y:$OTHERS"
else
    "$ADB" shell settings put secure enabled_accessibility_services "$A11Y"
fi
"$ADB" shell settings put secure accessibility_enabled 1

# 通知监听：MIUI 上 settings put 不触发重绑，必须用官方 cmd notification 接口
LISTENER="$PKG/com.jiyibi.app.core.notify.PaymentNotificationListener"
"$ADB" shell cmd notification disallow_listener "$LISTENER"
sleep 1
"$ADB" shell cmd notification allow_listener "$LISTENER"
sleep 3

echo "=== 4/4 验证双服务在线（重写设置后重绑需要几秒，轮询等待） ==="
ok=""
for i in 1 2 3 4 5 6 7 8; do
    sleep 2
    COUNT="$("$ADB" shell "dumpsys activity services $PKG" 2>/dev/null | grep -cE 'ServiceRecord' || true)"
    if [ "$COUNT" = "2" ]; then ok=1; break; fi
done
if [ -n "$ok" ]; then
    echo "[OK] 通知监听 + 无障碍服务都在线（等待 $((i * 2))s 内完成重绑）"
    "$ADB" logcat -d -s JiYiBiNotify:V 2>/dev/null | grep "已连接" | tail -2
else
    echo "[WARN] 服务数量=$COUNT（期望 2）。若无障碍缺失，去手机 设置→无障碍 重新开关一次"
fi
