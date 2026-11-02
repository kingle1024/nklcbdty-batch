package com.nklcbdty.batch.nklcbdty.batch.companyfeature.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor.FeatureHit;
import com.nklcbdty.batch.nklcbdty.batch.companyfeature.match.PostingFeatureExtractor.WelfareCategoryRule;
import com.nklcbdty.common.vo.Job_mst;

/**
 * welfare_category / company_feature 접근. 두 테이블은 nklcbdty-service 가 기동 시 만든다
 * (CompanyFeatureSchemaInitializer). 배치는 엔티티를 따로 두지 않고 JDBC 로만 쓴다.
 */
@Repository
public class CompanyFeatureJdbcRepository {

    private static final int MAX_SUBJECT = 500;
    private static final int MAX_SYS_COMPANY = 255;
    private static final int MAX_LINK = 1000;
    private static final int MAX_KEYWORD = 100;
    private static final int MAX_SNIPPET = 500;

    private final JdbcTemplate jdbcTemplate;

    public CompanyFeatureJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WelfareCategoryRule> findActiveCategories() {
        return jdbcTemplate.query(
            "SELECT code, keywords FROM welfare_category WHERE active = 1 ORDER BY sort_order, id",
            (rs, i) -> new WelfareCategoryRule(rs.getString("code"), splitKeywords(rs.getString("keywords"))));
    }

    static List<String> splitKeywords(String keywords) {
        if (keywords == null) {
            return List.of();
        }
        return Arrays.stream(keywords.split("\n"))
                     .map(String::trim)
                     .filter(k -> !k.isEmpty())
                     .collect(Collectors.toList());
    }

    /** 그 공고의 근거를 이번 결과로 통째로 바꾼다(키워드가 바뀌어 빠진 특징도 지워지도록). */
    public void replaceForJob(Job_mst job, List<FeatureHit> hits, LocalDateTime detectedAt) {
        jdbcTemplate.update("DELETE FROM company_feature WHERE job_id = ?", job.getId());
        if (hits.isEmpty()) {
            return;
        }
        List<Object[]> rows = new ArrayList<>();
        for (FeatureHit h : hits) {
            rows.add(new Object[] {
                job.getCompanyCd().toUpperCase(),
                h.featureType(),
                h.featureCode(),
                job.getId(),
                cut(job.getAnnoSubject(), MAX_SUBJECT),
                cut(job.getSysCompanyCdNm(), MAX_SYS_COMPANY),
                cut(job.getJobDetailLink(), MAX_LINK),
                cut(h.hit().keyword(), MAX_KEYWORD),
                cut(h.hit().snippet(), MAX_SNIPPET),
                Timestamp.valueOf(detectedAt),
            });
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO company_feature (company_cd, feature_type, feature_code, job_id, anno_subject," +
            " sys_company_nm, job_detail_link, matched_keyword, snippet, detected_dts)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            rows);
    }

    /**
     * 더는 열려 있지 않은 공고의 근거를 지운다. companyCd 가 있으면 그 회사 행만 본다
     * (회사 하나만 다시 돌렸을 때 다른 회사 근거를 건드리지 않게).
     *
     * @return 지운 행 수
     */
    public int deleteStale(Set<Long> activeJobIds, String companyCd) {
        List<Long> stored = companyCd == null
            ? jdbcTemplate.queryForList("SELECT DISTINCT job_id FROM company_feature", Long.class)
            : jdbcTemplate.queryForList("SELECT DISTINCT job_id FROM company_feature WHERE company_cd = ?",
                Long.class, companyCd.toUpperCase());
        List<Long> stale = stored.stream().filter(id -> !activeJobIds.contains(id)).collect(Collectors.toList());
        int deleted = 0;
        for (List<Long> chunk : chunks(stale, 500)) {
            String placeholders = chunk.stream().map(id -> "?").collect(Collectors.joining(","));
            deleted += jdbcTemplate.update(
                "DELETE FROM company_feature WHERE job_id IN (" + placeholders + ")", chunk.toArray());
        }
        return deleted;
    }

    private static List<List<Long>> chunks(Collection<Long> ids, int size) {
        List<List<Long>> result = new ArrayList<>();
        List<Long> current = new ArrayList<>();
        for (Long id : ids) {
            current.add(id);
            if (current.size() == size) {
                result.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    private static String cut(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
