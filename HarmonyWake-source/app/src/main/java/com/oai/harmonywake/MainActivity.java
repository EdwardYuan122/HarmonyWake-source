package com.oai.harmonywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout list;
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
        render();
    }

    private TextView createText(String text, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setPadding(0, 12, 0, 12);
        return view;
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private void buildUi() {

        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);

        scrollView.addView(root);

        root.addView(createText("Harmony Wake", 28));

        root.addView(
                createText(
                        "每天按固定时间点亮屏幕、启动指定 App，并按任务保持亮屏。",
                        15
                )
        );

        Button addButton = createButton("＋ 添加唤醒任务");
        addButton.setOnClickListener(v -> editTask(null));
        root.addView(addButton);

        Button settingsButton = createButton("应用后台设置");
        settingsButton.setOnClickListener(v -> {

            try {
                Intent intent = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                );

                intent.setData(
                        android.net.Uri.parse(
                                "package:" + getPackageName()
                        )
                );

                startActivity(intent);

            } catch (Exception ignored) {
            }
        });

        root.addView(settingsButton);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        root.addView(list);

        setContentView(scrollView);
    }

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

        for (WakeTask task : new ArrayList<>(tasks)) {

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(12, 18, 12, 18);

            String timeText = String.format(
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

            if (task.appName != null && !task.appName.isEmpty()) {
                appName = task.appName;
            } else if (
                    task.packageName != null
                            &&
                    !task.packageName.isEmpty()
            ) {
                appName = task.packageName;
            } else {
                appName = "未选择";
            }

            String detailText =
                    "目标 App：" + appName
                            +
                    "\n亮屏：" + task.screenMinutes + " 分钟";

            card.addView(
                    createText(
                            detailText,
                            16
                    )
            );

            Switch enableSwitch = new Switch(this);
            enableSwitch.setText("启用任务");
            enableSwitch.setChecked(task.enabled);

            enableSwitch.setOnCheckedChangeListener(
                    (buttonView, checked) -> {

                        task.enabled = checked;

                        persist();
                    }
            );

            card.addView(enableSwitch);

            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);

            Button editButton = createButton("编辑");

            editButton.setOnClickListener(
                    v -> editTask(task)
            );

            buttonRow.addView(editButton);

            Button testButton = createButton("立即测试");

            testButton.setOnClickListener(v -> {

                Intent intent =
                        new Intent(
                                this,
                                AlarmReceiver.class
                        );

                intent.putExtra(
                        "task_id",
                        task.id
                );

                sendBroadcast(intent);
            });

            buttonRow.addView(testButton);

            Button deleteButton = createButton("删除");

            deleteButton.setOnClickListener(v -> {

                AlarmScheduler.cancel(
                        this,
                        task.id
                );

                tasks.remove(task);

                persist();

                render();
            });

            buttonRow.addView(deleteButton);

            card.addView(buttonRow);

            list.addView(card);
        }
    }

    private void editTask(WakeTask existingTask) {

        final WakeTask draft;

        if (existingTask == null) {

            draft = new WakeTask(
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

            draft = new WakeTask(
                    existingTask.id,
                    existingTask.hour,
                    existingTask.minute,
                    existingTask.packageName,
                    existingTask.appName,
                    existingTask.screenMinutes,
                    existingTask.enabled
            );
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(36, 12, 36, 0);

        Button timeButton =
                createButton(
                        String.format(
                                Locale.getDefault(),
                                "时间：%02d:%02d",
                                draft.hour,
                                draft.minute
                        )
                );

        timeButton.setOnClickListener(v -> {

            TimePickerDialog dialog =
                    new TimePickerDialog(
                            this,
                            (view, hour, minute) -> {

                                draft.hour = hour;
                                draft.minute = minute;

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
        });

        container.addView(timeButton);

        String appButtonText;

        if (
                draft.appName != null
                        &&
                !draft.appName.isEmpty()
        ) {
            appButtonText =
                    "目标 App：" + draft.appName;
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

        container.addView(appButton);

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

        container.addView(screenMinutes);

        new AlertDialog.Builder(this)
                .setTitle(
                        existingTask == null
                                ?
                        "添加任务"
                                :
                        "编辑任务"
                )
                .setView(container)
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

                                draft.screenMinutes = 10;
                            }

                            if (existingTask == null) {

                                tasks.add(draft);

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

                launchableApps.add(app);
            }
        }

        launchableApps.sort(
                Comparator.comparing(
                        app ->
                                packageManager
                                        .getApplicationLabel(app)
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
                                            .getApplicationLabel(app)
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
