package com.nklcbdty.batch.nklcbdty.batch.companyfeature.text;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** 본문 소스들이 같이 쓰는 GET. 실패하면 null. */
@Slf4j
final class HttpText {

    static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private HttpText() {
    }

    static Function<String, String> defaultFetcher() {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
        return url -> {
            Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("⚠️ 공고 본문 응답 실패 status={} url={}", response.code(), url);
                    return null;
                }
                ResponseBody body = response.body();
                return body == null ? null : body.string();
            } catch (Exception e) {
                log.warn("⚠️ 공고 본문 조회 실패 url={} - {}", url, e.getMessage());
                return null;
            }
        };
    }
}
