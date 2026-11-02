package com.nklcbdty.batch.nklcbdty.batch.companyfeature.run;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.nklcbdty.batch.nklcbdty.batch.companyfeature.repository.CompanyFeatureJdbcRepository;
import com.nklcbdty.common.vo.Job_mst;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재택/복지 자동 추출 잡.
 * <ol>
 *   <li>companyFeatureExtractStep: 열린 공고마다 본문을 받아 키워드 매칭 → company_feature 를 공고 단위로 갈아끼움</li>
 *   <li>companyFeatureCleanupStep: 닫힌 공고의 근거를 지움</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CompanyFeatureBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    @Bean
    public Step companyFeatureExtractStep(CompanyFeatureReader companyFeatureReader,
                                          CompanyFeatureProcessor companyFeatureProcessor,
                                          CompanyFeatureWriter companyFeatureWriter) {
        return new StepBuilder("companyFeatureExtractStep", jobRepository)
            .<Job_mst, PostingFeatures>chunk(10, platformTransactionManager)
            .reader(companyFeatureReader)
            .processor(companyFeatureProcessor)
            .writer(companyFeatureWriter)
            .faultTolerant()
                .skipLimit(50)
                .skip(Exception.class)
            .build();
    }

    @Bean
    @StepScope
    public Tasklet companyFeatureCleanupTasklet(com.nklcbdty.common.crawler.repository.JobRepository jobMstRepository,
                                                CompanyFeatureJdbcRepository repository,
                                                @Value("#{jobParameters['companyCd']}") String companyCd) {
        return (contribution, chunkContext) -> {
            String target = companyCd == null || companyCd.isBlank() ? null : companyCd.trim();
            Set<Long> activeIds = CompanyFeatureReader.activeJobs(jobMstRepository, target).stream()
                .map(Job_mst::getId)
                .collect(Collectors.toSet());
            int deleted = repository.deleteStale(activeIds, target);
            log.info("🧹 닫힌 공고의 재택/복지 근거 {}건 삭제", deleted);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step companyFeatureCleanupStep(Tasklet companyFeatureCleanupTasklet) {
        return new StepBuilder("companyFeatureCleanupStep", jobRepository)
            .tasklet(companyFeatureCleanupTasklet, platformTransactionManager)
            .build();
    }

    @Bean
    public Job companyFeatureJob(Step companyFeatureExtractStep, Step companyFeatureCleanupStep) {
        return new JobBuilder("companyFeatureJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(companyFeatureExtractStep)
            .next(companyFeatureCleanupStep)
            .build();
    }
}
