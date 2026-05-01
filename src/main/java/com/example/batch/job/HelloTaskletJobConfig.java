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
        // A Job is the top-level batch unit. This chapter starts with one Step only.
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(helloTaskletStep())
                .build();
    }

    @Bean
    public Step helloTaskletStep() {
        // A Tasklet Step runs one task inside a transaction and finishes when the Tasklet returns FINISHED.
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(helloTasklet, transactionManager)
                .build();
    }
}
