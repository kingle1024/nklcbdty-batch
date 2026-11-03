package com.nklcbdty.batch.nklcbdty.batch.companyfeature.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor.FeatureHit;
import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor.WelfareCategoryRule;
import com.nklcbdty.batch.nklcbdty.batch.companyfeature.text.PostingText;

class PostingFeatureExtractorTest {

    private static final List<WelfareCategoryRule> CATEGORIES = List.of(
        new WelfareCategoryRule("MEAL", List.of("식대", "사내 식당")),
        new WelfareCategoryRule("EQUIPMENT", List.of("장비 지원", "맥북")),
        new WelfareCategoryRule("HEALTH", List.of("건강검진"))
    );

    @Test
    @DisplayName("공고 HTML 에서 근무형태 태그와 복지 카테고리를 근거와 함께 뽑는다")
    void extractsFromHtml() {
        String html = "<html><body>"
            + "<nav>복지 | 채용 | 재택근무 이야기</nav>"
            + "<h2>근무 환경</h2><ul><li>주 2회 재택근무</li><li>최신 맥북 지급</li></ul>"
            + "<h2>복지</h2><ul><li>식대 월 20만원</li><li>건강검진 미지원</li></ul>"
            + "<footer>재택근무 FAQ</footer>"
            + "</body></html>";

        List<FeatureHit> hits = PostingFeatureExtractor.extract(PostingText.fromHtml(html), CATEGORIES);

        assertThat(hits).extracting(FeatureHit::featureType, FeatureHit::featureCode)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("WORK_STYLE", "REMOTE"),
                org.assertj.core.groups.Tuple.tuple("WELFARE", "MEAL"),
                org.assertj.core.groups.Tuple.tuple("WELFARE", "EQUIPMENT"));
        assertThat(hits.get(0).hit().snippet()).contains("주 2회 재택근무");
    }

    @Test
    @DisplayName("메뉴·푸터에만 있는 재택 글자는 근거가 되지 않는다")
    void ignoresSiteChrome() {
        String html = "<body><header>재택근무 문화</header><main><p>서울 오피스 근무</p></main>"
            + "<footer>원격 근무 안내</footer></body>";

        assertThat(PostingFeatureExtractor.extract(PostingText.fromHtml(html), CATEGORIES)).isEmpty();
    }

    @Test
    @DisplayName("상세페이지 옆 '관련 공고' 목록의 재택 문구는 이 공고의 근거가 아니다(쿠팡)")
    void ignoresRelatedJobLists() {
        String html = "<body><div class=\"job-description\"><p>퍼포먼스 마케터, 판교 근무</p></div>"
            + "<aside class=\"sidebar sidebar-related\"><div class=\"js-job\">일반 사무 (일 8시간 재택 근무)</div></aside>"
            + "<div class=\"related-jobs\"><div class=\"card-body\">CS 상담 (재택 근무)</div></div></body>";

        assertThat(PostingFeatureExtractor.extract(PostingText.fromHtml(html), CATEGORIES)).isEmpty();
    }

    @Test
    @DisplayName("블록마다 줄을 바꿔, 다음 항목의 부정어가 앞 항목에 붙지 않는다")
    void blocksBecomeLines() {
        String html = "<ul><li>재택근무 운영</li><li>야간 근무 불가</li></ul>";

        String text = PostingText.fromHtml(html);

        assertThat(text).isEqualTo("재택근무 운영\n야간 근무 불가");
        assertThat(PostingFeatureExtractor.extract(text, List.of()))
            .extracting(FeatureHit::featureCode).containsExactly("REMOTE");
    }
}
