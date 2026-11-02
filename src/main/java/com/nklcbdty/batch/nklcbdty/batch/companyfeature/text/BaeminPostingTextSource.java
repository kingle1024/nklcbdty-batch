package com.nklcbdty.batch.nklcbdty.batch.companyfeature.text;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 배민(우아한형제들). 상세페이지가 Next.js 클라이언트 렌더링이라 HTML 에 본문이 없다.
 * 링크의 recruitNumber(R2601047 등)로 채용 API 상세를 받아 recruitContents(HTML)를 쓴다.
 */
@Component
@Order(10)
@Slf4j
public class BaeminPostingTextSource implements PostingTextSource {

    private static final String CAREER_HOST = "career.woowahan.com";
    private static final Pattern RECRUIT_NUMBER = Pattern.compile("/recruitment/(R\\d+)");
    static final String API_URL_FORMAT = "https://career.woowahan.com/w1/recruits/%s";

    private final Function<String, String> fetcher;

    public BaeminPostingTextSource() {
        this(HttpText.defaultFetcher());
    }

    BaeminPostingTextSource(Function<String, String> fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public boolean supports(Job_mst job) {
        String link = job.getJobDetailLink();
        return link != null && link.contains(CAREER_HOST) && RECRUIT_NUMBER.matcher(link).find();
    }

    @Override
    public String fetchText(Job_mst job) {
        Matcher m = RECRUIT_NUMBER.matcher(job.getJobDetailLink());
        if (!m.find()) {
            return null;
        }
        String json = fetcher.apply(String.format(API_URL_FORMAT, m.group(1)));
        if (json == null) {
            return null;
        }
        try {
            JSONObject data = new JSONObject(json).optJSONObject("data");
            if (data == null) {
                log.warn("⚠️ 배민 상세 응답에 data 가 없음 link={}", job.getJobDetailLink());
                return null;
            }
            String contents = data.optString("recruitContents", "");
            return PostingText.fromHtml(data.optString("recruitName", "") + "<br>" + contents);
        } catch (Exception e) {
            log.warn("⚠️ 배민 상세 응답 해석 실패 link={} - {}", job.getJobDetailLink(), e.getMessage());
            return null;
        }
    }
}
