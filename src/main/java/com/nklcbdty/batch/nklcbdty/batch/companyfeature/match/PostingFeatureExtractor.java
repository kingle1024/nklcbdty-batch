package com.nklcbdty.batch.nklcbdty.batch.companyfeature.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 공고 본문 하나에서 근무형태 태그와 복지 카테고리를 뽑는다.
 */
public final class PostingFeatureExtractor {

    public static final String WORK_STYLE = "WORK_STYLE";
    public static final String WELFARE = "WELFARE";

    private PostingFeatureExtractor() {
    }

    public static List<FeatureHit> extract(String text, List<WelfareCategoryRule> categories) {
        List<FeatureHit> hits = new ArrayList<>();
        for (WorkStyleRule rule : WorkStyleRule.values()) {
            KeywordMatcher.findFirst(text, rule.getKeywords())
                .ifPresent(hit -> hits.add(new FeatureHit(WORK_STYLE, rule.name(), hit)));
        }
        for (WelfareCategoryRule category : categories) {
            Optional<KeywordHit> hit = KeywordMatcher.findFirst(text, category.keywords());
            hit.ifPresent(h -> hits.add(new FeatureHit(WELFARE, category.code(), h)));
        }
        return hits;
    }

    /** 한 공고에서 찾은 특징 하나. */
    public record FeatureHit(String featureType, String featureCode, KeywordHit hit) {
    }

    /** welfare_category 의 활성 행. 키워드는 관리자가 백오피스에서 관리한다. */
    public record WelfareCategoryRule(String code, List<String> keywords) {
    }
}
