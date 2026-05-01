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
class ListChunkJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("listChunkJob")
    private Job listChunkJob;

    @BeforeEach
    void setUp() {
        // 여러 Job Bean 중 1-2장의 listChunkJob만 실행하도록 테스트 대상 Job을 명시한다.
        jobLauncherTestUtils.setJob(listChunkJob);
    }

    @AfterEach
    void tearDown() {
        // 테스트마다 새로운 Batch 메타데이터 상태에서 시작하도록 실행 기록을 정리한다.
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("List Chunk Job reads five items and writes all items")
    void listChunkJob_launchJob_readsAndWritesFiveItems() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParameters());

        // Job이 정상 종료되어야 StepExecution count를 신뢰할 수 있다.
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).hasSize(1);

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStepName()).isEqualTo(ListChunkJobConfig.STEP_NAME);
        assertThat(stepExecution.getReadCount()).isEqualTo(5);
        assertThat(stepExecution.getWriteCount()).isEqualTo(5);
        assertThat(stepExecution.getFilterCount()).isZero();
        assertThat(stepExecution.getSkipCount()).isZero();
    }

    @Test
    @DisplayName("List Chunk Job records StepExecution counts in Batch metadata")
    void listChunkJob_launchJob_writesStepExecutionMetadata() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParameters());

        // chunk size가 2이고 item이 5개이므로 2개, 2개, 1개 단위로 총 3번 commit 된다.
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getCommitCount()).isEqualTo(3);

        // BATCH_STEP_EXECUTION에는 Step 실행 결과와 read/write/commit count가 함께 저장된다.
        Long readCount = stepExecutionMetadataValue(execution.getId(), "READ_COUNT");
        Long writeCount = stepExecutionMetadataValue(execution.getId(), "WRITE_COUNT");
        Long commitCount = stepExecutionMetadataValue(execution.getId(), "COMMIT_COUNT");

        assertThat(readCount).isEqualTo(5);
        assertThat(writeCount).isEqualTo(5);
        assertThat(commitCount).isEqualTo(3);
    }

    private JobParameters uniqueJobParameters() {
        // 같은 JobParameters로 이미 완료된 JobInstance를 다시 실행하지 않도록 매 테스트마다 값을 바꾼다.
        return new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }

    private Long stepExecutionMetadataValue(Long jobExecutionId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM BATCH_STEP_EXECUTION "
                        + "WHERE JOB_EXECUTION_ID = ? AND STEP_NAME = ?",
                Long.class,
                jobExecutionId,
                ListChunkJobConfig.STEP_NAME
        );
    }
}
