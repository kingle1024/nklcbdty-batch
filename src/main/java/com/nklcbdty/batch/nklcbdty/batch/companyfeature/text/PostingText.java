package com.nklcbdty.batch.nklcbdty.batch.companyfeature.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 공고 HTML → 키워드 매칭용 평문.
 *
 * <p>블록 요소마다 줄을 바꿔 둔다. KeywordMatcher 가 줄을 문장 경계로 보기 때문에, 한 줄짜리로 이어 붙이면
 * "재택근무 가능" 다음 줄의 "수습기간 불가" 같은 다른 항목의 부정어가 앞 줄 키워드에 붙어 버린다.</p>
 *
 * <p>사이트 공통 메뉴·푸터(nav/header/footer)와 스크립트는 뺀다. 메뉴의 "복지" 같은 글자가 모든 공고에서
 * 잡히지 않게 하기 위해서다. 같은 이유로 "다른 공고/관련 공고" 목록(aside, related·recommend·similar 클래스)도
 * 뺀다 — 쿠팡 상세페이지 옆 목록의 "일반 사무 (일 8시간 재택 근무)" 가 엉뚱한 공고의 재택 근거로 잡혔다.</p>
 */
public final class PostingText {

    private static final String BLOCKS = "p, li, br, div, section, article, h1, h2, h3, h4, h5, h6, tr, dt, dd, ul, ol";
    private static final String CHROME = "script, style, noscript, template, svg, nav, header, footer, iframe, aside,"
        + " [class*=related], [class*=recommend], [class*=similar]";

    private PostingText() {
    }

    public static String fromHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        doc.select(CHROME).remove();
        for (Element block : doc.select(BLOCKS)) {
            block.prependText("\n");
            block.appendText("\n");
        }
        Element root = doc.body() == null ? doc : doc.body();
        return normalize(root.wholeText());
    }

    static String normalize(String text) {
        return text
            .replace(' ', ' ')
            .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
            .replaceAll(" *\\n[\\s]*", "\n")
            .trim();
    }
}
