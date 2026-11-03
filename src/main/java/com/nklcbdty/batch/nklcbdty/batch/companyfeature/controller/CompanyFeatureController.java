package com.nklcbdty.batch.nklcbdty.batch.companyfeature.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재택/복지 추출 수동 실행. 복지 카테고리 키워드를 바꾼 뒤 바로 반영하고 싶을 때.
 * {@code GET /batch/runCompanyFeatureJob?companyCd=LINE} 처럼 회사 하나만 돌릴 수도 있다.
 */
@RestController
@RequestMapping("/batch")
@Slf4j
@RequiredArgsConstructor
public class CompanyFeatureController {
    private final JobLauncher jobLauncher;
    private final Job companyFeatureJob;

    @GetMapping("/runCompanyFeatureJob")
    public ResponseEntity<Map<String, Object>> run(@RequestParam(required = false) String companyCd) {
        Map<String, Object> response = new HashMap<>();
        try {
            JobParametersBuilder params = new JobParametersBuilder().addLong("time", System.currentTimeMillis());
            if (companyCd != null && !companyCd.isBlank()) {
                params.addString("companyCd", companyCd.trim().toUpperCase());
            }
            log.info("⏰ 수동 재택/복지 추출 배치 실행 요청! companyCd={}", companyCd);
            JobExecution execution = jobLauncher.run(companyFeatureJob, params.toJobParameters());
            response.put("status", execution.getStatus().toString());
            response.put("writeCount", execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("😭 수동 재택/복지 추출 배치 실행 중 오류 발생: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "재택/복지 추출 배치 Job 실행 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
