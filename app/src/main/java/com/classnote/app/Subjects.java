package com.classnote.app;

/**
 * 学科配置：数学 / 物理 / 工程英语 / 通用。
 * 中文提示词直接写在这里，便于按自己的课程调整。
 */
public final class Subjects {

    public static final String[] NAMES = {"数学", "物理", "工程英语", "通用"};
    public static final String DEFAULT = "通用";

    private Subjects() {
    }

    private static String subjectFocus(String subject) {
        String s = subject == null ? "" : subject.trim();
        if ("数学".equals(s)) {
            return "本课是数学课。重点是：准确定义、定理的条件与结论、公式推导的关键步骤、例题的解法结构。"
                    + "数学符号、上下标、向量/矩阵写法要准确，公式用 LaTeX 行内格式（例如 $\\int_0^1 f(x)\\,dx$、$A^{-1}$、$\\nabla f$）。"
                    + "如果老师的推导被听漏了，请明确写“（推导中间步骤转写不清，待补）”，不要自己补全证明。";
        }
        if ("物理".equals(s)) {
            return "本课是物理课。重点是：物理概念、定律的表达式与适用条件、物理量与单位、近似与理想化假设、例题的受力/能量分析思路。"
                    + "公式用 LaTeX 行内格式并标注单位（例如 $F = m a$，单位 N）。"
                    + "涉及实验、装置、示意图时，用文字复述老师描述的装置结构与测量方法。";
        }
        if ("工程英语".equals(s)) {
            return "本课是工程英语（English for Engineering）课。重点是：专业词汇与固定搭配（英中对照）、课堂讲到的句型与表达、"
                    + "学术/技术写作习惯、口头表达如何改写为规范书面英语。"
                    + "请把老师的英文原话中的地道表达摘出来并给例句；对口语化、重复、语法不完整的句子，给出规范的英文改写。"
                    + "术语表请给出英文 → 中文 → 一个英文例句。";
        }
        return "本课是一门以英文授课的专业课。请把所有知识点整理清楚，术语给英中对照，公式用 LaTeX，例题写清解题思路。";
    }

    private static String sectionFour(String subject) {
        String s = subject == null ? "" : subject.trim();
        if ("工程英语".equals(s)) {
            return "## 四、重要表达与句型\n（英文原句 → 中文 → 使用场景/改写建议）";
        }
        return "## 四、公式 / 定理 / 定律\n（写出表达式，说明每个符号含义、适用条件；用 LaTeX）";
    }

    public static String systemPrompt(String subject) {
        String subj = (subject == null || subject.trim().isEmpty()) ? DEFAULT : subject.trim();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("你是一位资深大学助教，擅长把英文授课内容整理成高质量的中文课堂笔记。\n\n");
        sb.append("输入说明：用户提供的是一节课的英文语音自动转写稿。它可能有同音词错误、专有名词写错、"
                + "口语重复、断句混乱、缺标点、甚至漏句。\n\n");
        sb.append("硬性要求：\n");
        sb.append("1. 只依据转写稿的内容整理，绝不虚构结论、公式、数值或例子；信息不足处写“（转写不清，待补）”。\n");
        sb.append("2. 结合专业上下文纠正明显的语音识别错误（把听错的术语恢复成正确写法），必要时在括号里注明原文。\n");
        sb.append("3. 用中文撰写；专业术语保留英文，首次出现给中文对照。\n");
        sb.append("4. 公式用 LaTeX 行内写法，例如 $E = mc^2$。\n");
        sb.append("5. 直接输出 Markdown 正文，不要输出提示词复述、不要输出思考过程、不要用代码块包住全文。\n");
        sb.append("6. 老师反复强调、重复讲过的地方，要在“易错点”里体现出来。\n\n");
        sb.append("学科重点：\n").append(subjectFocus(subj)).append("\n\n");
        sb.append("输出结构（严格按此 Markdown 标题层级）：\n");
        sb.append("# 课程笔记：").append(subj).append("（英文授课）\n\n");
        sb.append("## 一、一句话概览\n（本课到底讲了什么，1-3 句）\n\n");
        sb.append("## 二、核心知识点\n（按讲课顺序分小节，每条 1-3 句，讲清“是什么 + 为什么 + 怎么用”）\n\n");
        sb.append("## 三、关键术语（英中对照）\n| 英文 | 中文 | 说明 |\n|---|---|---|\n\n");
        sb.append(sectionFour(subj)).append("\n\n");
        sb.append("## 五、例题与解题思路\n（没有例题就写“本课未讲例题”）\n\n");
        sb.append("## 六、易错点与老师强调\n\n");
        sb.append("## 七、课后复习清单\n（3-8 条，可直接执行的复习动作）\n\n");
        sb.append("## 八、作业与待办\n（作业、预习、要交的东西；没有就写“未提及”）\n");
        return sb.toString();
    }

    public static String userPrompt(String subject, String transcript) {
        String subj = (subject == null || subject.trim().isEmpty()) ? DEFAULT : subject.trim();
        return "科目：" + subj + "\n"
                + "下面是本节课的英文授课转写稿（按时间顺序）：\n"
                + "----- 转写稿开始 -----\n"
                + transcript
                + "\n----- 转写稿结束 -----\n\n"
                + "请按系统要求输出本节课的中文课堂笔记 Markdown。";
    }
}
