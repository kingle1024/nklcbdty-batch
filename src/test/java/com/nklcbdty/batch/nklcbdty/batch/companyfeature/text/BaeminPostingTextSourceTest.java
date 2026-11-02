package com.nklcbdty.batch.nklcbdty.batch.companyfeature.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.common.vo.Job_mst;

class BaeminPostingTextSourceTest {

    private Job_mst baeminJob() {
        Job_mst job = new Job_mst();
        job.setCompanyCd("BAEMIN");
        job.setJobDetailLink("https://career.woowahan.com/recruitment/R2601047/detail?jobCodes=&category=x");
        return job;
    }

    @Test
    @DisplayName("링크의 recruitNumber 로 상세 API 를 불러 recruitContents 를 평문으로")
    void fetchesRecruitContents() {
        List<String> called = new ArrayList<>();
        BaeminPostingTextSource source = new BaeminPostingTextSource(url -> {
            called.add(url);
            return "{\"code\":\"2000\",\"data\":{\"recruitName\":\"백엔드\","
                + "\"recruitContents\":\"<p>주 2회 <strong>재택근무</strong></p><p>식대 지원</p>\"}}";
        });

        String text = source.fetchText(baeminJob());

        assertThat(called).containsExactly("https://career.woowahan.com/w1/recruits/R2601047");
        assertThat(text).isEqualTo("백엔드\n주 2회 재택근무\n식대 지원");
    }

    @Test
    @DisplayName("API 실패나 data 없음은 null(이번 회차 건너뜀)")
    void nullOnFailure() {
        assertThat(new BaeminPostingTextSource(url -> null).fetchText(baeminJob())).isNull();
        assertThat(new BaeminPostingTextSource(url -> "{\"code\":\"4040\"}").fetchText(baeminJob())).isNull();
    }

    @Test
    @DisplayName("배민 상세 링크만 지원")
    void supportsOnlyBaeminLinks() {
        BaeminPostingTextSource source = new BaeminPostingTextSource(url -> null);
        Job_mst other = new Job_mst();
        other.setJobDetailLink("https://careers.linecorp.com/ko/jobs/3126");

        assertThat(source.supports(baeminJob())).isTrue();
        assertThat(source.supports(other)).isFalse();
    }
}
