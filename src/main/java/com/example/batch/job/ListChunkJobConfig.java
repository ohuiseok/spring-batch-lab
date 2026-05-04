package com.example.batch.job;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ListChunkJobConfig {

    private static final String JOB_NAME = "listChunkJob";
    private static final int CHUNK_SIZE = 2;
    static final String STEP_NAME = "listChunkStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job listChunkJob() {
        // Job은 하나의 배치 실행 단위이며, 1-2장에서는 Chunk 기반 Step 하나만 실행한다.
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(listChunkStep())
                .build();
    }

    @Bean
    public Step listChunkStep() {
        // chunk(2)는 item 2개를 읽고 처리한 뒤 한 번에 write/commit 한다는 뜻이다.
        // 입력이 5건이면 2건, 2건, 1건으로 총 3번 write/commit 되는 흐름을 관찰할 수 있다.
        return new StepBuilder(STEP_NAME, jobRepository)
                .<String, String>chunk(CHUNK_SIZE)
                .reader(listItemReader())
                .writer(listItemWriter())
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public ListItemReader<String> listItemReader() {
        // ListItemReader는 메모리의 List를 순서대로 하나씩 반환하며 read()가 null이면 Step을 마무리한다.
        // 내부 인덱스를 가진 stateful 객체이므로, singleton으로 두면 재실행 시 빈 결과만 반환된다.
        // @StepScope로 Step 실행마다 새 인스턴스를 만들어 read 위치를 초기 상태로 되돌린다.
        return new ListItemReader<>(List.of("alpha", "bravo", "charlie", "delta", "echo"));
    }

    @Bean
    public ItemWriter<String> listItemWriter() {
        // Writer는 chunk 단위로 호출된다. 여기서는 DB 저장 대신 각 chunk 내용을 로그로 남긴다.
        return chunk -> log.info("listChunkStep write chunk: {}", chunk.getItems());
    }
}
