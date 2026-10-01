package com.nklcbdty.batch.nklcbdty.batch.email.service;
import static com.nklcbdty.common.vo.QUserInterestVo.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import com.nklcbdty.common.vo.UserInterestVo;
import com.querydsl.core.Tuple;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;
import com.nklcbdty.common.dto.JobPosting;
import com.nklcbdty.common.email.JobEmailContentBuilder;
import com.nklcbdty.common.email.JobMailOrdering;
import com.nklcbdty.common.user.dto.UserIdAndEmailDto;
import com.nklcbdty.common.user.repository.UserInterestRepository;
import com.nklcbdty.common.user.repository.UserInterestRepositoryImpl;
import com.nklcbdty.batch.nklcbdty.batch.user.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {
    private final UserService userService;
    private final JavaMailSender mailSender;
    private final UserInterestRepository userInterestRepository;
    private final UserInterestRepositoryImpl userInterestRepositoryImpl;
    private final JobRepository jobRepositoryInterface;

    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helpder = new MimeMessageHelper(message, true, "UTF-8");
            helpder.setTo(to);
            helpder.setSubject(subject);
            helpder.setText(body, true);
            mailSender.send(message);
        } catch (AddressException e) {
            log.error("메일 발송 실패 - 잘못된 이메일 주소 형식: {}", to, e);
        } catch (AuthenticationFailedException e) {
            log.error("메일 발송 실패 - 인증 오류: {}", e.getMessage(), e);
        } catch (SendFailedException e) {
            log.error("메일 발송 실패 - 메시징 오류: {}", e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("메일 발송 실패 - 스프링 메일 오류: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("메일 발송 중 예상치 못한 오류 발생", e);
        }
    }

    public String generateJobPostingEmailHtml(String keyword, List<JobPosting> jobPostings) {
        return JobEmailContentBuilder.generateHtml(keyword, jobPostings);
    }

    public Map<String, List<String>> getUserCategoryMap() {
        List<Tuple> userCategories = userInterestRepositoryImpl.findUserCategories();
        // userId 추출
        Map<String, List<String>> userCategoryMap = new HashMap<>();

        for (Tuple tuple : userCategories) {
            String userId = tuple.get(userInterestVo.userId);
            String category = tuple.get(1, String.class);
            // 필요한 작업 수행
            List<String> usrIds = userCategoryMap.getOrDefault(category, new ArrayList<>());
            usrIds.add(userId);
            userCategoryMap.put(category, usrIds);
        }
        return userCategoryMap;
    }

    public Map<String, String> sendEmail(List<String> userIds) {
        List<UserIdAndEmailDto> userEmailItems = userService.findByUserIdIn(userIds);
        Map<String, String> userEmailMap = new HashMap<>();
        for (UserIdAndEmailDto item : userEmailItems) {
            userEmailMap.put(item.getUserId(), item.getEmail());
        }

        Map<String, String> result = new HashMap<>();
        for (String userId : userIds) {
            String email = userEmailMap.get(userId);
            if (email == null) {
                log.error("User ID: {} does not have an associated email.", userId);
                continue;
            }

            List<UserInterestVo> companys = userInterestRepository.findItemValueByUserIdAndItemType(userId, "company");
            List<String> companysStr = new ArrayList<>();
            for (UserInterestVo company : companys) {
                companysStr.add(company.getItemValue());
            }
            List<UserInterestVo> jobs = userInterestRepository.findItemValueByUserIdAndItemType(userId, "job");
            List<String> jobStr = new ArrayList<>();
            for (UserInterestVo job : jobs) {
                jobStr.add(job.getItemValue());
            }
            List<UserInterestVo> careerYear = userInterestRepository.findItemValueByUserIdAndItemType(userId, "career_year");
            long careerYearNum;
            if (careerYear.isEmpty()) {
                careerYearNum = 0;
            } else {
                final String itemValue = careerYear.get(careerYear.size() - 1).getItemValue();
                careerYearNum = Integer.parseInt(itemValue);
            }
            List<Job_mst> allByCompanyCdInAndSubJobCdNmIn = jobRepositoryInterface.findJobsByDetailedCriteria(
                companysStr,
                jobStr,
                careerYearNum, // 경력 시작일
                0L  // 경력 종료일 (0L은 경력 무관)
            );
            // 수동 메일과 동일 정책: 종료된/손상 공고 제외 + 1년 초과(2999-12-31 등 무기한)는 뒤로
            LocalDate today = LocalDate.now();
            allByCompanyCdInAndSubJobCdNmIn = allByCompanyCdInAndSubJobCdNmIn.stream()
                .filter(job -> JobMailOrdering.isLive(job, today))
                .collect(Collectors.toList());
            // job_mst 에 같은 공고가 여러 행으로 남아 있어도 메일에는 한 번만 싣는다.
            final int beforeDedupe = allByCompanyCdInAndSubJobCdNmIn.size();
            allByCompanyCdInAndSubJobCdNmIn = JobMailOrdering.dedupe(allByCompanyCdInAndSubJobCdNmIn);
            if (beforeDedupe != allByCompanyCdInAndSubJobCdNmIn.size()) {
                log.warn("중복 공고 {}건 제외 (userId={}, 원본={}건)",
                    beforeDedupe - allByCompanyCdInAndSubJobCdNmIn.size(), userId, beforeDedupe);
            }
            allByCompanyCdInAndSubJobCdNmIn = JobMailOrdering.pushFarFutureEndDateToBottom(allByCompanyCdInAndSubJobCdNmIn);
            log.info("userId: {}, companys: {}, jobs: {}, allByCompanyCdInAndSubJobCdNmIn: {}", userId, companys, jobs, allByCompanyCdInAndSubJobCdNmIn.size());
            List<JobPosting> jobPostings = new ArrayList<>();
            for (Job_mst job : allByCompanyCdInAndSubJobCdNmIn) {
                JobPosting jobPosting = new JobPosting();
                jobPosting.setTitle(job.getAnnoSubject());
                jobPosting.setCompany(job.getCompanyCd());
                jobPosting.setUrl(job.getJobDetailLink());
                jobPosting.setJobType(job.getSubJobCdNm());
                jobPosting.setStartDate(job.getStartDate());
                jobPosting.setEndDate(job.getEndDate());
                jobPosting.setPersonalHistory(job.getPersonalHistory());
                jobPosting.setPersonalHistoryEnd(job.getPersonalHistoryEnd());
                jobPosting.setEmpType(job.getEmpTypeCdNm());
                jobPostings.add(jobPosting);
            }

            String html = this.generateJobPostingEmailHtml("backend", jobPostings);
            result.put(email, html);
        }

        return result;
    }

}
