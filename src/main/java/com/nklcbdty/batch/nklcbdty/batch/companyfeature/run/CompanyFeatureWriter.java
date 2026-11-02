package com.nklcbdty.batch.nklcbdty.batch.companyfeature.run;

import java.time.LocalDateTime;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.nklcbdty.batch.nklcbdty.batch.companyfeature.repository.CompanyFeatureJdbcRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompanyFeatureWriter implements ItemWriter<PostingFeatures> {

    private final CompanyFeatureJdbcRepository repository;

    @Override
    public void write(Chunk<? extends PostingFeatures> items) {
        LocalDateTime now = LocalDateTime.now();
        for (PostingFeatures item : items) {
            repository.replaceForJob(item.job(), item.hits(), now);
        }
    }
}
