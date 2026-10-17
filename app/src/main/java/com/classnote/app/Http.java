package com.classnote.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 HttpURLConnection 的最小 HTTP 客户端：JSON POST + multipart 上传。
 * 不依赖第三方网络库，减少构建风险与安装包体积。
 */
public final class Http {

    private Http() {
    }

    public static String joinUrl(String base, String path) {
        String b = (base == null) ? "" : base.trim();
        if (!b.startsWith("http://") && !b.startsWith("https://")) {
            b = "https://" + b;
        }
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        String p = (path == null) ? "" : path;
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return b + p;
    }

    public static String post(String url, Map<String, String> headers, byte[] body,
                              String contentType, int readTimeoutMs) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "ClassNote/1.0 (Android)");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.setFixedLengthStreamingMode(body.length);
            OutputStream os = conn.getOutputStream();
            try {
                os.write(body);
                os.flush();
            } finally {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " " + shorten(resp));
            }
            return resp;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static byte[] multipart(String boundary, Map<String, String> fields,
                                   String fileField, String fileName, String mimeType,
                                   byte[] fileData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(fileData.length + 4096);
        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                writeAscii(out, "--" + boundary + "\r\n");
                writeAscii(out, "Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n");
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                writeAscii(out, "\r\n");
            }
        }
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                + fileName + "\"\r\n");
        writeAscii(out, "Content-Type: " + mimeType + "\r\n\r\n");
        out.write(fileData);
        writeAscii(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void writeAscii(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
                // 防御：教室里常见的 WiFi 登录页会返回超大 HTML，直接截断避免 OOM
                if (bos.size() > 8 * 1024 * 1024) {
                    throw new IOException("服务器返回内容过大（可能连到了需要登录的 WiFi 门户页）");
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try {
                is.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > 300) {
            t = t.substring(0, 300) + "…";
        }
        return t;
    }

    /** 把常见 HTTP 错误翻译成给用户看的提示。 */
    public static String humanize(Throwable t) {
        String msg = (t == null) ? "" : String.valueOf(t.getMessage());
        if (msg.contains("401") || msg.contains("403")) {
            return "鉴权失败（" + codeOf(msg) + "）：请检查 API Key 是否正确、是否有该模型权限";
        }
        if (msg.contains("404")) {
            return "接口地址不存在（404）：请检查 Base URL 是否少了 /v1 或路径写错";
        }
        if (msg.contains("429")) {
            return "请求过于频繁或额度不足（429）：请稍后再试或检查账户余额";
        }
        if (msg.contains("400")) {
            return "请求被拒绝（400）：模型名或参数可能不被该服务支持 " + msg;
        }
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("timeout")) {
            return "网络超时：请检查平板网络，或把分片时长调小一些";
        }
        if (msg.contains("Unable to resolve host") || msg.contains("UnknownHost")) {
            return "无法解析服务器地址：请检查网络或 Base URL";
        }
        return (msg.isEmpty() ? "未知错误" : msg);
    }

    private static String codeOf(String msg) {
        int i = msg.indexOf("HTTP ");
        if (i >= 0 && msg.length() > i + 8) {
            return msg.substring(i + 5, i + 8);
        }
        return "401/403";
    }
}
