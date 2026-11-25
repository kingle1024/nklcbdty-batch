package com.nklcbdty.batch.nklcbdty.batch.email.run;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
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
public class EmailBatchScheduler {
    private final JobLauncher jobLauncher;
    private final Job emailBatchJob;
    /**
     * 매일 오전 5시에 이메일 발송 배치 Job을 실행합니다.
     * cron = "초 분 시 일 월 요일"
     * "0 0 5 * * *" 의미: 매일 오전 5시 0분 0초
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    // @Scheduled(fixedDelay = 10000) // 10초마다 실행 (테스트용)
    public void runEmailBatchJob() {
        try {
            log.info("⏰ 이메일 발송 배치 실행 요청!");

            // JobParameters는 매번 다른 값으로 넣어줘야 같은 Job이 여러 번 실행될 수 있어요.
            // 여기서는 현재 시간을 파라미터로 사용해서 매번 다른 Job 인스턴스를 생성합니다.
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            // jobLauncher.run 은 잡이 FAILED 로 끝나도 예외를 던지지 않는다.
            // 예전에는 결과를 안 보고 무조건 "완료!" 를 찍어서 로그로도 실패를 알 수 없었다.
            JobExecution execution = jobLauncher.run(emailBatchJob, jobParameters);
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                log.info("🎉 이메일 발송 배치 완료: {}", EmailJobResult.summarize(execution));
            } else {
                log.error("😭 이메일 발송 배치 실패: {}", EmailJobResult.summarize(execution));
            }
        } catch (Exception e) {
            log.error("😭 이메일 발송 배치 실행 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
