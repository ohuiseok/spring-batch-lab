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
class FlatFileProductJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("flatFileProductJob")
    private Job flatFileProductJob;

    @BeforeEach
    void setUp() {
        // 여러 Job Bean 중 2-1장의 flatFileProductJob만 실행하도록 테스트 대상 Job을 명시한다.
        jobLauncherTestUtils.setJob(flatFileProductJob);
    }

    @AfterEach
    void tearDown() {
        // 파일 Reader 학습 테스트도 Batch 메타데이터를 사용하므로 테스트마다 실행 기록을 분리한다.
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("FlatFileItemReader reads CSV rows and writes all items")
    void flatFileProductJob_launchJob_readsCsvRowsAndWritesAllItems() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParameters());

        // CSV 헤더 1줄은 linesToSkip(1)로 제외되고, 데이터 5줄만 item으로 읽힌다.
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).hasSize(1);

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStepName()).isEqualTo(FlatFileProductJobConfig.STEP_NAME);
        assertThat(stepExecution.getReadCount()).isEqualTo(5);
        assertThat(stepExecution.getWriteCount()).isEqualTo(5);
        assertThat(stepExecution.getFilterCount()).isZero();
        assertThat(stepExecution.getSkipCount()).isZero();
    }

    @Test
    @DisplayName("FlatFileItemReader records chunk counts in Batch metadata")
    void flatFileProductJob_launchJob_writesStepExecutionMetadata() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParameters());

        // chunk size가 2이고 CSV 데이터가 5건이므로 2건, 2건, 1건 단위로 총 3번 commit 된다.
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getCommitCount()).isEqualTo(3);

        // BATCH_STEP_EXECUTION을 직접 조회하면 Reader/Writer가 처리한 건수와 커밋 횟수가 저장된 것을 볼 수 있다.
        Long readCount = stepExecutionMetadataValue(execution.getId(), "READ_COUNT");
        Long writeCount = stepExecutionMetadataValue(execution.getId(), "WRITE_COUNT");
        Long commitCount = stepExecutionMetadataValue(execution.getId(), "COMMIT_COUNT");

        assertThat(readCount).isEqualTo(5);
        assertThat(writeCount).isEqualTo(5);
        assertThat(commitCount).isEqualTo(3);
    }

    private JobParameters uniqueJobParameters() {
        // 테스트 독립성을 위해 매번 새로운 JobInstance가 만들어지도록 timestamp를 identifying parameter로 사용한다.
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
                FlatFileProductJobConfig.STEP_NAME
        );
    }
}
