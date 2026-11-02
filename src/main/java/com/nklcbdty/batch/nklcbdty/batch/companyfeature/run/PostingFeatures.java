package com.nklcbdty.batch.nklcbdty.batch.companyfeature.run;

import java.util.List;

import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor.FeatureHit;
import com.nklcbdty.common.vo.Job_mst;

/** 공고 한 건과 그 본문에서 찾은 특징들(없으면 빈 목록 — 기존 근거를 지운다). */
public record PostingFeatures(Job_mst job, List<FeatureHit> hits) {
}
