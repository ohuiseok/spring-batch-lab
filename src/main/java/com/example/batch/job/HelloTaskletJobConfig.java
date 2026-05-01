package com.example.batch.job;

import com.example.batch.tasklet.HelloTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class HelloTaskletJobConfig {

    private static final String JOB_NAME = "helloTaskletJob";
    static final String STEP_NAME = "helloTaskletStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final HelloTasklet helloTasklet;

    @Bean
    public Job helloTaskletJob() {
        // Job은 배치 실행의 최상위 단위이며, 1-1장에서는 Step 하나만 실행한다.
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(helloTaskletStep())
                .build();
    }

    @Bean
    public Step helloTaskletStep() {
        // Tasklet Step은 하나의 작업을 트랜잭션 안에서 실행하고, FINISHED가 반환되면 Step을 종료한다.
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(helloTasklet, transactionManager)
                .build();
    }
}
