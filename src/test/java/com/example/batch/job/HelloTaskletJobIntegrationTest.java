package com.example.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
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

    @AfterEach
    void tearDown() {
        // Keep each test independent by clearing Batch metadata after assertions finish.
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("Hello Tasklet Job completes a single step")
    void helloTaskletJob_launchJob_completesSingleStep() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParameters());

        // Job-level result: the whole Job ended successfully.
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).hasSize(1);

        // Step-level result: Tasklet Step has no item read/write counts, but it still commits once.
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

        // These three rows show the JobInstance -> JobExecution -> StepExecution relationship.
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
        // timestamp creates a new JobInstance for each test run.
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
