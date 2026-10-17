package com.classnote.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 单节课详情：看总结 / 看原文 / 复制 / 分享 / 分享录音 / 重新生成总结。
 */
public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_DIR = "extra_dir";

    private TextView txtMeta;
    private TextView txtContent;
    private ScrollView scrollContent;
    private Button btnToggle;
    private Button btnCopy;
    private Button btnShare;
    private Button btnShareAudio;
    private Button btnResummarize;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService exec;
    private File dir;
    private AlertDialog progressDialog;
    private String summary = "";
    private String transcript = "";
    private boolean showingSummary = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        txtMeta = findViewById(R.id.txtMeta);
        txtContent = findViewById(R.id.txtContent);
        scrollContent = findViewById(R.id.scrollContent);
        btnToggle = findViewById(R.id.btnToggle);
        btnCopy = findViewById(R.id.btnCopy);
        btnShare = findViewById(R.id.btnShare);
        btnShareAudio = findViewById(R.id.btnShareAudio);
        btnResummarize = findViewById(R.id.btnResummarize);

        String path = getIntent().getStringExtra(EXTRA_DIR);
        dir = (path == null) ? null : new File(path);
        if (dir == null || !dir.isDirectory()) {
            Toast.makeText(this, "找不到这节课的记录", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        exec = Executors.newSingleThreadExecutor();
        loadFiles();

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleView();
            }
        });
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyCurrent();
            }
        });
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareText();
            }
        });
        btnShareAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareAudio();
            }
        });
        btnResummarize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resummarize();
            }
        });
    }

    private void loadFiles() {
        try {
            summary = SessionStore.readText(new File(dir, "summary.md")).trim();
        } catch (Exception e) {
            summary = "";
        }
        try {
            transcript = SessionStore.readText(new File(dir, "transcript.txt")).trim();
        } catch (Exception e) {
            transcript = "";
        }
        render();
    }

    private void render() {
        String subject = SessionStore.subjectOf(dir);
        long startedAt = SessionStore.startedAt(dir);
        long duration = SessionStore.durationOf(dir);
        int words = TextMerge.wordCount(transcript);
        String meta = subject + "　" + SessionStore.formatTime(startedAt);
        if (duration > 0) {
            meta = meta + "　时长 " + TextMerge.fmtDuration(duration);
        }
        if (words > 0) {
            meta = meta + "　约 " + words + " 词";
        }
        txtMeta.setText(meta);

        boolean hasSummary = !summary.isEmpty();
        boolean hasTranscript = !transcript.isEmpty();
        showingSummary = hasSummary;

        if (hasSummary) {
            txtContent.setText(summary);
            btnToggle.setText("看转写原文");
            btnToggle.setEnabled(hasTranscript);
        } else if (hasTranscript) {
            txtContent.setText(transcript);
            btnToggle.setText("看总结（尚未生成）");
            btnToggle.setEnabled(false);
        } else {
            txtContent.setText("这节课没有内容。");
            btnToggle.setEnabled(false);
        }

        btnResummarize.setEnabled(hasTranscript);
        btnShareAudio.setEnabled(new File(dir, "audio.wav").exists());
    }

    private void toggleView() {
        if (summary.isEmpty() || transcript.isEmpty()) {
            return;
        }
        showingSummary = !showingSummary;
        if (showingSummary) {
            txtContent.setText(summary);
            btnToggle.setText("看转写原文");
        } else {
            txtContent.setText(transcript);
            btnToggle.setText("看总结");
        }
        scrollContent.post(new Runnable() {
            @Override
            public void run() {
                scrollContent.scrollTo(0, 0);
            }
        });
    }

    private String currentText() {
        if (showingSummary && !summary.isEmpty()) {
            return summary;
        }
        return transcript;
    }

    private void copyCurrent() {
        String text = currentText();
        if (text.isEmpty()) {
            toast("没有可复制的内容");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("classnote-note", text));
        }
        toast("已复制到剪贴板");
    }

    private void shareText() {
        String text = currentText();
        if (text.isEmpty()) {
            toast("没有可分享的内容");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "课堂笔记 · " + SessionStore.subjectOf(dir));
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "分享课堂笔记"));
    }

    private void shareAudio() {
        File wav = new File(dir, "audio.wav");
        if (!wav.exists()) {
            toast("这节课没有保存录音文件");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", wav);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("audio/wav");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "分享课堂录音"));
        } catch (Exception e) {
            toast("分享录音失败：" + e.getMessage());
        }
    }

    private void resummarize() {
        if (transcript.isEmpty()) {
            toast("没有转写稿，无法重新生成");
            return;
        }
        if (Prefs.llmKey(this).isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("缺少总结 API Key")
                    .setMessage("请先在“设置 → 总结服务”里填写 API Key。")
                    .setPositiveButton("去设置", (dialog, which) ->
                            startActivity(new Intent(this, SettingsActivity.class)))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        final String subject = SessionStore.subjectOf(dir);
        dismissProgress();
        progressDialog = new AlertDialog.Builder(this)
                .setTitle("正在重新生成总结")
                .setMessage("请稍候，通常需要 10~60 秒…")
                .setCancelable(false)
                .create();
        progressDialog.show();
        btnResummarize.setEnabled(false);

        exec.execute(new Runnable() {
            @Override
            public void run() {
                String newSummary = null;
                String error = null;
                try {
                    newSummary = Summarizer.summarize(DetailActivity.this, subject, transcript);
                    SessionStore.writeText(new File(dir, "summary.md"), newSummary);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String s = newSummary;
                final String err = error;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        dismissProgress();
                        if (s != null) {
                            summary = s;
                            render();
                            toast("总结已重新生成");
                        } else {
                            btnResummarize.setEnabled(true);
                            new AlertDialog.Builder(DetailActivity.this)
                                    .setTitle("生成失败")
                                    .setMessage(err == null ? "未知错误" : err)
                                    .setPositiveButton("知道了", null)
                                    .show();
                        }
                    }
                });
            }
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void dismissProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception ignored) {
        }
        progressDialog = null;
    }

    @Override
    protected void onDestroy() {
        dismissProgress();
        ui.removeCallbacksAndMessages(null);
        if (exec != null) {
            exec.shutdown();
        }
        super.onDestroy();
    }
}
