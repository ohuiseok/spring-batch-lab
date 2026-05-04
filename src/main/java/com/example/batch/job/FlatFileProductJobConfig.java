package com.example.batch.job;

import com.example.batch.domain.ProductCsvRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class FlatFileProductJobConfig {

    private static final String JOB_NAME = "flatFileProductJob";
    private static final String READER_NAME = "flatFileProductItemReader";
    private static final int CHUNK_SIZE = 2;
    static final String STEP_NAME = "flatFileProductStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job flatFileProductJob() {
        // 2-1장에서는 파일 Reader가 CSV 한 줄을 item 하나로 읽고, StepExecution의 read/write count에
        // 그 결과가 어떻게 기록되는지 관찰한다.
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(flatFileProductStep())
                .build();
    }

    @Bean
    public Step flatFileProductStep() {
        // FlatFileItemReader도 ItemStream이므로 open/update/close 생명주기를 통해 현재 읽은 위치를
        // ExecutionContext에 저장할 수 있다. 여기서는 chunk(2) 단위 커밋과 파일 read count 관찰에 집중한다.
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductCsvRow, ProductCsvRow>chunk(CHUNK_SIZE)
                .reader(flatFileProductItemReader())
                .writer(flatFileProductItemWriter())
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<ProductCsvRow> flatFileProductItemReader() {
        return new FlatFileItemReaderBuilder<ProductCsvRow>()
                .name(READER_NAME)
                .resource(new ClassPathResource("sample/products.csv"))
                .linesToSkip(1)
                .lineMapper(productLineMapper())
                .build();
    }

    private DefaultLineMapper<ProductCsvRow> productLineMapper() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("id", "name", "price");

        DefaultLineMapper<ProductCsvRow> lineMapper = new DefaultLineMapper<>();
        // DefaultLineMapper는 CSV 한 줄을 두 단계로 처리한다.
        // 먼저 LineTokenizer가 문자열을 FieldSet으로 나누고, FieldSetMapper가 도메인 객체로 변환한다.
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSet -> new ProductCsvRow(
                fieldSet.readLong("id"),
                fieldSet.readString("name"),
                fieldSet.readLong("price")
        ));

        return lineMapper;
    }

    @Bean
    public ItemWriter<ProductCsvRow> flatFileProductItemWriter() {
        // Writer는 Reader가 변환한 item 목록을 chunk 단위로 받는다.
        // 아직 DB 저장 전 단계이므로 로그로 출력하고, 테스트는 StepExecution count와 메타데이터를 검증한다.
        return chunk -> log.info("flatFileProductStep write chunk: {}", chunk.getItems());
    }
}
