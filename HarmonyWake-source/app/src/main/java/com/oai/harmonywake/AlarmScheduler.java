package com.oai.harmonywake;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;

public class AlarmScheduler {

    /*
     * ==============================================
     * 注册一个任务
     * ==============================================
     */
    public static void schedule(
            Context context,
            WakeTask task
    ) {

        if (
                context == null
                        ||
                task == null
        ) {

            return;
        }

        /*
         * 先清除同 ID 的旧 PendingIntent。
         *
         * 这样修改时间以后不会留下两个闹钟。
         */
        cancel(
                context,
                task.id
        );

        if (!task.enabled) {
            return;
        }

        /*
         * ==========================================
         * 计算下一次执行时间
         * ==========================================
         */
        Calendar next =
                Calendar.getInstance();

        next.set(
                Calendar.HOUR_OF_DAY,
                task.hour
        );

        next.set(
                Calendar.MINUTE,
                task.minute
        );

        next.set(
                Calendar.SECOND,
                0
        );

        next.set(
                Calendar.MILLISECOND,
                0
        );

        /*
         * 如果今天这个时间已经过去，
         * 就注册明天。
         *
         * 例如：
         *
         * 当前 14:30
         * 任务 07:30
         *
         * 那么下一次就是明天 07:30。
         */
        if (
                next.getTimeInMillis()
                        <=
                System.currentTimeMillis()
        ) {

            next.add(
                    Calendar.DAY_OF_YEAR,
                    1
            );
        }

        AlarmManager alarmManager =
                (AlarmManager)
                        context.getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager == null) {
            return;
        }

        /*
         * ==========================================
         * 关键：
         *
         * 闹钟到点直接启动 WakeActivity。
         * ==========================================
         *
         * 旧版本：
         *
         * AlarmManager
         *      ↓
         * AlarmReceiver
         *      ↓
         * context.startActivity()
         *
         * HarmonyOS 可能阻止后台 Receiver
         * 拉起 Activity。
         *
         *
         * 新版本：
         *
         * AlarmManager
         *      ↓
         * PendingIntent.getActivity()
         *      ↓
         * WakeActivity
         */
        Intent wakeIntent =
                new Intent(
                        context,
                        WakeActivity.class
                );

        wakeIntent.putExtra(
                "task_id",
                task.id
        );

        wakeIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent wakePendingIntent =
                PendingIntent.getActivity(
                        context,

                        /*
                         * 每一个 WakeTask 都使用独立 requestCode。
                         */
                        task.id,

                        wakeIntent,

                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        PendingIntent.FLAG_IMMUTABLE
                );

        /*
         * ==========================================
         * 系统“下一个闹钟”入口
         * ==========================================
         *
         * 用户点系统显示的闹钟信息时，
         * 打开 Harmony Wake 主页面。
         */
        Intent mainIntent =
                new Intent(
                        context,
                        MainActivity.class
                );

        mainIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
        );

        PendingIntent showIntent =
                PendingIntent.getActivity(
                        context,
                        100000 + task.id,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        PendingIntent.FLAG_IMMUTABLE
                );

        /*
         * AlarmClock 类型比普通 exact alarm
         * 更符合这个应用的用途。
         *
         * 系统在 Doze / 息屏下会给予
         * 用户可见闹钟更高优先级。
         */
        AlarmManager.AlarmClockInfo alarmClockInfo =
                new AlarmManager.AlarmClockInfo(
                        next.getTimeInMillis(),
                        showIntent
                );

        try {

            alarmManager.setAlarmClock(
                    alarmClockInfo,
                    wakePendingIntent
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /*
     * ==============================================
     * 取消一个任务
     * ==============================================
     */
    public static void cancel(
            Context context,
            int taskId
    ) {

        if (context == null) {
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager)
                        context.getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager == null) {
            return;
        }

        /*
         * ==========================================
         * 清除新版 Activity PendingIntent
         * ==========================================
         */
        Intent wakeIntent =
                new Intent(
                        context,
                        WakeActivity.class
                );

        PendingIntent activityPendingIntent =
                PendingIntent.getActivity(
                        context,
                        taskId,
                        wakeIntent,
                        PendingIntent.FLAG_NO_CREATE
                                |
                        PendingIntent.FLAG_IMMUTABLE
                );

        if (
                activityPendingIntent
                        !=
                null
        ) {

            try {

                alarmManager.cancel(
                        activityPendingIntent
                );

            } catch (Exception ignored) {
            }

            activityPendingIntent.cancel();
        }

        /*
         * ==========================================
         * 同时清除旧版本 Broadcast PendingIntent
         * ==========================================
         *
         * 这样用户从旧 APK 直接升级时，
         * 不会残留旧闹钟。
         */
        Intent oldReceiverIntent =
                new Intent(
                        context,
                        AlarmReceiver.class
                );

        PendingIntent oldBroadcast =
                PendingIntent.getBroadcast(
                        context,
                        taskId,
                        oldReceiverIntent,
                        PendingIntent.FLAG_NO_CREATE
                                |
                        PendingIntent.FLAG_IMMUTABLE
                );

        if (
                oldBroadcast
                        !=
                null
        ) {

            try {

                alarmManager.cancel(
                        oldBroadcast
                );

            } catch (Exception ignored) {
            }

            oldBroadcast.cancel();
        }
    }

    /*
     * ==============================================
     * 重新注册全部任务
     * ==============================================
     *
     * 主要用于：
     *
     * BOOT_COMPLETED
     * TIME_SET
     * TIMEZONE_CHANGED
     * 应用升级
     *
     * 以及 MainActivity 每次打开时的自修复。
     */
    public static void rescheduleAll(
            Context context
    ) {

        if (context == null) {
            return;
        }

        List<WakeTask> tasks =
                TaskStore.load(
                        context
                );

        if (tasks == null) {
            return;
        }

        for (
                WakeTask task :
                tasks
        ) {

            if (
                    task != null
                            &&
                    task.enabled
            ) {

                schedule(
                        context,
                        task
                );
            }
        }
    }
}
