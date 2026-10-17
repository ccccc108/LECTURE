package com.classnote.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 课后总结用的对话模型客户端：任何"OpenAI 兼容"的 /chat/completions 接口都能用。
 * 默认配置为 DeepSeek 官方接口（deepseek-flash）。
 */
public final class LlmClient {

    private LlmClient() {
    }

    public static String chat(Context ctx, String systemPrompt, String userPrompt, int maxTokens)
            throws IOException {
        String key = Prefs.llmKey(ctx);
        if (key.isEmpty()) {
            throw new IOException("未配置总结 API Key（设置 → 总结服务）");
        }
        String base = Prefs.llmBase(ctx);
        String model = Prefs.llmModel(ctx);
        String url = Http.joinUrl(base, "/chat/completions");

        boolean looksDeepseek = base.toLowerCase(Locale.US).contains("deepseek");
        String body = Json.chatBody(model, systemPrompt, userPrompt, 0.3, maxTokens, looksDeepseek);

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer " + key);

        String resp;
        try {
            resp = Http.post(url, headers, body.getBytes("UTF-8"), "application/json", 300000);
        } catch (IOException e) {
            throw new IOException(Http.humanize(e));
        }

        try {
            JSONObject o = new JSONObject(resp);
            JSONArray choices = o.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                String err = o.optString("error", "");
                throw new IOException("模型没有返回内容" + (err.isEmpty() ? "" : "：" + err));
            }
            JSONObject first = choices.optJSONObject(0);
            JSONObject message = (first == null) ? null : first.optJSONObject("message");
            String content = (message == null) ? "" : message.optString("content", "");
            if (content == null) {
                content = "";
            }
            content = content.trim();
            if (content.isEmpty()) {
                throw new IOException("模型返回了空内容（可能是内容被过滤，或模型名不被支持）");
            }
            return content;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("解析模型返回失败：" + Http.humanize(e));
        }
    }

    /** 设置页的连通性测试：发一个极小的请求，验证 Key / Base URL / 模型名。 */
    public static String test(Context ctx) throws IOException {
        return chat(ctx, "你是一个测试助手，只回复四个字：连接正常。", "请回复：连接正常。", 64);
    }
}
