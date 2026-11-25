package com.nklcbdty.batch.nklcbdty.batch.email.run;

import java.util.stream.Collectors;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

/**
 * 메일 배치 결과를 한 줄로 요약한다(로그·수동 실행 응답용).
 * "실행 완료" 만 찍던 시절엔 Gmail 로그인이 막혀도 실패가 어디에도 안 보였다.
 */
public final class EmailJobResult {

    private EmailJobResult() {
    }

    public static String summarize(JobExecution execution) {
        String steps = execution.getStepExecutions().stream()
            .map(EmailJobResult::step)
            .collect(Collectors.joining(", "));
        StringBuilder sb = new StringBuilder()
            .append(execution.getStatus())
            .append(" [").append(steps).append(']');
        String reason = firstLine(execution.getExitStatus().getExitDescription());
        if (!reason.isEmpty()) {
            sb.append(" 원인: ").append(reason);
        }
        return sb.toString();
    }

    private static String step(StepExecution s) {
        return s.getStepName() + " " + s.getStatus()
            + " 읽음=" + s.getReadCount()
            + " 발송=" + s.getWriteCount()
            + " 건너뜀=" + s.getSkipCount();
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        int nl = trimmed.indexOf('\n');
        return nl < 0 ? trimmed : trimmed.substring(0, nl).strip();
    }
}
