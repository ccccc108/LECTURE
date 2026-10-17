package com.classnote.app;

/**
 * 极简 JSON 拼接工具。
 * 请求体只用字符串拼装（避免 org.json 的受检异常），响应解析仍使用系统自带 org.json。
 */
public final class Json {

    private Json() {
    }

    /** 把字符串转成合法的 JSON 字符串字面量（含首尾引号）。 */
    public static String str(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * 构造 OpenAI 兼容的 /chat/completions 请求体。
     *
     * @param disableThinking 仅在 DeepSeek 官方接口上追加 thinking.type=disabled，避免长时间思考拖慢总结
     */
    public static String chatBody(String model, String system, String user,
                                  double temperature, int maxTokens, boolean disableThinking) {
        StringBuilder b = new StringBuilder(1024);
        b.append('{');
        b.append("\"model\":").append(str(model)).append(',');
        b.append("\"messages\":[");
        b.append("{\"role\":\"system\",\"content\":").append(str(system)).append("},");
        b.append("{\"role\":\"user\",\"content\":").append(str(user)).append("}");
        b.append("],");
        b.append("\"temperature\":").append(temperature).append(',');
        b.append("\"max_tokens\":").append(maxTokens).append(',');
        b.append("\"stream\":false");
        if (disableThinking) {
            b.append(",\"thinking\":{\"type\":\"disabled\"}");
        }
        b.append('}');
        return b.toString();
    }
}
