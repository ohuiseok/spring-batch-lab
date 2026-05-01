package com.example.batch.config;

import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Batch 6의 기본 설정은 메모리 기반 JobRepository를 사용할 수 있다.
 * 이 학습 프로젝트는 BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION,
 * BATCH_STEP_EXECUTION 같은 메타데이터 테이블을 직접 조회하며 동작을 확인하므로 JDBC 저장소를 사용한다.
 */
@Configuration
public class BatchMetadataConfig extends JdbcDefaultBatchConfiguration {
}
