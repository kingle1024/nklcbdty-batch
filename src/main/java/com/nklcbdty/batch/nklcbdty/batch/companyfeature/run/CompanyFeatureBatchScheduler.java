package com.nklcbdty.batch.nklcbdty.batch.companyfeature.run;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class CompanyFeatureBatchScheduler {
    private final JobLauncher jobLauncher;
    private final Job companyFeatureJob;

    /**
     * 매일 11시. 크롤링(7시)과 링크 검증(9시 30분)이 끝나 열린 공고 목록이 정리된 뒤에 돈다.
     */
    @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Seoul")
    public void runCompanyFeatureJob() {
        try {
            log.info("⏰ 재택/복지 추출 배치 실행 요청!");
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(companyFeatureJob, jobParameters);
            log.info("🎉 재택/복지 추출 배치 실행 완료!");
        } catch (Exception e) {
            log.error("😭 재택/복지 추출 배치 실행 중 오류 발생: {}", e.getMessage());
        }
    }
}
