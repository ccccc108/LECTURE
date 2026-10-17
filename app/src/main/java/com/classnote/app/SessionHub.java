package com.classnote.app;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 录音 / 转写 / 总结过程中，前台服务与界面之间的进程内状态总线。
 * 界面只是监听者：即使界面被销毁，服务仍可继续录音与转写。
 */
public final class SessionHub {

    public interface Listener {
        void onLiveUpdate(String liveText, String status);

        void onSessionFinished(String summary, String transcript, String error, File dir);

        void onError(String message);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<Listener>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final StringBuilder LIVE = new StringBuilder();
    private static final Object LOCK = new Object();

    /** 是否有任务在进行（录音中 或 正在生成总结），用于防止重复开始。 */
    public static volatile boolean busy = false;
    public static volatile boolean recording = false;
    public static volatile long startAt = 0L;
    public static volatile String statusText = "";
    public static volatile String subject = "通用";
    public static volatile File currentDir = null;

    private SessionHub() {
    }

    public static void addListener(Listener l) {
        if (l != null && !LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Listener l) {
        if (l != null) {
            LISTENERS.remove(l);
        }
    }

    public static String liveText() {
        synchronized (LOCK) {
            return LIVE.toString();
        }
    }

    public static void reset(String subj) {
        synchronized (LOCK) {
            LIVE.setLength(0);
        }
        subject = (subj == null || subj.isEmpty()) ? "通用" : subj;
        startAt = System.currentTimeMillis();
        statusText = "";
        currentDir = null;
        recording = false;
        postLive("");
    }

    public static void appendSegment(String seg) {
        if (seg == null || seg.isEmpty()) {
            return;
        }
        String full;
        synchronized (LOCK) {
            if (LIVE.length() > 0) {
                LIVE.append('\n');
            }
            LIVE.append(seg);
            full = LIVE.toString();
        }
        postLive(full);
    }

    public static void setStatus(String s) {
        statusText = (s == null) ? "" : s;
        postLive(liveText());
    }

    private static void postLive(final String full) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : LISTENERS) {
                    l.onLiveUpdate(full, statusText);
                }
            }
        });
    }

    public static void postFinished(final String summary, final String transcript,
                                    final String error, final File dir) {
        recording = false;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : LISTENERS) {
                    l.onSessionFinished(summary, transcript, error, dir);
                }
            }
        });
    }

    public static void postError(final String msg) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : LISTENERS) {
                    l.onError(msg);
                }
            }
        });
    }
}
