package com.oai.harmonywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout list;
    private TextView reliabilityStatus;

    private List<WakeTask> tasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();

        tasks = TaskStore.load(this);

        /*
         * 每次用户重新打开 Harmony Wake，
         * 都重新注册一次所有启用的闹钟。
         *
         * 如果华为系统之前清理过部分系统状态，
         * 这里相当于一次“自修复”。
         */
        try {
            AlarmScheduler.rescheduleAll(this);
        } catch (Exception ignored) {
        }

        updateReliabilityStatus();

        render();
    }

    private TextView createText(
            String text,
            int sizeSp
    ) {

        TextView view =
                new TextView(this);

        view.setText(text);
        view.setTextSize(sizeSp);

        view.setPadding(
                0,
                12,
                0,
                12
        );

        return view;
    }

    private Button createButton(
            String text
    ) {

        Button button =
                new Button(this);

        button.setText(text);

        return button;
    }

    private void buildUi() {

        ScrollView scrollView =
                new ScrollView(this);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                36,
                36,
                36,
                36
        );

        scrollView.addView(root);

        root.addView(
                createText(
                        "Harmony Wake",
                        28
                )
        );

        root.addView(
                createText(
                        "每天按固定时间点亮屏幕、启动指定 App，并按任务保持亮屏。",
                        15
                )
        );

        /*
         * ===============================
         * 后台可靠性状态
         * ===============================
         */

        reliabilityStatus =
                createText(
                        "",
                        15
                );

        root.addView(
                reliabilityStatus
        );

        /*
         * 华为自启动设置
         */
        Button huaweiButton =
                createButton(
                        "① 华为自启动 / 后台运行设置"
                );

        huaweiButton.setOnClickListener(
                v -> openHuaweiStartupSettings()
        );

        root.addView(
                huaweiButton
        );

        /*
         * 电池优化
         */
        Button batteryButton =
                createButton(
                        "② 关闭 Harmony Wake 电池优化"
                );

        batteryButton.setOnClickListener(
                v -> openBatteryOptimizationSettings()
        );

        root.addView(
                batteryButton
        );

        /*
         * Android/HarmonyOS 应用详情
         */
        Button appSettingsButton =
                createButton(
                        "③ Harmony Wake 应用详情"
                );

        appSettingsButton.setOnClickListener(
                v -> openAppDetails()
        );

        root.addView(
                appSettingsButton
        );

        TextView help =
                createText(
                        "华为/HarmonyOS 建议：\n"
                                +
                        "• 关闭“自动管理”\n"
                                +
                        "• 开启“允许自启动”\n"
                                +
                        "• 开启“允许关联启动”\n"
                                +
                        "• 开启“允许后台活动”\n"
                                +
                        "• 电池优化设置为“不允许优化”\n\n"
                                +
                        "注意：不要在系统设置里对 Harmony Wake 执行“强行停止”。",
                        14
                );

        root.addView(
                help
        );

        /*
         * ===============================
         * 任务按钮
         * ===============================
         */

        Button addButton =
                createButton(
                        "＋ 添加唤醒任务"
                );

        addButton.setOnClickListener(
                v -> editTask(null)
        );

        root.addView(
                addButton
        );

        list =
                new LinearLayout(this);

        list.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(
                list
        );

        setContentView(
                scrollView
        );
    }

    /*
     * 显示当前后台保障状态
     */
    private void updateReliabilityStatus() {

        if (reliabilityStatus == null) {
            return;
        }

        boolean ignoringBattery =
                isIgnoringBatteryOptimizations();

        StringBuilder status =
                new StringBuilder();

        status.append(
                "运行保障状态\n"
        );

        status.append(
                "✓ 系统闹钟模式：已启用\n"
        );

        status.append(
                "✓ 手机重启后：自动恢复任务\n"
        );

        if (ignoringBattery) {

            status.append(
                    "✓ 电池优化：已关闭\n"
            );

        } else {

            status.append(
                    "⚠ 电池优化：建议关闭\n"
            );
        }

        status.append(
                "⚠ 华为自启动：请在系统设置中确认"
        );

        reliabilityStatus.setText(
                status.toString()
        );
    }

    private boolean isIgnoringBatteryOptimizations() {

        if (
                Build.VERSION.SDK_INT
                        <
                Build.VERSION_CODES.M
        ) {
            return true;
        }

        try {

            PowerManager powerManager =
                    (PowerManager)
                            getSystemService(
                                    POWER_SERVICE
                            );

            return powerManager != null
                    &&
                    powerManager
                            .isIgnoringBatteryOptimizations(
                                    getPackageName()
                            );

        } catch (Exception ignored) {

            return false;
        }
    }

    /*
     * ===============================
     * 华为 / HarmonyOS 自启动设置
     * ===============================
     */

    private void openHuaweiStartupSettings() {

        /*
         * HarmonyOS / EMUI 不同版本的入口名称
         * 可能不同。
         *
         * 因此这里依次尝试多个华为 System Manager 页面。
         */

        String[][] components = {

                {
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                },

                {
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                },

                {
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                }
        };

        for (
                String[] component :
                components
        ) {

            try {

                Intent intent =
                        new Intent();

                intent.setComponent(
                        new ComponentName(
                                component[0],
                                component[1]
                        )
                );

                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                );

                startActivity(
                        intent
                );

                Toast.makeText(
                        this,
                        "请关闭“自动管理”，并允许自启动、关联启动和后台活动。",
                        Toast.LENGTH_LONG
                ).show();

                return;

            } catch (Exception ignored) {
            }
        }

        /*
         * 华为系统页面路径发生变化时，
         * 退回 Harmony Wake 自己的应用详情页面。
         */
        Toast.makeText(
                this,
                "没有找到华为启动管理页面，请在应用启动管理中手动设置 Harmony Wake。",
                Toast.LENGTH_LONG
        ).show();

        openAppDetails();
    }

    /*
     * ===============================
     * 电池优化
     * ===============================
     */

    private void openBatteryOptimizationSettings() {

        if (
                Build.VERSION.SDK_INT
                        <
                Build.VERSION_CODES.M
        ) {

            openAppDetails();

            return;
        }

        try {

            Intent intent =
                    new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    );

            intent.setData(
                    Uri.parse(
                            "package:"
                                    +
                            getPackageName()
                    )
            );

            startActivity(
                    intent
            );

        } catch (Exception firstException) {

            /*
             * 某些 HarmonyOS 版本不支持直接弹出
             * 单个 App 的白名单页面。
             *
             * 此时打开总电池优化列表。
             */
            try {

                Intent intent =
                        new Intent(
                                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        );

                startActivity(
                        intent
                );

            } catch (Exception ignored) {

                openAppDetails();
            }
        }
    }

    private void openAppDetails() {

        try {

            Intent intent =
                    new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    );

            intent.setData(
                    Uri.parse(
                            "package:"
                                    +
                            getPackageName()
                    )
            );

            startActivity(
                    intent
            );

        } catch (Exception ignored) {
        }
    }

    /*
     * ===============================
     * 任务列表
     * ===============================
     */

    private void render() {

        list.removeAllViews();

        if (tasks.isEmpty()) {

            list.addView(
                    createText(
                            "还没有任务。请至少添加两个固定时间点。",
                            17
                    )
            );

            return;
        }

        tasks.sort(
                (a, b) ->
                        (a.hour * 60 + a.minute)
                                -
                        (b.hour * 60 + b.minute)
        );

        for (
                WakeTask task :
                new ArrayList<>(tasks)
        ) {

            LinearLayout card =
                    new LinearLayout(this);

            card.setOrientation(
                    LinearLayout.VERTICAL
            );

            card.setPadding(
                    12,
                    18,
                    12,
                    18
            );

            String timeText =
                    String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            task.hour,
                            task.minute
                    );

            card.addView(
                    createText(
                            timeText,
                            26
                    )
            );

            String appName;

            if (
                    task.appName != null
                            &&
                    !task.appName.isEmpty()
            ) {

                appName =
                        task.appName;

            } else if (
                    task.packageName != null
                            &&
                    !task.packageName.isEmpty()
            ) {

                appName =
                        task.packageName;

            } else {

                appName =
                        "未选择";
            }

            String detailText =
                    "目标 App："
                            +
                    appName
                            +
                    "\n亮屏："
                            +
                    task.screenMinutes
                            +
                    " 分钟";

            card.addView(
                    createText(
                            detailText,
                            16
                    )
            );

            Switch enableSwitch =
                    new Switch(this);

            enableSwitch.setText(
                    "启用任务"
            );

            enableSwitch.setChecked(
                    task.enabled
            );

            enableSwitch.setOnCheckedChangeListener(
                    (buttonView, checked) -> {

                        task.enabled =
                                checked;

                        persist();
                    }
            );

            card.addView(
                    enableSwitch
            );

            LinearLayout buttonRow =
                    new LinearLayout(this);

            buttonRow.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            /*
             * 编辑
             */
            Button editButton =
                    createButton(
                            "编辑"
                    );

            editButton.setOnClickListener(
                    v -> editTask(task)
            );

            buttonRow.addView(
                    editButton
            );

            /*
             * 立即测试
             *
             * 新版本直接启动 WakeActivity，
             * 不再先经过 AlarmReceiver。
             */
            Button testButton =
                    createButton(
                            "立即测试"
                    );

            testButton.setOnClickListener(
                    v -> {

                        Intent intent =
                                new Intent(
                                        this,
                                        WakeActivity.class
                                );

                        intent.putExtra(
                                "task_id",
                                task.id
                        );

                        startActivity(
                                intent
                        );
                    }
            );

            buttonRow.addView(
                    testButton
            );

            /*
             * 删除
             */
            Button deleteButton =
                    createButton(
                            "删除"
                    );

            deleteButton.setOnClickListener(
                    v -> {

                        AlarmScheduler.cancel(
                                this,
                                task.id
                        );

                        tasks.remove(
                                task
                        );

                        persist();

                        render();
                    }
            );

            buttonRow.addView(
                    deleteButton
            );

            card.addView(
                    buttonRow
            );

            list.addView(
                    card
            );
        }
    }

    /*
     * ===============================
     * 编辑任务
     * ===============================
     */

    private void editTask(
            WakeTask existingTask
    ) {

        final WakeTask draft;

        if (existingTask == null) {

            draft =
                    new WakeTask(
                            (int) (
                                    System.currentTimeMillis()
                                            &
                                    0x7fffffff
                            ),
                            7,
                            30,
                            "",
                            "",
                            10,
                            true
                    );

        } else {

            draft =
                    new WakeTask(
                            existingTask.id,
                            existingTask.hour,
                            existingTask.minute,
                            existingTask.packageName,
                            existingTask.appName,
                            existingTask.screenMinutes,
                            existingTask.enabled
                    );
        }

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                36,
                12,
                36,
                0
        );

        Button timeButton =
                createButton(
                        String.format(
                                Locale.getDefault(),
                                "时间：%02d:%02d",
                                draft.hour,
                                draft.minute
                        )
                );

        timeButton.setOnClickListener(
                v -> {

                    TimePickerDialog dialog =
                            new TimePickerDialog(
                                    this,
                                    (view, hour, minute) -> {

                                        draft.hour =
                                                hour;

                                        draft.minute =
                                                minute;

                                        timeButton.setText(
                                                String.format(
                                                        Locale.getDefault(),
                                                        "时间：%02d:%02d",
                                                        hour,
                                                        minute
                                                )
                                        );
                                    },
                                    draft.hour,
                                    draft.minute,
                                    true
                            );

                    dialog.show();
                }
        );

        container.addView(
                timeButton
        );

        String appButtonText;

        if (
                draft.appName != null
                        &&
                !draft.appName.isEmpty()
        ) {

            appButtonText =
                    "目标 App："
                            +
                    draft.appName;

        } else {

            appButtonText =
                    "选择目标 App";
        }

        Button appButton =
                createButton(
                        appButtonText
                );

        appButton.setOnClickListener(
                v -> chooseApp(
                        draft,
                        appButton
                )
        );

        container.addView(
                appButton
        );

        EditText screenMinutes =
                new EditText(this);

        screenMinutes.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );

        screenMinutes.setHint(
                "亮屏分钟数，例如 10"
        );

        screenMinutes.setText(
                String.valueOf(
                        draft.screenMinutes
                )
        );

        container.addView(
                screenMinutes
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        existingTask == null
                                ?
                        "添加任务"
                                :
                        "编辑任务"
                )
                .setView(
                        container
                )
                .setPositiveButton(
                        "保存",
                        (dialog, which) -> {

                            try {

                                int minutes =
                                        Integer.parseInt(
                                                screenMinutes
                                                        .getText()
                                                        .toString()
                                                        .trim()
                                        );

                                draft.screenMinutes =
                                        Math.max(
                                                1,
                                                minutes
                                        );

                            } catch (Exception ignored) {

                                draft.screenMinutes =
                                        10;
                            }

                            if (existingTask == null) {

                                tasks.add(
                                        draft
                                );

                            } else {

                                int index =
                                        tasks.indexOf(
                                                existingTask
                                        );

                                if (index >= 0) {

                                    tasks.set(
                                            index,
                                            draft
                                    );
                                }
                            }

                            persist();

                            render();
                        }
                )
                .setNegativeButton(
                        "取消",
                        null
                )
                .show();
    }

    /*
     * ===============================
     * 选择目标应用
     * ===============================
     */

    private void chooseApp(
            WakeTask draft,
            Button appButton
    ) {

        PackageManager packageManager =
                getPackageManager();

        List<ApplicationInfo> installedApps =
                packageManager
                        .getInstalledApplications(0);

        List<ApplicationInfo> launchableApps =
                new ArrayList<>();

        for (
                ApplicationInfo app :
                installedApps
        ) {

            if (
                    packageManager
                            .getLaunchIntentForPackage(
                                    app.packageName
                            )
                            != null
                            &&
                    !app.packageName.equals(
                            getPackageName()
                    )
            ) {

                launchableApps.add(
                        app
                );
            }
        }

        launchableApps.sort(
                Comparator.comparing(
                        app ->
                                packageManager
                                        .getApplicationLabel(
                                                app
                                        )
                                        .toString()
                                        .toLowerCase(
                                                Locale.getDefault()
                                        )
                )
        );

        String[] labels =
                new String[
                        launchableApps.size()
                ];

        for (
                int i = 0;
                i < launchableApps.size();
                i++
        ) {

            labels[i] =
                    packageManager
                            .getApplicationLabel(
                                    launchableApps.get(i)
                            )
                            .toString();
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "选择目标 App"
                )
                .setItems(
                        labels,
                        (dialog, which) -> {

                            ApplicationInfo app =
                                    launchableApps
                                            .get(which);

                            draft.packageName =
                                    app.packageName;

                            draft.appName =
                                    packageManager
                                            .getApplicationLabel(
                                                    app
                                            )
                                            .toString();

                            appButton.setText(
                                    "目标 App："
                                            +
                                    draft.appName
                            );
                        }
                )
                .setNegativeButton(
                        "取消",
                        null
                )
                .show();
    }

    /*
     * ===============================
     * 保存 + 注册系统闹钟
     * ===============================
     */

    private void persist() {

        TaskStore.save(
                this,
                tasks
        );

        for (
                WakeTask task :
                tasks
        ) {

            AlarmScheduler.schedule(
                    this,
                    task
            );
        }
    }
}
