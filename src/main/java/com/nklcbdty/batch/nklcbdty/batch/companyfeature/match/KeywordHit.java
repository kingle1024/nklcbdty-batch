package com.nklcbdty.batch.nklcbdty.batch.companyfeature.match;

/**
 * 본문에서 찾은 키워드와 그 앞뒤 발췌(매칭 근거).
 */
public record KeywordHit(String keyword, String snippet) {
}
