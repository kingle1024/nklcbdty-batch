package com.nklcbdty.batch.nklcbdty.batch.companyfeature.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고 본문에서 키워드를 찾되, 부정 문맥("재택 불가", "no remote work")은 걸러낸다.
 *
 * <p>규칙</p>
 * <ul>
 *   <li>키워드 안의 공백은 0개 이상으로 본다("원격 근무" = "원격근무"). 대소문자는 무시한다.</li>
 *   <li>영문 키워드는 단어 경계에서만 잡는다("wfh" 가 다른 단어 속에서 잡히지 않게).</li>
 *   <li>키워드 뒤 같은 문장(줄·마침표 등으로 끊김) {@value #NEGATION_WINDOW}자 안에 부정어가 있으면 버린다.
 *       단 부정어보다 먼저 "가능"/"available" 이 나오면 살린다("재택 가능(수습 중 불가)").</li>
 *   <li>키워드 바로 앞 {@value #PREFIX_WINDOW}자 안에 영문 부정 접두("no ", "not ", "non-")가 있으면 버린다.</li>
 * </ul>
 * 한 키워드가 여러 번 나오면 부정이 아닌 첫 위치를 쓴다.
 */
public final class KeywordMatcher {

    static final int NEGATION_WINDOW = 20;
    static final int PREFIX_WINDOW = 12;
    private static final int SNIPPET_BEFORE = 40;
    private static final int SNIPPET_AFTER = 60;

    /** 문장(절) 경계. 부정어는 같은 절 안에서만 본다. */
    private static final String CLAUSE_BREAKS = ".!?\n•·;|";

    private static final List<String> NEGATIONS = List.of(
        "불가", "안 됨", "안됨", "안 돼", "안돼", "없음", "없습니다", "없어요", "없으며", "없고",
        "미지원", "미제공", "하지 않", "지 않습니다", "제외",
        // "재택근무를 마다하고 오피스를 찾는 이유" — 제도 안내가 아니라 출근을 권하는 문구(토스 공고)
        "마다하",
        "not available", "not supported", "not offered", "not provided", "not possible", "unavailable",
        "on-site only", "onsite only", "in-office only"
    );

    private static final List<String> PREFIX_NEGATIONS = List.of("no ", "not ", "non-", "non ", "without ");

    private KeywordMatcher() {
    }

    /** 키워드 목록 중 본문에 (부정 문맥이 아닌 채로) 처음 걸리는 것. 목록 순서가 우선순위다. */
    public static Optional<KeywordHit> findFirst(String text, List<String> keywords) {
        if (text == null || text.isEmpty() || keywords == null) {
            return Optional.empty();
        }
        for (String keyword : keywords) {
            Optional<KeywordHit> hit = find(text, keyword);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    static Optional<KeywordHit> find(String text, String keyword) {
        Pattern pattern = toPattern(keyword);
        if (pattern == null) {
            return Optional.empty();
        }
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (!isNegated(text, m.start(), m.end())) {
                return Optional.of(new KeywordHit(keyword.trim(), snippet(text, m.start(), m.end())));
            }
        }
        return Optional.empty();
    }

    static Pattern toPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        List<String> parts = new ArrayList<>();
        for (String part : trimmed.split("\\s+")) {
            parts.add(Pattern.quote(part));
        }
        String regex = String.join("\\s*", parts);
        if (isAsciiWordChar(trimmed.charAt(0))) {
            regex = "(?<![A-Za-z0-9])" + regex;
        }
        if (isAsciiWordChar(trimmed.charAt(trimmed.length() - 1))) {
            regex = regex + "(?![A-Za-z0-9])";
        }
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    static boolean isNegated(String text, int start, int end) {
        int clauseStart = start;
        while (clauseStart > 0 && start - clauseStart < PREFIX_WINDOW
            && CLAUSE_BREAKS.indexOf(text.charAt(clauseStart - 1)) < 0) {
            clauseStart--;
        }
        String prefix = text.substring(clauseStart, start).toLowerCase(Locale.ROOT);
        for (String neg : PREFIX_NEGATIONS) {
            if (prefix.endsWith(neg) || prefix.contains(" " + neg) || prefix.startsWith(neg)) {
                return true;
            }
        }

        int clauseEnd = end;
        while (clauseEnd < text.length() && clauseEnd - end < NEGATION_WINDOW
            && CLAUSE_BREAKS.indexOf(text.charAt(clauseEnd)) < 0) {
            clauseEnd++;
        }
        String after = text.substring(end, clauseEnd).toLowerCase(Locale.ROOT);
        int negationAt = firstIndexOf(after, NEGATIONS);
        if (negationAt < 0) {
            return false;
        }
        int positiveAt = firstPositive(after);
        return positiveAt < 0 || negationAt < positiveAt;
    }

    /** "가능"(앞에 "불"이 없는) 또는 "available"(앞에 "un" 이 없는)의 첫 위치 */
    private static int firstPositive(String after) {
        int best = -1;
        int from = 0;
        while ((from = after.indexOf("가능", from)) >= 0) {
            if (from == 0 || after.charAt(from - 1) != '불') {
                best = from;
                break;
            }
            from++;
        }
        from = 0;
        while ((from = after.indexOf("available", from)) >= 0) {
            boolean un = from >= 2 && after.startsWith("un", from - 2);
            boolean not = from >= 4 && after.startsWith("not ", from - 4);
            if (!un && !not) {
                best = best < 0 ? from : Math.min(best, from);
                break;
            }
            from++;
        }
        return best;
    }

    private static int firstIndexOf(String haystack, List<String> needles) {
        int best = -1;
        for (String needle : needles) {
            int i = haystack.indexOf(needle);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    static String snippet(String text, int start, int end) {
        int from = Math.max(0, start - SNIPPET_BEFORE);
        int to = Math.min(text.length(), end + SNIPPET_AFTER);
        String body = text.substring(from, to).replaceAll("\\s+", " ").trim();
        return (from > 0 ? "…" : "") + body + (to < text.length() ? "…" : "");
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
