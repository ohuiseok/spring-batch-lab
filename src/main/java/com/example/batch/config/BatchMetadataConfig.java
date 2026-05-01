package com.example.batch.config;

import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BatchMetadataConfig extends JdbcDefaultBatchConfiguration {
    /*
     * Spring Batch 6 defaults to an in-memory, resourceless JobRepository.
     * This chapter observes BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, and
     * BATCH_STEP_EXECUTION directly, so the project uses the JDBC repository.
     */
}
