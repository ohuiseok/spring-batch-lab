package com.example.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBatchTest
@SpringBootTest
class RunDateJobIntegrationTest {

    private static final String RUN_DATE = "2026-05-01";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("runDateJob")
    private Job runDateJob;

    @BeforeEach
    void setUp() {
        // 여러 Job Bean 중 1-3장의 runDateJob만 실행하도록 테스트 대상 Job을 명시한다.
        jobLauncherTestUtils.setJob(runDateJob);
    }

    @AfterEach
    void tearDown() {
        // 다음 테스트가 같은 JobRepository 상태에 의존하지 않도록 Batch 메타데이터를 정리한다.
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("run.date JobParameter is injected into the Step and stored in ExecutionContext")
    void runDateJob_withRunDateParameter_completesAndStoresRunDate() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(runDateJobParameters(RUN_DATE));

        // run.date가 정상 주입되면 Step이 COMPLETED로 끝나고, Tasklet은 한 번의 트랜잭션 안에서 commit 된다.
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).hasSize(1);

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStepName()).isEqualTo(RunDateJobConfig.STEP_NAME);
        assertThat(stepExecution.getCommitCount()).isEqualTo(1);
        assertThat(stepExecution.getReadCount()).isZero();
        assertThat(stepExecution.getWriteCount()).isZero();
        assertThat(stepExecution.getExecutionContext()
                .getString(RunDateJobConfig.EXECUTION_CONTEXT_RUN_DATE_KEY))
                .isEqualTo(RUN_DATE);
    }

    @Test
    @DisplayName("run.date JobParameter is recorded in Batch metadata")
    void runDateJob_withRunDateParameter_writesJobParameterMetadata() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(runDateJobParameters(RUN_DATE));

        // BATCH_JOB_EXECUTION_PARAMS에는 실제 실행에 전달된 JobParameters가 저장된다.
        // 이 테이블을 보면 어떤 입력값으로 JobInstance/JobExecution이 만들어졌는지 추적할 수 있다.
        String storedRunDate = jdbcTemplate.queryForObject(
                "SELECT PARAMETER_VALUE FROM BATCH_JOB_EXECUTION_PARAMS "
                        + "WHERE JOB_EXECUTION_ID = ? AND PARAMETER_NAME = ?",
                String.class,
                execution.getId(),
                RunDateJobConfig.PARAM_RUN_DATE
        );

        assertThat(storedRunDate).isEqualTo(RUN_DATE);
    }

    @Test
    @DisplayName("missing run.date JobParameter fails the Job")
    void runDateJob_withoutRunDateParameter_fails() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParametersOnly());

        // 필수 JobParameters가 없으면 잘못된 기준일로 처리하지 않고 FAILED로 끝낸다.
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getRollbackCount()).isEqualTo(1);
        assertThat(stepExecution.getFailureExceptions())
                .anySatisfy(exception -> assertThat(exception)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("run.date"));
    }

    private JobParameters runDateJobParameters(String runDate) {
        // timestamp는 일반 성공 테스트의 독립성을 위한 값이고, run.date는 비즈니스 의미가 있는 JobParameter다.
        return new JobParametersBuilder()
                .addString(RunDateJobConfig.PARAM_RUN_DATE, runDate)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }

    private JobParameters uniqueJobParametersOnly() {
        return new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }
}
