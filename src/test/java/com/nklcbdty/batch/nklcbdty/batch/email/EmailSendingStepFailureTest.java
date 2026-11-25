package com.nklcbdty.batch.nklcbdty.batch.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.nklcbdty.batch.nklcbdty.batch.email.run.EmailBatchConfig;
import com.nklcbdty.batch.nklcbdty.batch.email.run.EmailContentProcessor;
import com.nklcbdty.batch.nklcbdty.batch.email.run.EmailJobResult;
import com.nklcbdty.batch.nklcbdty.batch.email.run.EmailSenderWriter;
import com.nklcbdty.batch.nklcbdty.batch.email.service.EmailService;
import com.nklcbdty.batch.nklcbdty.batch.user.service.UserService;
import com.nklcbdty.common.user.repository.UserInterestRepository;
import com.nklcbdty.common.user.repository.UserInterestRepositoryImpl;

// 2026-11: Gmail SMTP 로그인이 막혀 아침 메일이 안 나갔는데, emailBatchJob 은 매일
// COMPLETED / write_count=1 로 기록됐다. EmailService 가 발송 예외를 삼켰기 때문이다.
// 운영과 같은 스텝 설정(EmailBatchConfig 의 retry/skip)으로 돌려서, SMTP 실패가
// 스텝 FAILED 로 드러나는지 확인한다. JobRepository 는 기록만 하는 쪽이라 mock 으로 둔다.
@org.junit.jupiter.api.Timeout(20)
class EmailSendingStepFailureTest {

    private JavaMailSender mailSender;
    private Step step;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        EmailService emailService = new EmailService(
            mock(UserService.class),
            mailSender,
            mock(UserInterestRepository.class),
            mock(UserInterestRepositoryImpl.class),
            mock(com.nklcbdty.common.crawler.repository.JobRepository.class));

        step = new EmailBatchConfig(mock(JobRepository.class), new ResourcelessTransactionManager())
            .emailSendingStep(
                new ListItemReader<>(List.of(Map.entry("user@example.com", "<p>본문</p>"))),
                new EmailContentProcessor(),
                new EmailSenderWriter(emailService));
    }

    @Test
    @DisplayName("SMTP 인증 실패는 재시도 후 스텝을 FAILED 로 만든다 (COMPLETED 로 숨기지 않는다)")
    void smtpAuthFailure_failsStep() throws Exception {
        doThrow(new MailAuthenticationException("535 Username and Password not accepted"))
            .when(mailSender).send(any(MimeMessage.class));

        StepExecution execution = run();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getWriteCount()).isZero();
        // retryLimit(3): 같은 메일을 세 번까지 시도하고 포기한다
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP 전송 실패(연결 거부 등)도 FAILED 다")
    void smtpSendFailure_failsStep() throws Exception {
        doThrow(new MailSendException("Connection refused"))
            .when(mailSender).send(any(MimeMessage.class));

        assertThat(run().getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("정상 발송이면 COMPLETED, 발송 1건")
    void success_completesStep() throws Exception {
        StepExecution execution = run();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getWriteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("요약 문자열에 상태와 발송 건수가 들어간다")
    void summary_showsStatusAndCounts() throws Exception {
        doThrow(new MailAuthenticationException("535"))
            .when(mailSender).send(any(MimeMessage.class));
        StepExecution stepExecution = run();
        JobExecution job = stepExecution.getJobExecution();
        job.setStatus(stepExecution.getStatus());

        String summary = EmailJobResult.summarize(job);

        assertThat(summary).startsWith("FAILED").contains("emailSendingStep FAILED").contains("발송=0");
    }

    private StepExecution run() throws Exception {
        JobExecution jobExecution = new JobExecution(new JobInstance(1L, "emailBatchJob"), 1L, new JobParameters());
        StepExecution stepExecution = jobExecution.createStepExecution("emailSendingStep");
        step.execute(stepExecution);
        return stepExecution;
    }
}
