package com.nklcbdty.batch.nklcbdty.batch.companyfeature.text;

import java.util.function.Function;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.nklcbdty.common.vo.Job_mst;

/**
 * 기본: 상세페이지(job_detail_link) HTML 을 받아 평문으로. 네이버·카카오·라인·당근·토스는 서버 렌더링이라
 * 이것으로 본문이 나온다. 쿠팡은 봇 차단(403)이라 null 이 되고 근거가 쌓이지 않는다.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HtmlPostingTextSource implements PostingTextSource {

    private final Function<String, String> fetcher;

    public HtmlPostingTextSource() {
        this(HttpText.defaultFetcher());
    }

    HtmlPostingTextSource(Function<String, String> fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public boolean supports(Job_mst job) {
        String link = job.getJobDetailLink();
        return link != null && link.startsWith("http");
    }

    @Override
    public String fetchText(Job_mst job) {
        String html = fetcher.apply(job.getJobDetailLink());
        return html == null ? null : PostingText.fromHtml(html);
    }
}
