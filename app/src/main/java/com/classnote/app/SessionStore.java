package com.classnote.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 每节课一个目录：files/sessions/20260601_140500/
 * 内含 meta.json、transcript.txt、summary.md、audio.wav
 */
public final class SessionStore {

    private SessionStore() {
    }

    public static File baseDir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "sessions");
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    public static File createSession(Context c, String subject) {
        String id = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(baseDir(c), id);
        int suffix = 1;
        while (dir.exists()) {
            dir = new File(baseDir(c), id + "_" + suffix);
            suffix++;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        saveMeta(c, dir, subject, System.currentTimeMillis(), 0);
        return dir;
    }

    public static void saveMeta(Context c, File dir, String subject, long startedAt, long durationMs) {
        JSONObject o = new JSONObject();
        try {
            o.put("subject", subject == null ? "通用" : subject);
            o.put("startedAt", startedAt);
            o.put("durationMs", durationMs);
            o.put("app", "ClassNote");
        } catch (Exception ignored) {
        }
        try {
            writeText(new File(dir, "meta.json"), o.toString());
        } catch (Exception ignored) {
        }
    }

    public static JSONObject loadMeta(File dir) {
        try {
            return new JSONObject(readText(new File(dir, "meta.json")));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static List<File> listSessions(Context c) {
        List<File> result = new ArrayList<File>();
        File[] files = baseDir(c).listFiles();
        if (files == null) {
            return result;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                result.add(f);
            }
        }
        // 目录名是时间戳，倒序即最新在前
        java.util.Collections.sort(result, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName());
            }
        });
        return result;
    }

    public static String readText(File f) throws IOException {
        if (f == null || !f.exists()) {
            return "";
        }
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(64, f.length()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static String subjectOf(File dir) {
        String s = loadMeta(dir).optString("subject", "");
        return (s == null || s.isEmpty()) ? "未分类" : s;
    }

    public static long startedAt(File dir) {
        long v = loadMeta(dir).optLong("startedAt", 0L);
        if (v > 0) {
            return v;
        }
        // 回退：从目录名解析
        try {
            return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .parse(dir.getName().substring(0, 15)).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static long durationOf(File dir) {
        return loadMeta(dir).optLong("durationMs", 0L);
    }

    public static String formatTime(long millis) {
        if (millis <= 0) {
            return "未知时间";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(millis));
    }
}
