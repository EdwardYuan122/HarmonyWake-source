package com.oai.harmonywake;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

public class WakeActivity extends Activity {

    private WakeTask task;

    private TextView statusText;

    /*
     * 防止 requestDismissKeyguard / onResume
     * 同时触发后重复启动目标 App。
     */
    private boolean targetLaunched = false;

    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        /*
         * ==========================================
         * 1. 允许 Harmony Wake 显示在锁屏上
         * ==========================================
         *
         * KEEP_SCREEN_ON:
         * WakeActivity 自己显示时保持屏幕亮。
         *
         * SHOW_WHEN_LOCKED:
         * Activity 可以出现在锁屏上。
         *
         * TURN_SCREEN_ON:
         * 自动点亮屏幕。
         *
         * DISMISS_KEYGUARD:
         * 对旧版本 Android 尝试解除非安全锁屏。
         */
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        /*
         * Android 8.1+
         * 使用官方新接口。
         */
        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.O_MR1
        ) {

            setShowWhenLocked(
                    true
            );

            setTurnScreenOn(
                    true
            );
        }

        /*
         * 简单状态界面。
         */
        statusText =
                new TextView(this);

        statusText.setText(
                "Harmony Wake 正在唤醒…"
        );

        statusText.setTextSize(
                22
        );

        statusText.setPadding(
                48,
                80,
                48,
                48
        );

        setContentView(
                statusText
        );

        /*
         * 处理当前闹钟任务。
         */
        handleIntent(
                getIntent()
        );
    }

    /*
     * WakeActivity 使用 singleTask。
     *
     * 如果短时间内又触发一个任务，
     * Android 可能不会重新 onCreate，
     * 而是调用 onNewIntent。
     *
     * 所以这里必须重新读取 task_id。
     */
    @Override
    protected void onNewIntent(
            Intent intent
    ) {

        super.onNewIntent(
                intent
        );

        setIntent(
                intent
        );

        handleIntent(
                intent
        );
    }

    private void handleIntent(
            Intent intent
    ) {

        targetLaunched =
                false;

        int taskId =
                intent.getIntExtra(
                        "task_id",
                        -1
                );

        task =
                TaskStore.find(
                        this,
                        taskId
                );

        if (task == null) {

            statusText.setText(
                    "未找到对应的唤醒任务"
            );

            handler.postDelayed(
                    this::finish,
                    3000
            );

            return;
        }

        /*
         * ==========================================
         * 2. 立刻注册下一次闹钟
         * ==========================================
         *
         * 当前这个时间点已经触发。
         * 马上计算明天同一时间并交回 AlarmManager。
         *
         * Harmony Wake 不需要一直留在后台。
         */
        try {

            AlarmScheduler.schedule(
                    this,
                    task
            );

        } catch (Exception e) {

            e.printStackTrace();
        }

        /*
         * ==========================================
         * 3. 开启亮屏计时 Service
         * ==========================================
         *
         * 目标 App 切换到前台以后，
         * WakeActivity 自己会结束。
         *
         * 所以保持屏幕亮的工作不能只依靠
         * FLAG_KEEP_SCREEN_ON。
         */
        startScreenOnService();

        /*
         * 给屏幕 / Keyguard 大约 300ms
         * 完成唤醒，然后开始处理锁屏。
         */
        handler.postDelayed(
                this::dismissKeyguardAndLaunch,
                300
        );
    }

    /*
     * ==============================================
     * 启动按任务时长保持亮屏的 Service
     * ==============================================
     */
    private void startScreenOnService() {

        if (task == null) {
            return;
        }

        long durationMillis =
                Math.max(
                        1,
                        task.screenMinutes
                )
                        *
                60_000L;

        Intent serviceIntent =
                new Intent(
                        this,
                        ScreenOnService.class
                );

        serviceIntent.putExtra(
                "duration_ms",
                durationMillis
        );

        try {

            if (
                    Build.VERSION.SDK_INT
                            >=
                    Build.VERSION_CODES.O
            ) {

                startForegroundService(
                        serviceIntent
                );

            } else {

                startService(
                        serviceIntent
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /*
     * ==============================================
     * 解除锁屏
     * ==============================================
     */
    private void dismissKeyguardAndLaunch() {

        if (
                task == null
                        ||
                targetLaunched
        ) {

            return;
        }

        KeyguardManager keyguardManager =
                (KeyguardManager)
                        getSystemService(
                                Context.KEYGUARD_SERVICE
                        );

        /*
         * 如果此时已经没有 Keyguard，
         * 不需要继续请求解锁。
         */
        if (
                keyguardManager == null
                        ||
                !keyguardManager.isKeyguardLocked()
        ) {

            launchTargetApp();

            return;
        }

        /*
         * ==========================================
         * Android 8.0+
         * ==========================================
         *
         * requestDismissKeyguard 是官方接口。
         *
         * 如果只是：
         *
         *   点亮屏幕
         *       ↓
         *   上划解锁
         *
         * 且没有 PIN / 密码 / 图案，
         * 系统一般可以直接解除 Keyguard。
         *
         * 如果存在 PIN / 密码 / 图案，
         * Android 不允许第三方 App 绕过认证。
         */
        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.O
        ) {

            statusText.setText(
                    "正在解除锁屏…"
            );

            try {

                keyguardManager.requestDismissKeyguard(
                        this,
                        new KeyguardManager.KeyguardDismissCallback() {

                            @Override
                            public void onDismissSucceeded() {

                                super.onDismissSucceeded();

                                statusText.setText(
                                        "锁屏已解除，正在打开目标 App…"
                                );

                                /*
                                 * HarmonyOS 的 Keyguard 动画
                                 * 有时比 Android 原生稍慢。
                                 *
                                 * 延迟一小段再拉目标应用。
                                 */
                                handler.postDelayed(
                                        WakeActivity.this
                                                ::launchTargetApp,
                                        400
                                );
                            }

                            @Override
                            public void onDismissCancelled() {

                                super.onDismissCancelled();

                                statusText.setText(
                                        "正在等待锁屏解除…"
                                );

                                /*
                                 * 某些华为版本可能不给成功回调，
                                 * 再检查一次 Keyguard 状态。
                                 */
                                handler.postDelayed(
                                        WakeActivity.this
                                                ::checkKeyguardAgain,
                                        800
                                );
                            }

                            @Override
                            public void onDismissError() {

                                super.onDismissError();

                                statusText.setText(
                                        "正在等待系统解除锁屏…"
                                );

                                handler.postDelayed(
                                        WakeActivity.this
                                                ::checkKeyguardAgain,
                                        800
                                );
                            }
                        }
                );

            } catch (Exception e) {

                e.printStackTrace();

                handler.postDelayed(
                        this::checkKeyguardAgain,
                        800
                );
            }

        } else {

            /*
             * Android 7.x 及更早版本
             */
            getWindow().addFlags(
                    WindowManager.LayoutParams
                            .FLAG_DISMISS_KEYGUARD
            );

            handler.postDelayed(
                    this::checkKeyguardAgain,
                    600
            );
        }
    }

    /*
     * ==============================================
     * 华为 / HarmonyOS 兼容检查
     * ==============================================
     *
     * 有些版本 requestDismissKeyguard()
     * 已经把锁屏去掉了，但 callback 不一定及时触发。
     */
    private void checkKeyguardAgain() {

        if (
                task == null
                        ||
                targetLaunched
        ) {

            return;
        }

        KeyguardManager keyguardManager =
                (KeyguardManager)
                        getSystemService(
                                Context.KEYGUARD_SERVICE
                        );

        if (
                keyguardManager == null
                        ||
                !keyguardManager.isKeyguardLocked()
        ) {

            launchTargetApp();

        } else {

            /*
             * 仍然处于锁屏。
             *
             * 不无限循环，等待 onResume
             * 或系统解锁状态变化。
             */
            statusText.setText(
                    "等待系统解除锁屏…"
            );
        }
    }

    /*
     * 当系统 Keyguard 消失，
     * Activity 往往会重新进入 resumed 状态。
     *
     * 这里再做一次保险检查。
     */
    @Override
    protected void onResume() {

        super.onResume();

        if (
                task == null
                        ||
                targetLaunched
        ) {

            return;
        }

        KeyguardManager keyguardManager =
                (KeyguardManager)
                        getSystemService(
                                Context.KEYGUARD_SERVICE
                        );

        if (
                keyguardManager != null
                        &&
                !keyguardManager.isKeyguardLocked()
        ) {

            handler.postDelayed(
                    this::launchTargetApp,
                    250
            );
        }
    }

    /*
     * ==============================================
     * 启动真正需要唤醒的目标 App
     * ==============================================
     */
    private void launchTargetApp() {

        if (targetLaunched) {
            return;
        }

        if (task == null) {

            statusText.setText(
                    "任务不存在"
            );

            return;
        }

        if (
                task.packageName == null
                        ||
                task.packageName.trim().isEmpty()
        ) {

            statusText.setText(
                    "这个任务还没有选择目标 App"
            );

            return;
        }

        try {

            Intent launchIntent =
                    getPackageManager()
                            .getLaunchIntentForPackage(
                                    task.packageName
                            );

            if (launchIntent == null) {

                statusText.setText(
                        "无法找到目标 App 的启动入口"
                );

                return;
            }

            /*
             * 先标记，避免 onResume
             * 再执行第二次 startActivity。
             */
            targetLaunched =
                    true;

            String appName =
                    task.appName;

            if (
                    appName == null
                            ||
                    appName.trim().isEmpty()
            ) {

                appName =
                        task.packageName;
            }

            statusText.setText(
                    "正在启动 "
                            +
                    appName
            );

            launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            );

            startActivity(
                    launchIntent
            );

            /*
             * 目标应用已经进入前台后，
             * Harmony Wake Activity 可以退出。
             *
             * ScreenOnService 不会退出，
             * 它继续保持屏幕亮到用户设定时间。
             */
            handler.postDelayed(
                    this::finish,
                    1200
            );

        } catch (Exception e) {

            targetLaunched =
                    false;

            statusText.setText(
                    "启动目标 App 失败"
            );

            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

        super.onDestroy();
    }
}
