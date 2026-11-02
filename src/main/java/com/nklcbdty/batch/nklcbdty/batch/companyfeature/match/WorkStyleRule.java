package com.nklcbdty.batch.nklcbdty.batch.companyfeature.match;

import java.util.List;

/**
 * 근무형태 태그와 그 태그를 붙일 본문 키워드.
 *
 * <p>상수 이름이 company_feature.feature_code 값이 된다(nklcbdty-service 의 WorkStyle 과 같아야 한다).
 * 근무형태를 늘리려면(예: FLEXIBLE 유연근무제) 여기와 서비스 enum 에 상수만 추가하면 되고 스키마는 그대로다.</p>
 *
 * <p>키워드는 단독으로 쓰이면 다른 뜻이 되는 말("원격", "remote" — 원격 제어, Remote Config 등)을 피하고
 * 근무 맥락이 붙은 구로 둔다. 앞에 둘수록 우선이다.</p>
 */
public enum WorkStyleRule {
    REMOTE(List.of(
        "재택", "원격 근무", "원격으로 근무", "리모트 근무", "리모트 워크", "리모트워크", "하이브리드 근무",
        "remote work", "remote-first", "remote first", "fully remote", "remote friendly", "remote-friendly",
        "work from home", "work remotely", "WFH"
    )),
    ;

    private final List<String> keywords;

    WorkStyleRule(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getKeywords() {
        return keywords;
    }
}
