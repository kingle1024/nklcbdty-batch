package com.nklcbdty.batch.nklcbdty.batch.companyfeature.text;

import com.nklcbdty.common.vo.Job_mst;

/**
 * 공고 본문 평문을 가져오는 방법. 상세페이지 HTML 을 그대로 받으면 되는 회사가 대부분이지만,
 * 상세페이지가 SPA 라 HTML 에 본문이 없는 회사(배민)는 채용 API 로 따로 받는다.
 *
 * <p>여러 구현이 지원하면 {@code @Order} 가 앞선 것을 쓴다. 상세페이지 HTML 구현이 맨 뒤 기본값이다.</p>
 */
public interface PostingTextSource {

    boolean supports(Job_mst job);

    /**
     * 본문 평문. 받지 못하면 null — 그 공고의 기존 근거는 이번 회차에 건드리지 않는다
     * (통신 실패를 "재택 아님"으로 오해해 지우지 않기 위해).
     */
    String fetchText(Job_mst job);
}
