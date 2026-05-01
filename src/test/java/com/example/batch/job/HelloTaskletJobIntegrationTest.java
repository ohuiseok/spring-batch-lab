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
class HelloTaskletJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("helloTaskletJob")
    private Job helloTaskletJob;

    @BeforeEach
    void setUp() {
        // 여러 Job Bean이 있어도 이 테스트는 1-1장의 helloTaskletJob만 실행하도록 명시한다.
        jobLauncherTestUtils.setJob(helloTaskletJob);
    }

    @AfterEach
    void tearDown() {
        // 테스트 간 JobInstance/JobExecution 기록이 섞이지 않도록 Batch 메타데이터를 정리한다.
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("Hello Tasklet Job completes a single step")
    void helloTaskletJob_launchJob_completesSingleStep() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParameters());

        // Job 전체가 정상 종료되었는지 확인한다.
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).hasSize(1);

        // Tasklet Step은 item을 읽거나 쓰지 않지만, Step 실행 트랜잭션은 한 번 commit 된다.
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStepName()).isEqualTo(HelloTaskletJobConfig.STEP_NAME);
        assertThat(stepExecution.getCommitCount()).isEqualTo(1);
        assertThat(stepExecution.getReadCount()).isZero();
        assertThat(stepExecution.getWriteCount()).isZero();
        assertThat(stepExecution.getFilterCount()).isZero();
        assertThat(stepExecution.getSkipCount()).isZero();
    }

    @Test
    @DisplayName("Hello Tasklet Job writes Batch metadata")
    void helloTaskletJob_launchJob_writesBatchMetadata() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParameters());

        // 세 테이블의 row를 함께 보면 JobInstance -> JobExecution -> StepExecution 관계를 확인할 수 있다.
        Long jobInstanceCount = countByJobInstanceId(
                "BATCH_JOB_INSTANCE",
                "JOB_INSTANCE_ID",
                execution.getJobInstance().getInstanceId()
        );
        Long jobExecutionCount = countByJobInstanceId(
                "BATCH_JOB_EXECUTION",
                "JOB_INSTANCE_ID",
                execution.getJobInstance().getInstanceId()
        );
        Long stepExecutionCount = countByJobExecutionId(execution.getId());

        assertThat(jobInstanceCount).isOne();
        assertThat(jobExecutionCount).isOne();
        assertThat(stepExecutionCount).isOne();
    }

    private JobParameters uniqueJobParameters() {
        // timestamp를 identifying parameter로 넣어 테스트마다 새로운 JobInstance를 만든다.
        return new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }

    private Long countByJobInstanceId(String tableName, String columnName, Long jobInstanceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Long.class,
                jobInstanceId
        );
    }

    private Long countByJobExecutionId(Long jobExecutionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?",
                Long.class,
                jobExecutionId
        );
    }
}
