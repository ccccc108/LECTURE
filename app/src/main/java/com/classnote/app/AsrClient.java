package com.classnote.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 云端语音转写客户端：任何"OpenAI 兼容"的 /audio/transcriptions 接口都能用。
 * 默认配置为硅基流动 SenseVoice（免费额度、对英文授课够用）。
 */
public final class AsrClient {

    public static final class Result {
        public final boolean ok;
        public final String text;
        public final String error;

        private Result(boolean ok, String text, String error) {
            this.ok = ok;
            this.text = text;
            this.error = error;
        }

        public static Result success(String text) {
            return new Result(true, text == null ? "" : text, null);
        }

        public static Result failure(String error) {
            return new Result(false, "", error == null ? "未知错误" : error);
        }
    }

    private AsrClient() {
    }

    /** 只用分片本身的字节做转写（分片是内存中的 PCM，不需要先落盘）。 */
    public static Result transcribe(Context ctx, byte[] pcm16kMono) {
        if (pcm16kMono == null || pcm16kMono.length < 4000) {
            return Result.success("");
        }
        String key = Prefs.asrKey(ctx);
        if (key.isEmpty()) {
            return Result.failure("未配置转写 API Key（设置 → 转写服务）");
        }
        String base = Prefs.asrBase(ctx);
        String model = Prefs.asrModel(ctx);
        String url = Http.joinUrl(base, "/audio/transcriptions");

        byte[] wav = WavUtil.pcm16ToWav(pcm16kMono, AudioCapture.SAMPLE_RATE, 1);
        String boundary = "----ClassNote" + System.currentTimeMillis();

        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("model", model);

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer " + key);

        try {
            byte[] body = Http.multipart(boundary, fields, "file", "chunk.wav", "audio/wav", wav);
            String resp = Http.post(url, headers, body,
                    "multipart/form-data; boundary=" + boundary, 60000);
            JSONObject o = new JSONObject(resp);
            String text = o.optString("text", "");
            if (text == null) {
                text = "";
            }
            text = text.trim();
            if (text.isEmpty()) {
                // 有些兼容服务在失败时返回 {"error": ...}
                String err = o.optString("error", "");
                if (err != null && !err.isEmpty()) {
                    return Result.failure("转写失败：" + err);
                }
            }
            return Result.success(text);
        } catch (Exception e) {
            return Result.failure("转写请求失败：" + Http.humanize(e));
        }
    }
}
