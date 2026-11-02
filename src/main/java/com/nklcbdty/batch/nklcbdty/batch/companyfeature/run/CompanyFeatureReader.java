package com.nklcbdty.batch.nklcbdty.batch.companyfeature.run;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 열려 있는 공고(findActiveJobs). 잡 파라미터 companyCd 가 있으면 그 회사 공고만.
 */
@Component
@StepScope
@Slf4j
public class CompanyFeatureReader implements ItemReader<Job_mst> {

    private final JobRepository jobRepository;
    private final String companyCd;
    private Iterator<Job_mst> iterator;

    @Getter
    private int total;

    public CompanyFeatureReader(JobRepository jobRepository,
                                @Value("#{jobParameters['companyCd']}") String companyCd) {
        this.jobRepository = jobRepository;
        this.companyCd = companyCd == null || companyCd.isBlank() ? null : companyCd.trim();
    }

    @Override
    public Job_mst read() {
        if (iterator == null) {
            List<Job_mst> targets = activeJobs(jobRepository, companyCd);
            total = targets.size();
            log.info("📋 재택/복지 추출 대상 공고 수: {} (회사: {})", total, companyCd == null ? "전체" : companyCd);
            iterator = targets.iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    static List<Job_mst> activeJobs(JobRepository jobRepository, String companyCd) {
        return jobRepository.findActiveJobs().stream()
            .filter(job -> job.getCompanyCd() != null)
            .filter(job -> companyCd == null || companyCd.equalsIgnoreCase(job.getCompanyCd()))
            .collect(Collectors.toList());
    }
}
