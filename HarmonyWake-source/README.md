# Harmony Wake

功能：多个每日定点任务；每个任务选择目标 App；每个任务独立亮屏分钟数；AlarmManager.setAlarmClock 精确定时；重启/时间/时区变化后恢复。

构建：`gradle assembleDebug`，输出 `app/build/outputs/apk/debug/app-debug.apk`。

HarmonyOS 4 / 华为设备请允许自启动、关联启动、后台活动，并关闭对本 App 的严格电池优化。完全关机状态无法由普通第三方 APK 保证开机。
