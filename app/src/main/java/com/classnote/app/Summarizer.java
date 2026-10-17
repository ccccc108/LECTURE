package com.classnote.app;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 课后总结：把整节课的转写稿交给对话模型，按学科生成结构化中文笔记。
 * 转写稿很长时先分段提炼、再合并（DeepSeek 上下文很大，正常一节课用不到分段）。
 */
public final class Summarizer {

    /** 单次请求的最大字符数，超出则先分段提炼再合并。 */
    private static final int SINGLE_SHOT_LIMIT = 120000;
    private static final int PART_LIMIT = 60000;

    private Summarizer() {
    }

    public static String summarize(Context ctx, String subject, String transcript) throws IOException {
        String clean = (transcript == null) ? "" : transcript.trim();
        if (clean.isEmpty()) {
            throw new IOException("转写稿为空，无法生成总结");
        }
        String system = Subjects.systemPrompt(subject);

        if (clean.length() <= SINGLE_SHOT_LIMIT) {
            return LlmClient.chat(ctx, system, Subjects.userPrompt(subject, clean), 6000);
        }

        List<String> parts = split(clean, PART_LIMIT);
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String mapSystem = "你是课堂记录整理助手。请把输入的第 " + (i + 1) + "/" + parts.size()
                    + " 段英文授课转写稿整理成中文要点清单：只保留知识点、术语、公式、例题、老师强调的话；"
                    + "保留英文术语；纠正明显的识别错误；不要编造；不要输出思考过程。";
            String mapped = LlmClient.chat(ctx, mapSystem, parts.get(i), 2000);
            notes.append("\n\n【第 ").append(i + 1).append(" 段要点】\n").append(mapped);
        }

        String mergedUser = "下面是同一堂课按时间顺序整理的要点清单（由 " + parts.size()
                + " 个片段分别提炼）。请合并、去重、按逻辑重排，输出完整的课堂笔记。\n" + notes;
        return LlmClient.chat(ctx, system, mergedUser, 6000);
    }

    private static List<String> split(String text, int maxChars) {
        List<String> parts = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxChars);
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > start + maxChars / 2) {
                    end = nl;
                }
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }
}
