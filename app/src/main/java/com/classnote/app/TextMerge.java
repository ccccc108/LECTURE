package com.classnote.app;

import java.util.Locale;

/**
 * 分片转写拼接：连续两个音频分片之间有重叠，需要把下一段开头重复的单词去掉。
 */
public final class TextMerge {

    private TextMerge() {
    }

    /**
     * @param previous 上一段的完整识别结果（原样）
     * @param next     当前段的完整识别结果
     * @return 需要追加到转写稿中的新内容（可能为空串）
     */
    public static String merge(String previous, String next) {
        String cur = (next == null) ? "" : next.trim();
        if (cur.isEmpty()) {
            return "";
        }
        String prev = (previous == null) ? "" : previous.trim();
        if (prev.isEmpty()) {
            return cur;
        }
        String[] a = prev.split("\\s+");
        String[] b = cur.split("\\s+");
        if (b.length == 0) {
            return "";
        }
        int max = Math.min(Math.min(a.length, b.length), 30);
        for (int k = max; k >= 2; k--) {
            boolean same = true;
            int chars = 0;
            for (int i = 0; i < k; i++) {
                String na = norm(a[a.length - k + i]);
                String nb = norm(b[i]);
                // 纯标点/纯符号的 token 归一化后是空串，不能当作"相同"
                if (na.isEmpty() || !na.equals(nb)) {
                    same = false;
                    break;
                }
                chars += na.length();
            }
            // 2 个词的匹配要求累计字符数够长，降低"老师本来就重复说一遍"被误删的概率
            if (same && (k >= 3 || chars >= 8)) {
                return joinFrom(b, k);
            }
        }
        return cur;
    }

    static String norm(String word) {
        StringBuilder sb = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static String joinFrom(String[] words, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < words.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(words[i]);
        }
        return sb.toString();
    }

    /** 估算文本对应的英文单词数，用于统计和提示。 */
    public static int wordCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    public static String fmtDuration(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }
}
