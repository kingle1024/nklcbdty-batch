package com.nklcbdty.batch.nklcbdty.batch.linkvalidator.liveness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.common.vo.Job_mst;

// 쿠팡 상세페이지는 Cloudflare 봇 차단에 걸려 서버에서는 챌린지 페이지(403)만 온다.
// 그래서 HTML 문자열 매칭 대신 Greenhouse board API 의 공고 id 목록으로 생존을 판정한다.
class CoupangLivenessCheckerTest {

    private Job_mst coupangJob(String annoId) {
        Job_mst job = new Job_mst();
        job.setAnnoId(annoId);
        job.setCompanyCd("COUPANG");
        job.setAnnoSubject("[쿠팡] Backend Engineer");
        job.setJobDetailLink("https://www.coupang.jobs/kr/jobs/?gh_jid=8096053");
        return job;
    }

    private String boardJson(String... ids) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"id\":").append(ids[i])
                .append(",\"title\":\"공고").append(i).append("\"")
                .append(",\"location\":{\"name\":\"Seoul, South Korea\"}}");
        }
        return "{\"jobs\":[" + items + "],\"meta\":{\"total\":" + ids.length + "}}";
    }

    @Test
    @DisplayName("board API 에 공고 id 가 있으면 ALIVE")
    void aliveWhenIdPresent() {
        CoupangLivenessChecker checker =
            new CoupangLivenessChecker(url -> boardJson("8096053", "8127263", "8100001"));

        assertThat(checker.check(coupangJob("8096053"))).isEqualTo(Liveness.ALIVE);
    }

    @Test
    @DisplayName("board API 에 없는 공고 id 는 CLOSED")
    void closedWhenIdAbsent() {
        CoupangLivenessChecker checker =
            new CoupangLivenessChecker(url -> boardJson("8096053", "8127263"));

        assertThat(checker.check(coupangJob("99999999"))).isEqualTo(Liveness.CLOSED);
    }

    @Test
    @DisplayName("API 통신 실패(null 응답)는 CLOSED 가 아니라 UNKNOWN — 장애로 공고가 전멸하면 안 된다")
    void unknownWhenFetchFails() {
        CoupangLivenessChecker checker = new CoupangLivenessChecker(url -> null);

        assertThat(checker.check(coupangJob("8096053"))).isEqualTo(Liveness.UNKNOWN);
    }

    @Test
    @DisplayName("응답 형식이 바뀌어 jobs 를 못 찾으면 UNKNOWN")
    void unknownWhenEnvelopeChanged() {
        CoupangLivenessChecker checker =
            new CoupangLivenessChecker(url -> "{\"postings\":[]}");

        assertThat(checker.check(coupangJob("8096053"))).isEqualTo(Liveness.UNKNOWN);
    }

    @Test
    @DisplayName("응답이 JSON 이 아니어도(Cloudflare 챌린지 페이지 등) UNKNOWN")
    void unknownWhenNotJson() {
        CoupangLivenessChecker checker =
            new CoupangLivenessChecker(url -> "<html><head><title>Just a moment...</title></head></html>");

        assertThat(checker.check(coupangJob("8096053"))).isEqualTo(Liveness.UNKNOWN);
    }

    @Test
    @DisplayName("공고 건수만큼 API 를 때리지 않는다 — 스냅샷을 재사용한다")
    void reusesSnapshotAcrossJobs() {
        List<String> calls = new ArrayList<>();
        Function<String, String> counting = url -> {
            calls.add(url);
            return boardJson("8096053", "8127263", "8100001");
        };
        CoupangLivenessChecker checker = new CoupangLivenessChecker(counting);

        checker.check(coupangJob("8096053"));
        checker.check(coupangJob("8127263"));
        checker.check(coupangJob("8100001"));

        assertThat(calls).hasSize(1);
    }

    @Test
    @DisplayName("annoId 가 없으면 판정하지 않는다")
    void unknownWhenNoAnnoId() {
        CoupangLivenessChecker checker =
            new CoupangLivenessChecker(url -> boardJson("8096053"));

        assertThat(checker.check(coupangJob(null))).isEqualTo(Liveness.UNKNOWN);
    }

    @Test
    @DisplayName("쿠팡 공고만 맡는다 — company_cd 또는 coupang.jobs 링크로 판별")
    void supportsOnlyCoupang() {
        CoupangLivenessChecker checker = new CoupangLivenessChecker(url -> null);

        Job_mst naver = new Job_mst();
        naver.setCompanyCd("NAVER");
        naver.setJobDetailLink("https://recruit.navercorp.com/rcrt/view.do?annoId=30005189");

        Job_mst legacyNoCompanyCd = new Job_mst();
        legacyNoCompanyCd.setJobDetailLink("https://www.coupang.jobs/kr/jobs/8127263/some-slug/?gh_jid=8127263");

        assertThat(checker.supports(coupangJob("8096053"))).isTrue();
        assertThat(checker.supports(legacyNoCompanyCd)).isTrue();
        assertThat(checker.supports(naver)).isFalse();
        assertThat(checker.supports(null)).isFalse();
    }
}
