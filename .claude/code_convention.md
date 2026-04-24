# 코드 컨벤션 (Spring Batch 프로젝트)

## 프로젝트 환경
- Java 21
- Spring Boot 4.0.x
- Spring Batch (Boot 번들 버전)
- Spring Data JPA / JDBC
- Testcontainers + MySQL (통합 테스트용)

---

## 1. 패키지 구조

```
com.example.batch
├── config/           # DataSource, Batch 공통 설정
├── job/              # Job 정의 (도메인별 하위 패키지 권장)
│   └── settlement/   # 예: 정산 배치
├── step/             # Step / Tasklet 구현
├── reader/           # 커스텀 ItemReader
├── processor/        # ItemProcessor 구현
├── writer/           # 커스텀 ItemWriter
├── listener/         # Job / Step / Skip / Retry Listener
├── domain/           # Entity, DTO
├── repository/       # Spring Data Repository (필요 시)
└── BatchApplication.java
```

Job이 늘어나면 `job/settlement/`, `job/migration/` 처럼 **Job 단위 패키지**로 분리해
해당 Job에서만 쓰는 Reader/Processor/Writer를 함께 모아두는 것을 권장한다.

---

## 2. 네이밍 규칙

### 클래스
- 설정 클래스: `XxxJobConfig`, `XxxStepConfig`, `XxxBatchConfig`
  - 예: `SettlementJobConfig`, `BatchMetadataConfig`
- Reader: `XxxItemReader` 또는 커스텀 클래스에 한해 `XxxReader`
- Processor: `XxxItemProcessor`
- Writer: `XxxItemWriter`
- Tasklet: `XxxTasklet` (예: `HelloTasklet`)
- Listener: `XxxJobListener`, `XxxStepListener`, `XxxSkipListener`, `XxxRetryListener`
- Decider: `XxxDecider`
- Partitioner: `XxxPartitioner`

### 메서드
- `camelCase`, 동사로 시작 (`configure`, `process`, `read`, `write`, `build`)
- Job/Step Bean 팩토리 메서드는 **리턴 타입이 바로 드러나도록** 명명
  - `public Job settlementJob(...)` / `public Step settlementStep(...)`

### 상수
- `UPPER_SNAKE_CASE`, `private static final`
- JobParameters key는 상수로 분리: `public static final String PARAM_RUN_DATE = "run.date";`

### 변수
- `camelCase`, 의미 있는 이름
- Reader/Writer 체인에서 chunk 크기는 `CHUNK_SIZE` 등 상수로 노출

---

## 3. 애노테이션 순서

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementItemProcessor implements ItemProcessor<Order, Settlement> {
```

- 스프링 애노테이션(`@Component`, `@Configuration`) → Lombok(`@RequiredArgsConstructor`, `@Slf4j`) 순서
- `@Configuration` 클래스에는 되도록 Job/Step 빈 팩토리만 두고, 도메인 로직은 Reader/Processor/Writer 빈 클래스로 분리

---

## 4. Spring Batch 컨벤션

### Job / Step 정의 (Java Config + Builder 권장)

```java
@Configuration
@RequiredArgsConstructor
public class SettlementJobConfig {

    private static final String JOB_NAME = "settlementJob";
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final SettlementItemReader reader;
    private final SettlementItemProcessor processor;
    private final SettlementItemWriter writer;

    @Bean
    public Job settlementJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(settlementStep())
                .build();
    }

    @Bean
    public Step settlementStep() {
        return new StepBuilder("settlementStep", jobRepository)
                .<Order, Settlement>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
```

> **핵심**: Spring Batch 5 이후에는 `JobBuilderFactory`/`StepBuilderFactory`가 **deprecated**다.
> `new JobBuilder(name, jobRepository)`, `new StepBuilder(name, jobRepository)`를 직접 사용한다.

### Tasklet 구현

```java
@Component
@Slf4j
public class HelloTasklet implements Tasklet {

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Hello, Batch!");
        return RepeatStatus.FINISHED;
    }
}
```

### ItemProcessor 구현

```java
@Component
@RequiredArgsConstructor
public class SettlementItemProcessor implements ItemProcessor<Order, Settlement> {

    @Override
    public Settlement process(Order order) {
        if (order.isCancelled()) {
            return null;    // null 반환 → 해당 아이템은 Writer로 전달되지 않음 (필터링)
        }
        return Settlement.from(order);
    }
}
```

> **필터링 시 주의**: `null`을 반환하면 Writer로 넘어가지 않고 `filterCount`가 증가한다.
> 예외를 던지는 것과는 다르다 (예외는 Skip/Retry 대상).

### @StepScope / @JobScope

- `JobParameters`를 받아 초기화해야 하는 Reader/Processor/Writer는 반드시 `@StepScope`를 붙인다
- 이렇게 해야 **Late Binding**(Step 실행 시점에 파라미터 주입)이 동작한다
- Job 단위 파라미터만 쓸 경우 `@JobScope`

```java
@Bean
@StepScope
public JdbcPagingItemReader<Order> orderReader(
        @Value("#{jobParameters['run.date']}") String runDate,
        DataSource dataSource) {
    // runDate를 WHERE 절에 활용
}
```

---

## 5. 멱등성 / 재시작 설계 원칙

- 같은 `JobParameters`로 재실행됐을 때 **이미 처리된 건은 다시 쓰지 않도록** 설계한다
  - 출력 테이블에 UNIQUE 제약을 두거나, `INSERT ... ON DUPLICATE KEY UPDATE` 사용
  - 또는 Reader 쿼리에서 이미 처리된 데이터 제외
- Reader/Processor/Writer 내부에 **인스턴스 필드로 상태를 축적하지 않는다**
  - 멀티스레드 Step으로 확장할 때 버그가 터지는 주범
- 진행 상태를 저장해야 하면 `ExecutionContext`를 사용한다 (Spring Batch가 재시작 시 복원해준다)

---

## 6. 예외 처리

- 배치 예외 전략은 크게 세 가지: **Skip / Retry / Fail**
- 비즈니스 규칙 위반(데이터 불량 등)은 `skip()` 대상으로
- 일시적 예외(네트워크, DB 락 등)는 `retry()` 대상으로
- 그 외 복구 불가능한 예외는 던져서 Job을 **FAILED**로 종료시키고, 다음 재실행에서 이어가게 한다
- `SkipListener`, `RetryListener`로 발생 사실을 반드시 로그 또는 별도 테이블에 남긴다

```java
.faultTolerant()
.skip(DataIntegrityViolationException.class)
.skipLimit(10)
.retry(TransientDataAccessException.class)
.retryLimit(3)
.listener(skipListener)
```

---

## 7. 로깅

- `@Slf4j` (Lombok) 사용
- 로그 레벨 기준:
  - `DEBUG`: 아이템 단위 상세 처리 내역
  - `INFO`: Job/Step 시작·종료, 주요 집계 결과
  - `WARN`: Skip 발생, 예상 가능한 예외 상황
  - `ERROR`: Job 실패, 복구 불가 예외
- Job 시작/종료 로그는 **JobExecutionListener**에 통일해두면 로그 포맷을 일관되게 유지할 수 있다
- 민감 정보(주민번호, 카드번호, 비밀번호 등)는 절대 로그에 남기지 않는다

---

## 8. 코드 스타일

- 들여쓰기: 4칸 스페이스
- 최대 줄 길이: 120자
- 중괄호: K&R 스타일 (같은 줄에 열기)
- `var` 키워드: 타입이 명확할 때만 사용
- 불필요한 주석 지양, 코드 자체로 의도 표현
- Chunk 크기, 페이지 사이즈 같은 수치는 **매직 넘버 대신 상수**로 정의

---

## 9. 의존성 주입

- 생성자 주입 방식 우선
- Lombok `@RequiredArgsConstructor` 활용
- `JobRepository`, `PlatformTransactionManager`는 **Spring Boot가 자동 구성해주므로** 직접 @Bean 정의하지 않는다

```java
@Configuration
@RequiredArgsConstructor
public class SettlementJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    // ...
}
```

---

## 10. application.yml 기본 설정

```yaml
spring:
  batch:
    job:
      enabled: false              # CLI에서 --spring.batch.job.name 으로 명시 실행
    jdbc:
      initialize-schema: always   # 학습 단계에서만. 운영은 never
  datasource:
    url: jdbc:mysql://localhost:3306/spring_batch?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop       # 학습용

logging:
  level:
    org.springframework.batch: INFO
    org.hibernate.SQL: DEBUG
```
