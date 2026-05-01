package com.example.batch.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RunDateJobConfig {

    public static final String PARAM_RUN_DATE = "run.date";
    static final String STEP_NAME = "runDateStep";
    static final String EXECUTION_CONTEXT_RUN_DATE_KEY = "processed.run.date";

    private static final String JOB_NAME = "runDateJob";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job runDateJob(@Qualifier("runDateStep") Step runDateStep) {
        // JobParameters는 JobInstance를 구분하는 입력값이다. 같은 Job 이름이라도 run.date가 다르면
        // Spring Batch는 다른 JobInstance로 보고 BATCH_JOB_INSTANCE에 별도 실행 단위를 만든다.
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(runDateStep)
                .build();
    }

    @Bean
    public Step runDateStep(@Qualifier("runDateTasklet") Tasklet runDateTasklet) {
        // 이 Step은 1-3장의 목표인 JobParameters 전달 흐름에 집중하기 위해 Tasklet 하나로 구성한다.
        // 실제 운영 Job에서는 이 run.date를 Reader의 조회 조건이나 Writer의 적재 기준일로 자주 사용한다.
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(runDateTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet runDateTasklet(@Value("#{jobParameters['" + PARAM_RUN_DATE + "']}") String runDate) {
        // @StepScope는 Step 실행 시점에 Bean을 만들게 해준다. 덕분에 애플리케이션 시작 시점이 아니라
        // JobLauncher가 넘긴 JobParameters를 안전하게 주입받는 Late Binding이 동작한다.
        return new RunDateTasklet(runDate);
    }

    private static class RunDateTasklet implements Tasklet {

        private final String runDate;

        private RunDateTasklet(String runDate) {
            this.runDate = runDate;
        }

        @Override
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
            if (!StringUtils.hasText(runDate)) {
                throw new IllegalArgumentException("run.date JobParameter is required");
            }

            // ExecutionContext는 Spring Batch가 재시작을 위해 보관하는 실행 상태 저장소다.
            // 여기서는 학습 목적으로 run.date가 실제 Step 실행에 반영됐음을 테스트에서 확인할 수 있게 남긴다.
            contribution.getStepExecution()
                    .getExecutionContext()
                    .putString(EXECUTION_CONTEXT_RUN_DATE_KEY, runDate);

            log.info("runDateStep processed run.date={}", runDate);
            return RepeatStatus.FINISHED;
        }
    }
}
