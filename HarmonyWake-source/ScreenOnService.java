package com.oai.harmonywake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class ScreenOnService extends Service {

    private static final String CHANNEL_ID =
            "harmony_wake_screen_service";

    private static final String CHANNEL_NAME =
            "Harmony Wake 亮屏服务";

    private static final int NOTIFICATION_ID =
            2001;

    /*
     * 屏幕 WakeLock。
     *
     * 注意：
     * SCREEN_BRIGHT_WAKE_LOCK 在新 Android API
     * 中已经属于 deprecated。
     *
     * 这里作为 HarmonyOS 4 / Android 兼容层使用，
     * 是因为目标需求是：
     *
     * Harmony Wake Activity 已经退出，
     * 目标第三方 App 在前台，
     * 仍然保持屏幕亮指定时间。
     *
     * 第三方应用无法给另一个 App 的 Window
     * 添加 FLAG_KEEP_SCREEN_ON。
     */
    private PowerManager.WakeLock screenWakeLock;

    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    private Runnable stopRunnable;

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        /*
         * 默认 1 分钟。
         */
        long durationMillis =
                60_000L;

        if (intent != null) {

            durationMillis =
                    intent.getLongExtra(
                            "duration_ms",
                            60_000L
                    );
        }

        /*
         * 最少保持 10 秒，
         * 防止错误值导致立刻退出。
         */
        durationMillis =
                Math.max(
                        10_000L,
                        durationMillis
                );

        /*
         * ==========================================
         * Foreground Service 必须尽快 startForeground
         * ==========================================
         */
        Notification notification =
                buildNotification();

        startForeground(
                NOTIFICATION_ID,
                notification
        );

        /*
         * 获取屏幕 WakeLock。
         */
        acquireScreenWakeLock(
                durationMillis
        );

        /*
         * 如果 Service 已经因为上一个任务运行，
         * 先取消旧的停止计时器。
         */
        if (stopRunnable != null) {

            handler.removeCallbacks(
                    stopRunnable
            );
        }

        final long finalDuration =
                durationMillis;

        stopRunnable =
                () -> {

                    releaseScreenWakeLock();

                    try {

                        stopForeground(
                                true
                        );

                    } catch (Exception ignored) {
                    }

                    stopSelf();
                };

        handler.postDelayed(
                stopRunnable,
                finalDuration
        );

        /*
         * 不希望系统杀掉以后自动重新启动。
         *
         * 下一个定时任务会重新拉起 Service。
         */
        return START_NOT_STICKY;
    }

    /*
     * ==============================================
     * 获得屏幕 WakeLock
     * ==============================================
     */
    @SuppressWarnings("deprecation")
    private void acquireScreenWakeLock(
            long durationMillis
    ) {

        releaseScreenWakeLock();

        try {

            PowerManager powerManager =
                    (PowerManager)
                            getSystemService(
                                    POWER_SERVICE
                            );

            if (powerManager == null) {
                return;
            }

            screenWakeLock =
                    powerManager.newWakeLock(

                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                                    |
                            PowerManager.ACQUIRE_CAUSES_WAKEUP
                                    |
                            PowerManager.ON_AFTER_RELEASE,

                            "HarmonyWake:ScreenOn"
                    );

            /*
             * 我们自己控制生命周期，
             * 不使用引用计数。
             */
            screenWakeLock.setReferenceCounted(
                    false
            );

            /*
             * WakeLock 自己也设超时。
             *
             * 即使 Service 因异常没执行到 release，
             * 系统最多多保持 5 秒。
             *
             * 避免出现无限亮屏。
             */
            screenWakeLock.acquire(
                    durationMillis
                            +
                    5000L
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /*
     * ==============================================
     * 释放 WakeLock
     * ==============================================
     */
    private void releaseScreenWakeLock() {

        try {

            if (
                    screenWakeLock
                            !=
                    null
                            &&
                    screenWakeLock.isHeld()
            ) {

                screenWakeLock.release();
            }

        } catch (Exception ignored) {
        }

        screenWakeLock =
                null;
    }

    /*
     * ==============================================
     * Notification Channel
     * ==============================================
     */
    private void createNotificationChannel() {

        if (
                Build.VERSION.SDK_INT
                        <
                Build.VERSION_CODES.O
        ) {

            return;
        }

        try {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Harmony Wake 在定时启动目标应用后，"
                            +
                    "按任务设定时间保持屏幕亮起。"
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /*
     * ==============================================
     * 前台通知
     * ==============================================
     */
    private Notification buildNotification() {

        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.O
        ) {

            return new Notification.Builder(
                    this,
                    CHANNEL_ID
            )
                    .setContentTitle(
                            "Harmony Wake"
                    )
                    .setContentText(
                            "正在按任务设置保持屏幕点亮"
                    )
                    .setSmallIcon(
                            android.R.drawable
                                    .ic_lock_idle_alarm
                    )
                    .setOngoing(
                            true
                    )
                    .setOnlyAlertOnce(
                            true
                    )
                    .build();

        } else {

            return new Notification.Builder(
                    this
            )
                    .setContentTitle(
                            "Harmony Wake"
                    )
                    .setContentText(
                            "正在保持屏幕点亮"
                    )
                    .setSmallIcon(
                            android.R.drawable
                                    .ic_lock_idle_alarm
                    )
                    .setOngoing(
                            true
                    )
                    .build();
        }
    }

    @Override
    public void onDestroy() {

        /*
         * 删除还没执行的定时停止回调。
         */
        if (stopRunnable != null) {

            handler.removeCallbacks(
                    stopRunnable
            );

            stopRunnable =
                    null;
        }

        releaseScreenWakeLock();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        /*
         * 不是 Bound Service。
         */
        return null;
    }
}
