package com.classnote.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 前台服务：负责整节课的采集 → 分片转写 → 保存 → 课后总结。
 * 即使切到别的应用或锁屏，录音和转写也会继续（需在系统里允许后台运行）。
 */
public class RecordService extends Service implements AudioCapture.Listener {

    public static final String ACTION_START = "com.classnote.app.action.START";
    public static final String ACTION_STOP = "com.classnote.app.action.STOP";
    public static final String EXTRA_SUBJECT = "extra_subject";

    private static final String TAG = "ClassNote";
    private static final String CHANNEL_REC = "recording";
    private static final String CHANNEL_DONE = "done";
    private static final int NOTIF_REC = 1001;
    private static final int NOTIF_DONE = 1002;
    /** 排队等待转写的分片上限：防止网络慢时队列无限增长把内存吃爆（每片约 0.5MB）。 */
    private static final int MAX_PENDING_CHUNKS = 40;

    private ThreadPoolExecutor worker;
    private AudioCapture capture;
    private NotificationManager nm;
    private PowerManager.WakeLock wakeLock;

    private final StringBuilder transcript = new StringBuilder();
    private String lastSegment = "";
    private File sessionDir;
    private File pcmFile;
    private String subject = Subjects.DEFAULT;
    private long startedAt;
    private int chunkCount;
    private int failCount;
    private int droppedCount;

    private volatile boolean stopping;
    private volatile boolean finished;

    @Override
    public void onCreate() {
        super.onCreate();
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent == null) ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            if (capture == null && sessionDir == null && !SessionHub.busy) {
                // 没有任何正在进行的任务，直接退出，避免生成一条空记录
                stopSelf();
            } else {
                stopRecording();
            }
        } else if (ACTION_START.equals(action)) {
            if (!SessionHub.busy) {
                startRecording(intent);
            }
        }
        return START_NOT_STICKY;
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startRecording(Intent intent) {
        if (!hasMicPermission()) {
            SessionHub.postError("没有麦克风权限，无法录音。请在系统设置里允许本应用使用麦克风。");
            stopSelf();
            return;
        }
        SessionHub.busy = true;
        String s = (intent == null) ? null : intent.getStringExtra(EXTRA_SUBJECT);
        subject = (s == null || s.trim().isEmpty()) ? Subjects.DEFAULT : s.trim();
        startedAt = System.currentTimeMillis();
        transcript.setLength(0);
        lastSegment = "";
        chunkCount = 0;
        failCount = 0;
        droppedCount = 0;
        stopping = false;
        finished = false;

        int chunkSeconds = Prefs.chunkSeconds(this);
        try {
            sessionDir = SessionStore.createSession(this, subject);
            pcmFile = new File(sessionDir, "audio.pcm");
            SessionHub.reset(subject);
            SessionHub.currentDir = sessionDir;
            SessionHub.startAt = startedAt;
            SessionHub.recording = true;
            startForegroundCompat(buildRecordingNotification("准备录音…"));
            acquireWakeLock();
            capture = new AudioCapture(pcmFile, chunkSeconds, this);
            capture.start();
            SessionHub.setStatus("正在录音（每 " + chunkSeconds + " 秒转写一段）");
        } catch (Exception e) {
            Log.w(TAG, "开始录音失败", e);
            failAndFinish("无法开始录音：" + e.getMessage());
        }
    }

    @Override
    public void onChunk(final byte[] pcm) {
        final int index = ++chunkCount;
        if (worker == null || worker.isShutdown()) {
            return;
        }
        // 网络明显跟不上（排队过多）时丢弃新分片并计数，避免内存无限增长；
        // 音频文件本身是完整的，丢的只是这几段的文字。
        if (worker.getQueue().size() >= MAX_PENDING_CHUNKS) {
            droppedCount++;
            SessionHub.setStatus("网络较慢，已跳过 " + droppedCount + " 段转写（录音不受影响）");
            return;
        }
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    if (finished) {
                        return;
                    }
                    doTranscribeChunk(pcm, index);
                }
            });
        } catch (Exception e) {
            // 线程池已关闭（正在收尾），忽略本次分片，音频文件里仍然有完整录音
            Log.w(TAG, "转写任务提交失败", e);
        }
    }

    @Override
    public void onError(String message) {
        SessionHub.postError("麦克风错误：" + message);
        if (!stopping) {
            stopRecording();
        }
    }

    private void doTranscribeChunk(byte[] pcm, int index) {
        AsrClient.Result result = null;
        // 正在收尾时只试一次，避免"结束"被两次超时拖成几分钟
        int attempts = stopping ? 1 : 2;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            result = AsrClient.transcribe(RecordService.this, pcm);
            if (result.ok) {
                break;
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (result == null) {
            return;
        }
        if (!result.ok) {
            failCount++;
            SessionHub.setStatus("第 " + index + " 段转写失败（累计 " + failCount + " 段）：" + result.error);
            return;
        }
        if (result.text.isEmpty()) {
            return;
        }
        String merged = TextMerge.merge(lastSegment, result.text);
        lastSegment = result.text;
        if (!merged.isEmpty()) {
            transcript.append(merged).append('\n');
            SessionHub.appendSegment(merged);
        }
        String status = "已转写 " + chunkCount + " 段";
        if (failCount > 0) {
            status = status + "，失败 " + failCount + " 段";
        }
        SessionHub.setStatus(status);
        updateRecordingNotification(status + " · 录音中");
    }

    private void stopRecording() {
        if (stopping || finished) {
            return;
        }
        stopping = true;
        SessionHub.setStatus("正在收尾…");
        final AudioCapture cap = capture;
        // 先让采集线程真正停下（会把最后不足一片的音频冲出来），再排队做收尾，
        // 这样收尾任务一定排在所有转写任务之后。
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (cap != null) {
                    cap.stop();
                }
                if (worker != null) {
                    try {
                        if (!worker.isShutdown() && !worker.isTerminated()) {
                            worker.execute(new Runnable() {
                                @Override
                                public void run() {
                                    finalizeSession();
                                }
                            });
                            return;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "收尾任务排队失败，直接就地收尾", e);
                    }
                }
                // 线程池不可用时直接收尾，避免 SessionHub.busy 永远卡在 true
                finalizeSession();
            }
        }, "classnote-finalize").start();
    }

    private void finalizeSession() {
        if (finished) {
            return;
        }
        finished = true;
        long duration = System.currentTimeMillis() - startedAt;
        final File dir = sessionDir;
        String text = transcript.toString().trim();

        try {
            if (dir != null) {
                SessionStore.writeText(new File(dir, "transcript.txt"), text);
                if (pcmFile != null && pcmFile.exists() && pcmFile.length() > 1000) {
                    try {
                        WavUtil.writeWavFromPcmFile(pcmFile, new File(dir, "audio.wav"),
                                AudioCapture.SAMPLE_RATE, 1);
                        //noinspection ResultOfMethodCallIgnored
                        pcmFile.delete();
                    } catch (Exception e) {
                        Log.w(TAG, "封装 WAV 失败", e);
                    }
                }
                SessionStore.saveMeta(this, dir, subject, startedAt, duration);
            }
        } catch (Exception e) {
            Log.w(TAG, "保存转写稿失败", e);
        }

        String summary = null;
        String message = null;
        boolean wantSummary = !Prefs.llmKey(this).isEmpty();

        if (text.isEmpty()) {
            if (failCount > 0) {
                message = "没有转写成功的内容（" + failCount + " 段全部失败）。请检查转写 API Key、网络与额度。";
            } else {
                message = "没有识别到语音内容。请确认老师在说话、平板没有静音，并检查转写服务配置。";
            }
        } else if (wantSummary) {
            SessionHub.setStatus("正在生成课后总结（约 10-60 秒）…");
            updateRecordingNotification("正在生成课后总结…");
            try {
                summary = Summarizer.summarize(this, subject, text);
                if (dir != null) {
                    SessionStore.writeText(new File(dir, "summary.md"), summary);
                }
            } catch (Exception e) {
                Log.w(TAG, "生成总结失败", e);
                message = "总结生成失败：" + e.getMessage()
                        + "。转写稿已保存，可在“历史记录”里点“重新生成总结”。";
            }
        } else {
            message = "转写完成并已保存。未配置总结 API Key，暂未生成总结（填写后可在历史记录里重新生成）。";
        }

        SessionHub.busy = false;
        SessionHub.recording = false;
        SessionHub.postFinished(summary, text, message, dir);
        if (summary != null && dir != null) {
            postDoneNotification(dir);
        }
        shutdownSelf();
    }

    private void failAndFinish(String message) {
        SessionHub.busy = false;
        SessionHub.recording = false;
        SessionHub.postFinished(null, "", message, sessionDir);
        shutdownSelf();
    }

    private void shutdownSelf() {
        releaseWakeLock();
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        try {
            stopSelf();
        } catch (Exception ignored) {
        }
        if (worker != null) {
            try {
                worker.shutdown();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        final AudioCapture cap = capture;
        // 录音线程的 join 不能放在主线程，否则可能 ANR
        if (cap != null && cap.isRunning()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        cap.stop();
                    } catch (Exception ignored) {
                    }
                }
            }, "classnote-destroy-stop").start();
        }
        releaseWakeLock();
        if (worker != null) {
            try {
                worker.shutdownNow();
            } catch (Exception ignored) {
            }
        }
        // 异常销毁（被系统回收 / 被强行停止）时，避免界面一直卡在"处理中"
        if (!finished) {
            SessionHub.busy = false;
            SessionHub.recording = false;
        }
        super.onDestroy();
    }

    // ---------- 通知 ----------

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || nm == null) {
            return;
        }
        NotificationChannel rec = new NotificationChannel(CHANNEL_REC, "录音与实时转写",
                NotificationManager.IMPORTANCE_LOW);
        rec.setShowBadge(false);
        rec.setDescription("录音过程中的常驻通知");
        NotificationChannel done = new NotificationChannel(CHANNEL_DONE, "课后总结",
                NotificationManager.IMPORTANCE_DEFAULT);
        done.setDescription("总结生成完成的通知");
        nm.createNotificationChannel(rec);
        nm.createNotificationChannel(done);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_REC, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_REC, notification);
        }
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildRecordingNotification(String text) {
        Intent stop = new Intent(this, RecordService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_REC)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("课堂速记 · 正在录音")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, "结束并生成总结", stopPi)
                .build();
    }

    private void updateRecordingNotification(String text) {
        if (nm == null) {
            return;
        }
        try {
            nm.notify(NOTIF_REC, buildRecordingNotification(text));
        } catch (Exception ignored) {
        }
    }

    private void postDoneNotification(File dir) {
        if (nm == null) {
            return;
        }
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_DIR, dir.getAbsolutePath());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("课后总结已生成")
                .setContentText(subject + " · 点击查看课堂笔记")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        try {
            nm.notify(NOTIF_DONE, n);
        } catch (Exception ignored) {
        }
    }

    // ---------- 电源 ----------

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                return;
            }
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClassNote:recording");
            wakeLock.setReferenceCounted(false);
            // 最长 3 小时，避免异常情况下一直占着锁
            wakeLock.acquire(3 * 60 * 60 * 1000L);
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {
        }
        wakeLock = null;
    }
}
