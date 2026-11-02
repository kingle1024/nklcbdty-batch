package com.nklcbdty.batch.nklcbdty.batch.companyfeature.run;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor;
import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor.FeatureHit;
import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor.WelfareCategoryRule;
import com.nklcbdty.batch.nklcbdty.batch.companyfeature.repository.CompanyFeatureJdbcRepository;
import com.nklcbdty.batch.nklcbdty.batch.companyfeature.text.PostingTextSource;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 공고 본문을 받아 근무형태·복지 특징을 뽑는다. 본문을 못 받으면 null(→ 그 공고는 이번 회차 건너뜀).
 */
@Component
@StepScope
@Slf4j
public class CompanyFeatureProcessor implements ItemProcessor<Job_mst, PostingFeatures> {

    private final List<PostingTextSource> textSources;
    private final CompanyFeatureJdbcRepository repository;
    private final long minIntervalPerDomainMs;

    private final Map<String, Long> lastCallPerDomain = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private List<WelfareCategoryRule> categories;

    public CompanyFeatureProcessor(List<PostingTextSource> textSources,
                                   CompanyFeatureJdbcRepository repository,
                                   @Value("${nklcb.company-feature.min-interval-ms:3000}") long minIntervalPerDomainMs) {
        this.textSources = textSources;
        this.repository = repository;
        this.minIntervalPerDomainMs = minIntervalPerDomainMs;
    }

    @Override
    public PostingFeatures process(Job_mst job) throws Exception {
        if (categories == null) {
            // 스텝 하나 동안은 같은 카테고리/키워드로 본다
            categories = repository.findActiveCategories();
            log.info("🏷️ 활성 복지 카테고리 {}개: {}", categories.size(),
                categories.stream().map(WelfareCategoryRule::code).toList());
        }
        int current = counter.incrementAndGet();

        PostingTextSource source = textSources.stream().filter(s -> s.supports(job)).findFirst().orElse(null);
        if (source == null) {
            log.warn("🚫 본문을 받을 방법이 없어 건너뜀: id={} link={}", job.getId(), job.getJobDetailLink());
            return null;
        }

        throttleByDomain(job.getJobDetailLink());
        String text = source.fetchText(job);
        if (text == null || text.isBlank()) {
            log.warn("❔ 본문을 받지 못해 이번 회차 건너뜀: id={} company={}", job.getId(), job.getCompanyCd());
            return null;
        }

        List<FeatureHit> hits = PostingFeatureExtractor.extract(text, categories);
        log.info("🔎 [{}] id={} {} '{}' → {}", current, job.getId(), job.getCompanyCd(), job.getAnnoSubject(),
            hits.stream().map(h -> h.featureCode() + "(" + h.hit().keyword() + ")").toList());
        return new PostingFeatures(job, hits);
    }

    private void throttleByDomain(String url) throws InterruptedException {
        String domain = domainOf(url);
        if (domain == null) {
            return;
        }
        Long last = lastCallPerDomain.get(domain);
        if (last != null) {
            long wait = minIntervalPerDomainMs - (System.currentTimeMillis() - last);
            if (wait > 0) {
                Thread.sleep(wait);
            }
        }
        lastCallPerDomain.put(domain, System.currentTimeMillis());
    }

    private static String domainOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
