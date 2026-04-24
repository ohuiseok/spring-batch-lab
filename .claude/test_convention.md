# 테스트 컨벤션 (Spring Batch 프로젝트)

## 테스트 환경

| 라이브러리 | 역할 |
|-----------|------|
| JUnit 5 | 테스트 프레임워크 |
| Spring Boot Test | `@SpringBootTest` 등 통합 테스트 |
| `spring-batch-test` | `@SpringBatchTest`, `JobLauncherTestUtils`, `JobRepositoryTestUtils` |
| Testcontainers | MySQL 컨테이너 기반 실제 DB 테스트 |
| AssertJ | Fluent Assertion |
| Mockito (필요 시) | Reader/Processor/Writer 단위 테스트 보조 |

> **핵심 구조**: `JobLauncherTestUtils`로 Job을 직접 실행하고, Testcontainers가 띄운 MySQL에서 메타데이터와 결과를 검증한다.

```
[Test] --JobLauncherTestUtils.launchJob()--> [Job/Step]
                                                ↓ read / write
                                        [Testcontainers MySQL]
                                                ↓ assert
                                          [Test가 직접 SELECT로 검증]
```

---

## 1. 테스트 클래스 네이밍

| 테스트 종류 | 클래스명 규칙 | 예시 |
|------------|-------------|------|
| Job 전체 통합 테스트 | `XxxJobIntegrationTest` | `SettlementJobIntegrationTest` |
| Step 단위 테스트 | `XxxStepIntegrationTest` | `SettlementStepIntegrationTest` |
| Reader/Processor/Writer 단위 테스트 | `XxxTest` | `SettlementItemProcessorTest` |
| 슬라이스 테스트 | `XxxSliceTest` | `BatchMetadataSliceTest` |

---

## 2. 테스트 메서드 네이밍

**패턴**: `대상_시나리오_기대결과`

```java
@Test
void job_withValidRunDate_completesSuccessfully() { }

@Test
void processor_cancelledOrder_returnsNull() { }

@Test
void step_whenChunkFails_skipsUpToLimit() { }

@Test
void restart_afterFailure_resumesFromLastCommit() { }
```

또는 한글 + `@DisplayName` (가독성 우선):
```java
@Test
@DisplayName("정상 JobParameters 로 실행하면 COMPLETED 상태로 종료된다")
void 정상_파라미터_실행하면_COMPLETED() { }
```

---

## 3. Job 통합 테스트 기본 패턴

### `@SpringBatchTest` + `@SpringBootTest` + Testcontainers

```java
@SpringBootTest
@SpringBatchTest
@Testcontainers
@ActiveProfiles("test")
class SettlementJobIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        // 메타데이터 정리 — 테스트 간 독립성 확보
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("주문 데이터를 읽어 정산 테이블로 집계한다")
    void settlementJob_validInput_writesAggregatedRows() throws Exception {
        // Arrange — 테스트 데이터 삽입
        jdbcTemplate.update("INSERT INTO orders(id, amount, status) VALUES (?, ?, ?)",
                1L, 10_000, "PAID");

        JobParameters params = new JobParametersBuilder()
                .addString("run.date", "2026-04-24")
                .addLong("timestamp", System.currentTimeMillis())   // 매 테스트마다 새 JobInstance 강제
                .toJobParameters();

        // Act
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // Assert — Job 상태
        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        StepExecution step = execution.getStepExecutions().iterator().next();
        assertThat(step.getReadCount()).isEqualTo(1);
        assertThat(step.getWriteCount()).isEqualTo(1);
        assertThat(step.getFilterCount()).isZero();

        // Assert — 실제 테이블 결과
        Integer amount = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM settlement WHERE run_date = ?",
                Integer.class, "2026-04-24");
        assertThat(amount).isEqualTo(10_000);
    }
}
```

> **핵심 포인트**
> - `JobLauncherTestUtils.launchJob(JobParameters)`로 Job을 실행한다.
> - `ExitStatus`뿐 아니라 **readCount / writeCount / filterCount / skipCount**까지 검증한다.
> - 결과 테이블을 `JdbcTemplate`으로 직접 SELECT해서 확인한다 — 이것이 "배치 테스트"의 본질.
> - 일반 성공 테스트에서는 `timestamp` 같은 구분 파라미터로 독립성을 확보할 수 있다.
> - restart 테스트에서는 같은 `JobInstance`를 재사용해야 하므로 `timestamp` 같은 매번 달라지는 identifying parameter를 넣지 않는다.

### JobParameters 테스트 전략

| 테스트 목적 | 권장 방식 |
|------------|-----------|
| 일반 성공 테스트 | `timestamp` 또는 테스트별 고유 파라미터로 새 JobInstance 생성 |
| 실패 테스트 | 실패를 유발하는 고정 파라미터로 `FAILED` 상태 검증 |
| restart 테스트 | 같은 identifying JobParameters로 1차 `FAILED`, 2차 `COMPLETED` 검증 |
| 멱등성 테스트 | 같은 비즈니스 파라미터로 반복 실행 후 결과 테이블 중복 여부 검증 |

restart 테스트에서는 `BATCH_JOB_INSTANCE` row가 새로 생기지 않아야 하며,
같은 `JOB_INSTANCE_ID`에 새로운 `BATCH_JOB_EXECUTION` row가 추가되는지 확인한다.

---

## 4. Step 단독 테스트 — `launchStep()`

Job 전체를 돌리지 않고 특정 Step만 실행하고 싶을 때 사용.

```java
@Test
@DisplayName("aggregateStep만 실행했을 때 집계 결과가 정확하다")
void aggregateStep_runAlone_producesCorrectTotals() throws Exception {
    jdbcTemplate.update("INSERT INTO orders(id, amount, status) VALUES (?, ?, ?)", 1L, 1_000, "PAID");

    JobParameters params = new JobParametersBuilder()
            .addString("run.date", "2026-04-24")
            .toJobParameters();

    JobExecution execution = jobLauncherTestUtils.launchStep("aggregateStep", params);

    assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
}
```

---

## 5. 챕터별 주요 테스트 패턴

### 1단계 — Tasklet / 기본 Chunk

```java
@Test
@DisplayName("HelloTasklet은 한 번 실행되고 FINISHED를 반환한다")
void helloTasklet_runsOnce_returnsFinished() throws Exception {
    JobExecution execution = jobLauncherTestUtils.launchJob(
            new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters());

    assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    StepExecution step = execution.getStepExecutions().iterator().next();
    assertThat(step.getCommitCount()).isEqualTo(1);
}
```

### 3단계 — Processor 단위 테스트 (Spring Context 없이 POJO 테스트)

```java
class SettlementItemProcessorTest {

    private final SettlementItemProcessor processor = new SettlementItemProcessor();

    @Test
    @DisplayName("취소된 주문은 null을 반환해 Writer로 전달되지 않는다")
    void process_cancelledOrder_returnsNull() {
        Order cancelled = Order.builder().id(1L).status("CANCELLED").build();

        Settlement result = processor.process(cancelled);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("정상 주문은 Settlement DTO로 변환된다")
    void process_paidOrder_convertsToSettlement() {
        Order paid = Order.builder().id(1L).status("PAID").amount(5_000).build();

        Settlement result = processor.process(paid);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getAmount()).isEqualTo(5_000);
    }
}
```

### 5단계 — Skip / Restart 테스트

```java
@Test
@DisplayName("스킵 한도 내 예외는 건너뛰고 Job은 COMPLETED로 끝난다")
void step_exceptionWithinSkipLimit_skipsAndCompletes() throws Exception {
    // Arrange — 의도적으로 1건 오염 데이터 삽입
    jdbcTemplate.update("INSERT INTO orders(id, amount, status) VALUES (?, ?, ?)",
            99L, -1, "INVALID");

    JobExecution execution = jobLauncherTestUtils.launchJob(defaultParams());

    assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    StepExecution step = execution.getStepExecutions().iterator().next();
    assertThat(step.getSkipCount()).isEqualTo(1);
}

@Test
@DisplayName("중간에 실패한 Job을 같은 JobParameters로 재실행하면 실패 지점부터 이어서 처리된다")
void restart_afterFailure_resumesProcessing() throws Exception {
    // 1차: 의도적 실패 유발 (예: 특정 조건에서 예외 발생하도록 설정)
    JobParameters params = new JobParametersBuilder()
            .addString("run.date", "2026-04-24")
            .toJobParameters();

    JobExecution first = jobLauncherTestUtils.launchJob(params);
    assertThat(first.getExitStatus().getExitCode()).isEqualTo("FAILED");
    int firstWrite = first.getStepExecutions().iterator().next().getWriteCount();

    // 2차: 실패 원인 제거 후 같은 JobParameters로 재실행
    fixFailureCondition();
    JobExecution second = jobLauncherTestUtils.launchJob(params);

    assertThat(second.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    int secondWrite = second.getStepExecutions().iterator().next().getWriteCount();
    assertThat(firstWrite + secondWrite).isEqualTo(TOTAL_EXPECTED);
}
```

---

## 6. ItemReader / ItemWriter 단위 테스트

### Reader 테스트 (POJO + 실제 DB 미사용)

```java
@Test
@DisplayName("FlatFileItemReader는 CSV 3줄을 Order 3건으로 파싱한다")
void flatFileReader_csvWith3Lines_reads3Orders() throws Exception {
    FlatFileItemReader<Order> reader = new FlatFileItemReaderBuilder<Order>()
            .name("testReader")
            .resource(new ClassPathResource("orders-3lines.csv"))
            .delimited()
            .names("id", "amount", "status")
            .targetType(Order.class)
            .build();

    reader.open(new ExecutionContext());
    List<Order> result = new ArrayList<>();
    Order item;
    while ((item = reader.read()) != null) {
        result.add(item);
    }
    reader.close();

    assertThat(result).hasSize(3);
}
```

### Writer 테스트 — `write(Chunk)` 직접 호출

```java
@Test
@DisplayName("JdbcBatchItemWriter가 모든 아이템을 DB에 저장한다")
void writer_writesAllItems() throws Exception {
    JdbcBatchItemWriter<Settlement> writer = /* ... */;

    writer.write(new Chunk<>(List.of(
            new Settlement(1L, 1000),
            new Settlement(2L, 2000))));

    Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM settlement", Integer.class);
    assertThat(count).isEqualTo(2);
}
```

---

## 7. 테스트용 프로필 설정

`src/test/resources/application-test.yml`:

```yaml
spring:
  batch:
    job:
      enabled: false
    jdbc:
      initialize-schema: always
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

logging:
  level:
    org.springframework.batch: INFO
    com.example.batch: DEBUG
```

---

## 8. 테스트 원칙

1. **독립성**: 각 테스트는 다른 테스트에 의존하지 않는다. `@AfterEach`에서 `JobRepositoryTestUtils.removeJobExecutions()`로 메타데이터 정리
2. **실제 DB에 가깝게**: 메타데이터 테이블 동작은 실제 DB(MySQL)와 다르게 동작하는 부분이 있으므로, 통합 테스트는 Testcontainers 우선
3. **경계값 우선**: Skip 한도 직전/직후, 빈 입력, 재시작 시나리오를 우선 검증
4. **메타데이터까지 검증**: `readCount`, `writeCount`, `filterCount`, `skipCount`, `commitCount`를 기대값과 비교
5. **결과 테이블을 SQL로 확인**: Job 반환값만 믿지 말고 실제 테이블 상태를 SELECT로 검증

---

## 9. 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "com.example.batch.*JobIntegrationTest"

# 특정 메서드
./gradlew test --tests "com.example.batch.job.settlement.SettlementJobIntegrationTest.settlementJob_validInput_writesAggregatedRows"

# 테스트 결과 확인
open build/reports/tests/test/index.html
```

---

## 10. `spring-batch-test` 주요 API 레퍼런스

```java
// JobLauncherTestUtils — 단일 Job Bean이 있을 때 자동 설정
JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters);
JobExecution execution = jobLauncherTestUtils.launchStep("stepName", jobParameters);

// JobParameters 생성
JobParameters params = new JobParametersBuilder()
        .addString("run.date", "2026-04-24")
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters();

// JobRepositoryTestUtils — 메타데이터 정리
jobRepositoryTestUtils.removeJobExecutions();

// 여러 Job Bean이 있을 경우, 테스트 대상 Job을 명시적으로 주입
@Autowired
private Job settlementJob;

@BeforeEach
void setUp() {
    jobLauncherTestUtils.setJob(settlementJob);
}
```
