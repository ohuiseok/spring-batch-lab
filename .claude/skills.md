# 기술 스택 & 핵심 개념 (Spring Batch)

## 프로젝트 의존성

| 의존성 | 버전 | 용도 |
|--------|------|------|
| Spring Boot | 4.0.x | 기반 프레임워크 |
| Spring Batch | Boot 번들 버전 | 배치 처리 프레임워크 |
| Spring Data JPA | Boot 내장 | Entity 기반 Reader/Writer |
| JDBC (HikariCP) | Boot 내장 | `Jdbc*ItemReader/Writer` |
| MySQL | 로컬 설치 | 로컬 실행 및 메타데이터 확인 |
| MySQL Driver | 최신 | 로컬/통합 테스트용 |
| Testcontainers | 최신 | MySQL 컨테이너 실행 |
| `spring-batch-test` | Boot 내장 | `JobLauncherTestUtils` 등 |
| Lombok | 최신 | 보일러플레이트 제거 |
| Java | 21 | 언어 버전 |

> **참고**: Spring Batch 5 이후 `JobBuilderFactory`, `StepBuilderFactory`가 deprecated되고,
> `JobBuilder` / `StepBuilder`를 `JobRepository`와 `PlatformTransactionManager`를 받아 직접 사용합니다.
> 또한 `@EnableBatchProcessing`은 더 이상 필수가 아니며, Spring Boot가 자동 구성을 제공합니다.

---

## 1. Spring Batch 핵심 개념

### Job / JobInstance / JobExecution

```
Job (설계)          ─ 반복 가능한 배치 작업의 정의
  └── JobInstance   ─ 특정 JobParameters 조합으로 고유하게 식별되는 논리적 실행
        └── JobExecution  ─ 실제 한 번의 실행 기록 (성공/실패 가능)
              └── StepExecution  ─ 각 Step의 실행 기록
```

**중요**: 같은 `JobParameters`로 실행하면 같은 `JobInstance`로 취급된다.
- `JobInstance`가 이미 `COMPLETED` 상태면 재실행 불가 (`JobInstanceAlreadyCompleteException`)
- `JobInstance`가 `FAILED` 상태면 같은 파라미터로 재실행 시 이전 Execution을 이어받는다 (Restart)
- 매번 새 `JobInstance`를 만들고 싶다면 `RunIdIncrementer`를 사용하거나 `timestamp` 같은 고유 파라미터 추가

### 메타데이터 테이블

| 테이블 | 역할 |
|--------|------|
| `BATCH_JOB_INSTANCE` | Job 이름 + 파라미터 해시로 유일한 Job 인스턴스 |
| `BATCH_JOB_EXECUTION` | 각 실행 기록 (시작/종료 시각, ExitStatus) |
| `BATCH_JOB_EXECUTION_PARAMS` | 실행 시 넘긴 JobParameters |
| `BATCH_JOB_EXECUTION_CONTEXT` | Job 수준 ExecutionContext (직렬화된 형태) |
| `BATCH_STEP_EXECUTION` | Step 단위 실행 기록 (read/write/skip count 등) |
| `BATCH_STEP_EXECUTION_CONTEXT` | Step 수준 ExecutionContext |
| `BATCH_JOB_SEQ`, `BATCH_JOB_EXECUTION_SEQ`, `BATCH_STEP_EXECUTION_SEQ` | ID 시퀀스 |

> 학습 초반에는 **한 챕터마다 이 테이블들을 직접 SELECT**해서 어떤 값이 어떻게 기록되는지 확인하는 습관을 반드시 들이세요. Spring Batch의 거의 모든 동작이 이 테이블에 흔적을 남깁니다.
> 자세한 관찰 질문과 확인 SQL은 `docs/metadata-tables.md`를 참고합니다.

### Chunk 지향 처리 vs Tasklet

| 구분 | 사용 상황 |
|------|----------|
| **Tasklet** | 단순/단일 동작 (파일 삭제, 단일 SQL 실행, 외부 API 호출 1회 등) |
| **Chunk 지향** | 대량 데이터를 **read → process → write**로 반복 처리 (배치의 대부분이 이 방식) |

Chunk 지향 처리의 트랜잭션 경계:

```
[read × chunkSize] → [process × chunkSize] → [write Chunk] → commit
                                                                ↓
                                                            다음 chunk
```

- 한 chunk = 한 트랜잭션
- 중간에 예외가 발생하면 해당 chunk 전체가 롤백
- Skip/Retry가 활성화되면 더 복잡한 트랜잭션 분리가 일어남 (한 건씩 재시도)

---

## 2. ItemReader

| Reader | 특징 |
|--------|------|
| `FlatFileItemReader` | CSV/고정폭 파일. `LineMapper`로 객체 변환 |
| `JdbcCursorItemReader` | DB 커서 유지, 단일 스레드 전제 (커넥션 점유 시간 김) |
| `JdbcPagingItemReader` | 페이지 단위 쿼리, 멀티스레드 환경에 적합 |
| `JpaPagingItemReader` | JPA Entity 로딩. 페이지마다 `EntityManager.clear()` 필요 |
| `StaxEventItemReader` | XML |
| `ListItemReader` | 메모리 List (주로 테스트/학습용) |

**Paging vs Cursor 선택 기준**:
- Paging: 데이터양이 크고 멀티스레드 확장 가능성 있음, 중간에 DB 락 유지하고 싶지 않음
- Cursor: 데이터량이 작거나 페이지 쿼리 오프셋 부담이 큰 경우

**커스텀 Reader 구현**

```java
@Component
@StepScope
public class CustomItemReader implements ItemReader<Order> {

    private Iterator<Order> iterator;

    @Override
    public Order read() {
        if (iterator == null) {
            iterator = fetchAll().iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }
}
```

> `read()`가 `null`을 반환하면 Spring Batch는 **Reader가 소진됐다고 판단**하고 Step을 끝냅니다.

---

## 3. ItemProcessor

```java
public interface ItemProcessor<I, O> {
    O process(I item) throws Exception;
}
```

- 반환값이 `null`이면 **해당 아이템은 Writer로 전달되지 않고 `filterCount`가 증가**
- 예외를 던지면 Skip/Retry/실패 정책에 따라 처리됨
- `CompositeItemProcessor`로 여러 Processor를 체이닝 가능

```java
@Bean
public CompositeItemProcessor<Order, Settlement> compositeProcessor() {
    CompositeItemProcessor<Order, Settlement> processor = new CompositeItemProcessor<>();
    processor.setDelegates(List.of(validator, converter));
    return processor;
}
```

---

## 4. ItemWriter

| Writer | 특징 |
|--------|------|
| `FlatFileItemWriter` | CSV/텍스트 파일 출력 |
| `JdbcBatchItemWriter` | `PreparedStatement.addBatch()` + `executeBatch()`로 대량 INSERT/UPDATE |
| `JpaItemWriter` | `EntityManager.persist`. 대량 처리에는 JDBC가 더 빠른 경우가 많음 |
| `CompositeItemWriter` | 여러 Writer로 동시 출력 |
| `ClassifierCompositeItemWriter` | 조건별로 다른 Writer 라우팅 |

**커스텀 Writer**

```java
@Component
public class LoggingItemWriter implements ItemWriter<Settlement> {

    @Override
    public void write(Chunk<? extends Settlement> chunk) {
        chunk.forEach(s -> log.info("settlement={}", s));
    }
}
```

> Spring Batch 5부터 `ItemWriter.write()`의 인자가 `List<? extends T>`에서 **`Chunk<? extends T>`** 로 변경됐습니다.

---

## 5. Listener

| Listener | 호출 시점 |
|----------|---------|
| `JobExecutionListener` | Job 시작 전 / 종료 후 |
| `StepExecutionListener` | Step 시작 전 / 종료 후 |
| `ChunkListener` | 각 chunk 시작 / 종료 / 에러 |
| `ItemReadListener` | 각 read 전/후, 에러 |
| `ItemProcessListener` | process 전/후, 에러 |
| `ItemWriteListener` | write 전/후, 에러 |
| `SkipListener` | skip 발생 시 (read/process/write 각각) |
| `RetryListener` | retry 발생 시 |

**로그·집계·알림을 Listener에 몰아두면** 본 로직이 깨끗하게 유지된다.

```java
@Component
@Slf4j
public class JobDurationListener implements JobExecutionListener {

    @Override
    public void afterJob(JobExecution execution) {
        Duration duration = Duration.between(execution.getStartTime(), execution.getEndTime());
        log.info("[{}] took {}ms, status={}",
                execution.getJobInstance().getJobName(),
                duration.toMillis(),
                execution.getExitStatus());
    }
}
```

---

## 6. Flow 제어

### 기본 흐름
```java
return new JobBuilder("myJob", jobRepository)
        .start(step1)
        .next(step2)
        .next(step3)
        .build();
```

### 조건부 분기 (ExitStatus 기반)
```java
return new JobBuilder("myJob", jobRepository)
        .start(step1)
            .on("FAILED").to(recoveryStep)
        .from(step1)
            .on("*").to(step2)
        .end()
        .build();
```

### Decider
```java
@Component
public class RunModeDecider implements JobExecutionDecider {

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        String mode = jobExecution.getJobParameters().getString("mode");
        return "FULL".equals(mode)
                ? new FlowExecutionStatus("FULL")
                : new FlowExecutionStatus("INCREMENTAL");
    }
}
```

### Split (병렬 Flow)
```java
Flow flowA = new FlowBuilder<SimpleFlow>("flowA").start(stepA).build();
Flow flowB = new FlowBuilder<SimpleFlow>("flowB").start(stepB).build();

return new JobBuilder("parallelJob", jobRepository)
        .start(new FlowBuilder<SimpleFlow>("split")
                .split(new SimpleAsyncTaskExecutor())
                .add(flowA, flowB)
                .build())
        .end()
        .build();
```

---

## 7. 예외 처리 — Skip / Retry / Restart

### Skip / Retry
```java
return new StepBuilder("robustStep", jobRepository)
        .<Input, Output>chunk(100, transactionManager)
        .reader(reader).processor(processor).writer(writer)
        .faultTolerant()
        .skip(DataIntegrityViolationException.class)
        .skipLimit(10)
        .retry(TransientDataAccessException.class)
        .retryLimit(3)
        .listener(skipListener)
        .build();
```

### Restart 기본 동작
1. 같은 `JobParameters`로 재실행 → 같은 `JobInstance` 재사용
2. 이전 `JobExecution`이 `FAILED`이면 Step들을 순서대로 확인
3. `COMPLETED`였던 Step은 건너뛰고, 실패했던 Step부터 재실행
4. `ItemStream`을 구현한 Reader/Writer는 `ExecutionContext`에서 마지막 위치를 복구

### `allowStartIfComplete(true)`
완료된 Step이라도 매번 다시 실행하고 싶을 때 (집계처럼 전체를 다시 해야 하는 경우).

---

## 8. 성능 & 확장

### Multi-threaded Step
```java
return new StepBuilder("mtStep", jobRepository)
        .<Input, Output>chunk(100, transactionManager)
        .reader(reader).processor(processor).writer(writer)
        .taskExecutor(new SimpleAsyncTaskExecutor())
        .build();
```

> Multi-threaded Step에서는 **Reader가 thread-safe**여야 한다.
> `JdbcCursorItemReader`는 thread-safe가 아니므로 `JdbcPagingItemReader`를 쓰거나 `SynchronizedItemStreamReader`로 감싼다.

### Partitioning
대용량 데이터를 ID 범위 등으로 분할해 **여러 Step 인스턴스**가 병렬 처리.

```
MasterStep (Partitioner로 ID 범위 나눔)
  ├── WorkerStep (id 1~10000)
  ├── WorkerStep (id 10001~20000)
  └── WorkerStep (id 20001~30000)
```

### 성능 튜닝 포인트
- Chunk 크기: 너무 작으면 커밋 오버헤드, 너무 크면 롤백 비용/메모리 부담 → **보통 100~1000 사이에서 측정 후 결정**
- Reader 페이지 사이즈 = Chunk 크기가 일반적
- JDBC fetchSize 조정 (JDBC 드라이버 기본값이 작은 경우가 많음)
- `JdbcBatchItemWriter`로 INSERT 묶기

---

## 9. Spring Batch 5 / 4 주요 변경점 (Boot 3+ 사용 시)

| 항목 | 변경 내용 |
|------|---------|
| `@EnableBatchProcessing` | **더 이상 필수 아님** (Spring Boot가 자동 구성) |
| `JobBuilderFactory` / `StepBuilderFactory` | deprecated → `new JobBuilder(name, jobRepository)` |
| `PlatformTransactionManager` | StepBuilder 사용 시 **명시적으로 주입 필요** |
| `ItemWriter.write()` 인자 | `List<? extends T>` → `Chunk<? extends T>` |
| `JobExecution.getCreateTime()` 등 | `Date` → `LocalDateTime` |
| Observation API | Micrometer `Observation`으로 관측성 개선 |

---

## 10. 자주 쓰는 설정 프로퍼티

```yaml
spring:
  batch:
    job:
      enabled: false               # 자동 실행 끄기
    jdbc:
      initialize-schema: always    # 학습용. 운영은 never
      table-prefix: BATCH_          # 기본값. 필요 시 변경 가능

logging:
  level:
    org.springframework.batch: INFO
    org.springframework.batch.core.step: DEBUG   # Step 실행 상세
    org.hibernate.SQL: DEBUG
```

---

## 11. 학습 참고 자료

- [Spring Batch 공식 문서](https://docs.spring.io/spring-batch/reference/)
- [Spring Batch 5.0 Migration Guide](https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-5.0-Migration-Guide)
- [Spring Boot Batch 자동 구성](https://docs.spring.io/spring-boot/reference/howto/batch.html)
- [Testcontainers for Java - MySQL](https://java.testcontainers.org/modules/databases/mysql/)
- 이동욱(jojoldu) 블로그의 Spring Batch 시리즈 (Batch 4.x 기반이지만 개념 설명이 훌륭 — 문법 차이만 주의)
