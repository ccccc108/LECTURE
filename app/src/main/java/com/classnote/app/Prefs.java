package com.classnote.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 轻量配置存取。所有设置都保存在应用私有 SharedPreferences 中，不会被上传。
 */
public final class Prefs {

    public static final String DEFAULT_ASR_BASE = "https://api.siliconflow.cn/v1";
    public static final String DEFAULT_ASR_MODEL = "FunAudioLLM/SenseVoiceSmall";
    public static final String DEFAULT_LLM_BASE = "https://api.deepseek.com";
    public static final String DEFAULT_LLM_MODEL = "deepseek-flash";

    private static final String NAME = "classnote_settings";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static String get(Context c, String key, String def) {
        String v = sp(c).getString(key, null);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        return v.trim();
    }

    public static String asrBase(Context c) {
        return get(c, "asr_base", DEFAULT_ASR_BASE);
    }

    public static String asrKey(Context c) {
        return get(c, "asr_key", "");
    }

    public static String asrModel(Context c) {
        return get(c, "asr_model", DEFAULT_ASR_MODEL);
    }

    public static String llmBase(Context c) {
        return get(c, "llm_base", DEFAULT_LLM_BASE);
    }

    public static String llmKey(Context c) {
        return get(c, "llm_key", "");
    }

    public static String llmModel(Context c) {
        return get(c, "llm_model", DEFAULT_LLM_MODEL);
    }

    /** 每个转写片段的秒数，限定在 8~30 秒之间，默认 15 秒。 */
    public static int chunkSeconds(Context c) {
        String s = sp(c).getString("chunk_seconds", null);
        int v;
        try {
            v = (s == null) ? 15 : Integer.parseInt(s.trim());
        } catch (Exception e) {
            v = 15;
        }
        if (v < 8) {
            v = 8;
        }
        if (v > 30) {
            v = 30;
        }
        return v;
    }

    /** 上次使用的科目，下次打开时默认选中。 */
    public static String lastSubject(Context c) {
        return get(c, "last_subject", "");
    }

    public static void put(Context c, String key, String value) {
        sp(c).edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    public static void reset(Context c) {
        sp(c).edit().clear().apply();
    }
}
