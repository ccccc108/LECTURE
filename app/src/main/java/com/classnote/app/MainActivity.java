package com.classnote.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

/**
 * 首页：选科目 → 开始录音（实时英文字幕）→ 结束并生成课后总结。
 */
public class MainActivity extends AppCompatActivity implements SessionHub.Listener {

    private static final int REQ_MIC = 1001;
    private static final long DOUBLE_CLICK_GUARD_MS = 1500L;

    private Spinner spinnerSubject;
    private TextView txtStatus;
    private TextView txtLive;
    private ScrollView scrollLive;
    private Button btnStart;
    private Button btnStop;
    private Button btnHistory;
    private Button btnSettings;
    private Button btnCopyLive;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private long lastStartClickAt = 0L;
    private boolean stopRequested = false;
    private AlertDialog sessionDialog;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            if (SessionHub.busy) {
                ui.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerSubject = findViewById(R.id.spinnerSubject);
        txtStatus = findViewById(R.id.txtStatus);
        txtLive = findViewById(R.id.txtLive);
        scrollLive = findViewById(R.id.scrollLive);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnHistory = findViewById(R.id.btnHistory);
        btnSettings = findViewById(R.id.btnSettings);
        btnCopyLive = findViewById(R.id.btnCopyLive);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, Subjects.NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(adapter);
        selectLastSubject();

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        btnCopyLive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyLive();
            }
        });
    }

    private void selectLastSubject() {
        String last = Prefs.lastSubject(this);
        if (last.isEmpty()) {
            return;
        }
        for (int i = 0; i < Subjects.NAMES.length; i++) {
            if (Subjects.NAMES[i].equals(last)) {
                spinnerSubject.setSelection(i);
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SessionHub.addListener(this);
        ui.removeCallbacks(ticker);
        ui.postDelayed(ticker, 200);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SessionHub.removeListener(this);
        ui.removeCallbacks(ticker);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void startRecording() {
        long now = System.currentTimeMillis();
        if (now - lastStartClickAt < DOUBLE_CLICK_GUARD_MS) {
            return;
        }
        lastStartClickAt = now;

        if (SessionHub.busy) {
            toast("上一节课的录音或总结还没结束，请稍等");
            return;
        }
        if (Prefs.asrKey(this).isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("还差一步")
                    .setMessage("请先填写“转写服务”的 API Key。\n\n默认使用硅基流动 SenseVoice："
                            + "注册账号 → API 密钥 → 复制 Key → 粘贴到设置里（有免费额度）。")
                    .setPositiveButton("去设置", (dialog, which) ->
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class)))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        if (!hasMicPermission()) {
            requestPermissions();
            return;
        }

        String subject = Subjects.DEFAULT;
        Object sel = spinnerSubject.getSelectedItem();
        if (sel != null) {
            subject = sel.toString();
        }
        Prefs.put(this, "last_subject", subject);

        SessionHub.reset(subject);
        Intent intent = new Intent(this, RecordService.class);
        intent.setAction(RecordService.ACTION_START);
        intent.putExtra(RecordService.EXTRA_SUBJECT, subject);
        try {
            ContextCompat.startForegroundService(this, intent);
            toast("开始录音：" + subject + "（可以锁屏或切到别的应用）");
        } catch (Exception e) {
            toast("启动录音失败：" + e.getMessage());
            return;
        }
        refreshUi();
        ui.removeCallbacks(ticker);
        ui.postDelayed(ticker, 500);
    }

    private void stopRecording() {
        stopRequested = true;
        btnStop.setEnabled(false);
        SessionHub.setStatus("正在结束并生成总结…");
        Intent intent = new Intent(this, RecordService.class);
        intent.setAction(RecordService.ACTION_STOP);
        try {
            // 服务已经在运行，这里用 startService；不要用 startForegroundService，
            // 否则服务若还没进入前台就 stopSelf 会触发 Android 12+ 的前台服务超时崩溃。
            startService(intent);
        } catch (Exception e) {
            toast("结束录音失败：" + e.getMessage());
        }
        refreshUi();
    }

    private void copyLive() {
        String text = SessionHub.liveText();
        if (text.isEmpty()) {
            toast("还没有转写内容");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("classnote-transcript", text));
        }
        toast("已复制（约 " + TextMerge.wordCount(text) + " 个英文词）");
    }

    private void refreshUi() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        boolean busy = SessionHub.busy;
        boolean rec = SessionHub.recording;
        if (!rec) {
            stopRequested = false;
        }
        btnStart.setEnabled(!busy && !stopRequested);
        btnStop.setEnabled(rec && !stopRequested);

        String status = SessionHub.statusText == null ? "" : SessionHub.statusText;
        if (status.isEmpty()) {
            if (busy) {
                status = "处理中…";
            } else if (Prefs.asrKey(this).isEmpty()) {
                status = "首次使用：请点右上角【设置】填写转写与总结的 API Key";
            } else {
                status = "空闲：选好科目，点“开始录音”";
            }
        }
        if (rec) {
            status = status + "　已录 "
                    + TextMerge.fmtDuration(System.currentTimeMillis() - SessionHub.startAt);
        }
        txtStatus.setText(status);

        String live = SessionHub.liveText();
        if (live.isEmpty()) {
            if (!rec && !busy) {
                txtLive.setText("（还没有开始录音）");
            }
        } else if (!live.equals(txtLive.getText().toString())) {
            txtLive.setText(live);
            scrollToBottom();
        }

        if (rec) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void scrollToBottom() {
        scrollLive.post(new Runnable() {
            @Override
            public void run() {
                scrollLive.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            }, REQ_MIC);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO
            }, REQ_MIC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) {
            return;
        }
        if (hasMicPermission()) {
            // 刚刚是从授权弹窗回来，跳过防连点的时间窗限制
            lastStartClickAt = 0L;
            startRecording();
        } else {
            toast("没有麦克风权限，无法录音");
        }
    }

    private void openDetail(File dir) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_DIR, dir.getAbsolutePath());
        startActivity(i);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ---------- SessionHub.Listener ----------

    @Override
    public void onLiveUpdate(String liveText, String status) {
        refreshUi();
    }

    @Override
    public void onSessionFinished(String summary, String transcript, String error, File dir) {
        refreshUi();
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (error != null && !error.isEmpty()) {
            toast(error);
        }
        if (dir == null) {
            return;
        }
        final File target = dir;
        boolean hasSummary = summary != null && !summary.isEmpty();
        if (sessionDialog != null) {
            try {
                sessionDialog.dismiss();
            } catch (Exception ignored) {
            }
            sessionDialog = null;
        }
        sessionDialog = new AlertDialog.Builder(this)
                .setTitle(hasSummary ? "课后总结已生成" : "本次记录已保存")
                .setMessage(hasSummary
                        ? "课堂笔记已在“历史记录”里，可以随时查看、分享或重新生成。"
                        : "转写稿已保存（没有生成总结）。是否现在查看？")
                .setPositiveButton("查看", (dialog, which) -> openDetail(target))
                .setNegativeButton("稍后", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (sessionDialog != null) {
            try {
                sessionDialog.dismiss();
            } catch (Exception ignored) {
            }
            sessionDialog = null;
        }
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onError(String message) {
        refreshUi();
        toast(message);
    }
}
