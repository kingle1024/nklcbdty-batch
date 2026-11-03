package com.nklcbdty.batch.nklcbdty.batch.companyfeature.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeywordMatcherTest {

    private static final List<String> REMOTE = WorkStyleRule.REMOTE.getKeywords();

    @ParameterizedTest
    @DisplayName("재택을 허용하는 문장은 잡는다")
    @ValueSource(strings = {
        "주 2회 재택근무를 하고 있어요.",
        "근무 형태: 재택 가능",
        "원격근무 제도를 운영합니다",
        "리모트 워크 기반으로 일해요",
        "We are a remote-first company.",
        "You can work from home twice a week.",
        "WFH allowance provided",
        "재택근무 가능 (단, 수습기간 중 불가)",
        "하이브리드 근무(주 3일 출근)",
    })
    void matchesPositiveRemote(String text) {
        assertThat(KeywordMatcher.findFirst(text, REMOTE)).isPresent();
    }

    @ParameterizedTest
    @DisplayName("부정 문맥은 거른다")
    @ValueSource(strings = {
        "재택근무 불가",
        "본 포지션은 재택 근무가 불가능합니다.",
        "재택근무는 지원하지 않습니다",
        "재택 근무 없음",
        "원격 근무는 제공하지 않습니다",
        "Remote work is not available for this role.",
        "This is not a remote work position.",
        "No remote work. On-site in Seoul.",
        "work from home: unavailable",
        "토스팀원이 재택근무를 마다하고 오피스를 찾는 이유?",
    })
    void rejectsNegatedRemote(String text) {
        assertThat(KeywordMatcher.findFirst(text, REMOTE)).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("근무와 무관한 '원격'/'remote' 는 잡지 않는다")
    @ValueSource(strings = {
        "원격 제어 솔루션 개발 경험",
        "Firebase Remote Config 경험",
        "remote sensing 데이터 분석",
        "우대사항: 원격 진료 서비스 개발",
        "remoteworking", // 단어 경계 밖
    })
    void ignoresUnrelatedRemoteWords(String text) {
        assertThat(KeywordMatcher.findFirst(text, REMOTE)).isEmpty();
    }

    @Test
    @DisplayName("다른 줄의 부정어는 앞 줄 키워드에 붙지 않는다")
    void negationDoesNotCrossLines() {
        Optional<KeywordHit> hit = KeywordMatcher.findFirst("재택근무 운영\n수습기간 중 연장근무 불가", REMOTE);

        assertThat(hit).isPresent();
        assertThat(hit.get().keyword()).isEqualTo("재택");
    }

    @Test
    @DisplayName("부정된 첫 등장은 건너뛰고 뒤의 긍정 등장을 잡는다")
    void skipsNegatedOccurrence() {
        Optional<KeywordHit> hit = KeywordMatcher.findFirst(
            "인턴은 재택 불가합니다. 정규직 전환 후 재택근무를 주 2회 해요.", REMOTE);

        assertThat(hit).isPresent();
        assertThat(hit.get().snippet()).contains("재택근무를 주 2회");
    }

    @Test
    @DisplayName("키워드 공백은 유연하게: '원격 근무' 키워드가 '원격근무'도 잡는다")
    void whitespaceInsensitive() {
        assertThat(KeywordMatcher.findFirst("원격근무 OK", List.of("원격 근무"))).isPresent();
        assertThat(KeywordMatcher.findFirst("원격  근무 OK", List.of("원격근무"))).isEmpty();
    }

    @Test
    @DisplayName("근거 발췌는 키워드 앞뒤를 자르고 공백을 정리한다")
    void snippetAroundKeyword() {
        String text = "가".repeat(100) + " 식대   월 20만원 지원 " + "나".repeat(100);

        KeywordHit hit = KeywordMatcher.findFirst(text, List.of("식대")).orElseThrow();

        assertThat(hit.keyword()).isEqualTo("식대");
        assertThat(hit.snippet()).startsWith("…").endsWith("…").contains("식대 월 20만원 지원");
        assertThat(hit.snippet().length()).isLessThanOrEqualTo(40 + 2 + 60 + 2);
    }

    @Test
    @DisplayName("복지도 같은 부정 규칙: '식대 미지원'은 버린다")
    void welfareNegation() {
        assertThat(KeywordMatcher.findFirst("식대 미지원, 대신 사내 식당 운영", List.of("식대"))).isEmpty();
        assertThat(KeywordMatcher.findFirst("식대 미지원, 대신 사내 식당 운영", List.of("식대", "사내 식당")))
            .get().extracting(KeywordHit::keyword).isEqualTo("사내 식당");
    }
}
