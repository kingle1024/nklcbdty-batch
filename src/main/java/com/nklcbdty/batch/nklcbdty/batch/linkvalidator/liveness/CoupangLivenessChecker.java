package com.nklcbdty.batch.nklcbdty.batch.linkvalidator.liveness;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 쿠팡 공고 생존 판정.
 *
 * <p>상세페이지(www.coupang.jobs)가 Cloudflare 봇 차단 뒤로 들어가면서, 서버에서 받는 응답은
 * 공고 페이지가 아니라 "Just a moment..." 챌린지(403)다. 기본 검증은 이걸 응답 실패로 보고
 * 오류 종료(endDate=2000-01-01) 처리해서, 살아있는 쿠팡 공고가 통째로 목록에서 사라졌다.</p>
 *
 * <p>coupang.jobs 는 Greenhouse 를 채용 시스템으로 쓰고(목록 링크가 모두 {@code ?gh_jid=}),
 * Greenhouse 공개 board API 는 봇 차단이 없다. 거기서 열려 있는 공고 id 목록을 받아
 * {@code annoId}(= gh_jid) 포함 여부로 판정한다.</p>
 *
 * <p>목록은 지역 필터 없이 전부 받는다. 크롤러는 서울 공고만 적재하지만, 공고 지역 표기가
 * 바뀌어도 살아있는 건 살아있는 것이므로 넓게 받아야 오탐(살아있는데 종료 처리)이 없다.</p>
 */
@Component
@Slf4j
public class CoupangLivenessChecker implements CompanyLivenessChecker {

    static final String CAREER_HOST = "coupang.jobs";
    private static final String COMPANY_CD = "COUPANG";

    /** 페이징이 없는 단일 응답. content=false 로 본문을 빼면 0.5MB 정도다. */
    private static final String API_URL =
        "https://boards-api.greenhouse.io/v1/boards/coupang/jobs?content=false";

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 배치 한 번 도는 동안은 같은 스냅샷을 쓴다. 공고 건수만큼 API 를 때리지 않기 위함. */
    private static final long SNAPSHOT_TTL_MS = TimeUnit.MINUTES.toMillis(10);

    private final Function<String, String> fetcher;

    private Set<String> liveIdSnapshot;
    private long snapshotTakenAt;

    public CoupangLivenessChecker() {
        this(defaultFetcher());
    }

    // 테스트에서 HTTP 를 걷어내기 위한 생성자.
    CoupangLivenessChecker(Function<String, String> fetcher) {
        this.fetcher = fetcher;
    }

    private static Function<String, String> defaultFetcher() {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

        return url -> {
            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("쿠팡 채용 API 응답 실패 status={} url={}", response.code(), url);
                    return null;
                }
                ResponseBody body = response.body();
                return body == null ? null : body.string();
            } catch (Exception e) {
                log.warn("쿠팡 채용 API 호출 실패 url={} - {}", url, e.getMessage());
                return null;
            }
        };
    }

    @Override
    public boolean supports(Job_mst job) {
        if (job == null) {
            return false;
        }
        if (COMPANY_CD.equalsIgnoreCase(job.getCompanyCd())) {
            return true;
        }
        // company_cd 가 비어 있는 예전 행도 링크 도메인으로 잡아낸다.
        String link = job.getJobDetailLink();
        return link != null && link.contains(CAREER_HOST);
    }

    @Override
    public Liveness check(Job_mst job) {
        String annoId = job == null ? null : job.getAnnoId();
        if (annoId == null || annoId.isBlank()) {
            // 대조할 키가 없으면 판정하지 않는다.
            return Liveness.UNKNOWN;
        }

        Set<String> liveIds = liveIds();
        if (liveIds.isEmpty()) {
            // 통신 실패거나 응답이 통째로 비었다. 진짜 0건일 수도 있지만 그 경우와 장애를
            // 구분할 수 없으므로 종료 처리하지 않는다. (전멸 방지)
            return Liveness.UNKNOWN;
        }

        return liveIds.contains(annoId.trim()) ? Liveness.ALIVE : Liveness.CLOSED;
    }

    private synchronized Set<String> liveIds() {
        long now = System.currentTimeMillis();
        if (liveIdSnapshot != null && now - snapshotTakenAt < SNAPSHOT_TTL_MS) {
            return liveIdSnapshot;
        }

        Set<String> collected = fetchAllLiveIds();
        if (collected.isEmpty()) {
            // 실패한 스냅샷은 캐시하지 않는다. 다음 공고에서 다시 시도할 수 있어야 한다.
            return Collections.emptySet();
        }

        liveIdSnapshot = Collections.unmodifiableSet(collected);
        snapshotTakenAt = now;
        log.info("쿠팡 채용 API 스냅샷 갱신 — 열려 있는 공고 {}건", liveIdSnapshot.size());
        return liveIdSnapshot;
    }

    private Set<String> fetchAllLiveIds() {
        String rawJson = fetcher.apply(API_URL);
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptySet();
        }

        JSONArray jobs;
        try {
            jobs = new JSONObject(rawJson).optJSONArray("jobs");
        } catch (Exception e) {
            log.warn("쿠팡 채용 API 응답 파싱 실패: {}", e.getMessage());
            return Collections.emptySet();
        }
        if (jobs == null) {
            log.warn("쿠팡 채용 API 응답에 jobs 없음");
            return Collections.emptySet();
        }

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject item = jobs.optJSONObject(i);
            Object id = item == null ? null : item.opt("id");
            if (id != null) {
                ids.add(id.toString());
            }
        }
        return ids;
    }
}
