package com.zhixue.engine.safety;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入/输出内容护栏。
 * <p>
 * 覆盖三类风险：违法违规内容、提示词注入、以及超出教育场景的敏感建议。
 * 命中注入特征时不直接拒绝（避免误伤正常提问），而是标记后由系统提示词强制降权；
 * 命中违法内容则直接拦截，保证 AI 输出可控。
 */
@Component
public class ContentGuard {

    private static final List<Pattern> BLOCKED = List.of(
            Pattern.compile("(制作|合成|提炼).{0,6}(炸弹|炸药|毒品|冰毒|枪支)"),
            Pattern.compile("(如何|怎么).{0,6}(自杀|自残)"),
            Pattern.compile("(儿童|未成年).{0,4}(色情|裸照)"),
            Pattern.compile("(入侵|攻击).{0,8}(学校|教务|考试).{0,6}系统")
    );

    private static final List<Pattern> INJECTION = List.of(
            Pattern.compile("(忽略|忘记|无视).{0,8}(之前|以上|前面|先前).{0,6}(指令|提示|规则|设定)"),
            Pattern.compile("(ignore|disregard|forget).{0,20}(previous|above|prior).{0,20}(instruction|prompt|rule)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(输出|打印|告诉我|显示).{0,8}(系统提示词|system prompt|初始指令)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(开发者模式|DAN模式|越狱模式|jailbreak)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(扮演|假装).{0,6}(不受限制|没有限制|无限制)的?(AI|模型|助手)")
    );

    private static final List<Pattern> SENSITIVE_ADVICE = List.of(
            Pattern.compile("(抑郁症|自杀|自残|精神分裂)"),
            Pattern.compile("(处方|用药剂量|停药|抗抑郁药)"),
            Pattern.compile("(投资建议|买入|卖出|涨停|荐股)")
    );

    private static final int MAX_INPUT_CHARS = 6000;

    public record Verdict(boolean allowed, String message, boolean injected, String cleanedText) {
    }

    public Verdict inspect(String rawText) {
        String trimmed = rawText == null ? "" : rawText.trim();
        final String text = trimmed.length() > MAX_INPUT_CHARS
                ? trimmed.substring(0, MAX_INPUT_CHARS)
                : trimmed;
        for (Pattern pattern : BLOCKED) {
            if (pattern.matcher(text).find()) {
                return new Verdict(false,
                        "这个问题涉及违法违规内容，我无法提供帮助。如果你有学习上的困惑，可以换个角度问我。",
                        false, text);
            }
        }
        boolean injected = INJECTION.stream().anyMatch(p -> p.matcher(text).find());
        return new Verdict(true, null, injected, text);
    }

    /**
     * 输出后处理：附加必要的免责说明，避免 AI 在医疗/法律/金融等高风险话题上被当作专业建议。
     */
    public String postProcess(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        List<String> notices = new ArrayList<>();
        for (Pattern pattern : SENSITIVE_ADVICE) {
            if (pattern.matcher(content).find()) {
                notices.add("以上内容仅用于学习讨论，不构成医疗、法律或投资建议；如涉及真实困扰，请咨询专业人士。");
                break;
            }
        }
        if (notices.isEmpty()) {
            return content;
        }
        return content + "\n\n> " + String.join(" ", notices);
    }
}
