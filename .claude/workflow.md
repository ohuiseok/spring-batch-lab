# 개발 워크플로우 (Spring Batch 프로젝트)

## 목표
Spring Batch의 핵심 개념을 학습하고, 실제 동작하는 배치 애플리케이션을 직접 구현하며 익힌다.
특히 **재실행 안전성(멱등성)**, **청크 단위 트랜잭션**, **메타데이터 테이블 활용**을 몸으로 체득하는 것이 목표다.

---

## 1. 학습 순서

> 각 챕터마다 `spring-batch-test` + Testcontainers 기반 통합 테스트를 함께 작성한다.

### 1단계 — Job & Step 기초

| 챕터 | 내용 | 핵심 개념 |
|------|------|----------|
| 1-1 | Tasklet 기반 단일 Step Job으로 "Hello, Batch!" 출력 | `Tasklet` |
| 1-2 | 메모리 List를 읽어 로그로 쓰는 Chunk Job | Chunk 지향 처리 |
| 1-3 | `JobParameters`로 `run.date` 받아 처리에 반영 | `JobParameters` |

### 2단계 — ItemReader / ItemWriter

| 챕터 | 내용 | 핵심 개념 |
|------|------|----------|
| 2-1 | `FlatFileItemReader`로 CSV 읽기 | `LineMapper` |
| 2-2 | `JdbcPagingItemReader`로 DB 읽기 | Paging vs Cursor |
| 2-3 | `JpaPagingItemReader`로 Entity 읽기 | 영속성 컨텍스트 clear |
| 2-4 | `JdbcBatchItemWriter`로 대량 INSERT | `batchUpdate` |

### 3단계 — ItemProcessor

| 챕터 | 내용 | 핵심 개념 |
|------|------|----------|
| 3-1 | Entity → DTO 변환 Processor | `ItemProcessor<I, O>` |
| 3-2 | 조건 미충족 시 `null` 반환으로 필터링 | `filter_count` |
| 3-3 | `CompositeItemProcessor`로 체이닝 | 단일 책임 분리 |

### 4단계 — Flow 제어 & Listener

| 챕터 | 내용 | 핵심 개념 |
|------|------|----------|
| 4-1 | 다중 Step Job (`.next()`) | Step 간 흐름 |
| 4-2 | `on("FAILED").to(stepC)` 분기 | Flow API |
| 4-3 | `JobExecutionDecider` 기반 분기 | Decider |
| 4-4 | Job/Step Listener로 실행 전후 로깅 | Listener 종류 |

### 5단계 — 예외 처리 & 재시작

| 챕터 | 내용 | 핵심 개념 |
|------|------|----------|
| 5-1 | 특정 예외 `skip()` 처리 | Skip 정책, `SkipListener` |
| 5-2 | `retry()`로 일시 예외 재시도 | Retry, Backoff |
| 5-3 | 실패한 Job 재실행 → 이어서 처리 | Restart, identifying params |
| 5-4 | `ExecutionContext`에 진행 상태 저장 | `ItemStream` |

### 6단계 — 성능 & 확장

| 챕터 | 내용 | 핵심 개념 |
|------|------|----------|
| 6-1 | Multi-threaded Step (`TaskExecutor`) | thread-safe Reader |
| 6-2 | `Flow.split()`으로 Step 병렬 | Parallel Steps |
| 6-3 | `Partitioner`로 로컬 파티셔닝 | Master/Worker |

### 7단계 — 운영 & 스케줄링

| 챕터 | 내용 | 핵심 개념 |
|------|------|----------|
| 7-1 | `@Scheduled`/CLI 주기 실행, 중복 실행 방지 | `JobOperator`, `RunIdIncrementer` |
| 7-2 | Actuator + Micrometer Job 메트릭 | `spring.batch.job.*` |

> **선수 지식**: Job / JobInstance / JobExecution / StepExecution 관계, Tasklet 과 Chunk 지향 처리의 차이,
> 메타데이터 테이블 9종(`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION` 등)을
> 1단계 시작 전에 문서로 먼저 훑어두면 이후 흐름이 훨씬 자연스럽습니다.

---

## 2. 기능 개발 흐름

```
요구사항 파악 (어떤 데이터를, 어떤 주기로, 어떤 단위로 처리하는가)
    ↓
Job/Step 설계 (청크 크기, 트랜잭션 경계, 재시작 포인트 결정)
    ↓
테스트 코드 먼저 작성 (TDD 권장 — 정상 + 실패 + 재시작 시나리오)
    ↓
구현 (Config / Reader / Processor / Writer / Listener)
    ↓
로컬 실행 및 메타데이터 테이블 확인 (SELECT로 직접 검증)
    ↓
테스트 통과 확인
    ↓
재실행 시 멱등성 체크 (같은 JobParameters로 다시 돌렸을 때 데이터가 깨지지 않는가)
    ↓
코드 리뷰 및 정리
    ↓
커밋
```

### JobParameters 사용 기준

테스트 목적에 따라 JobParameters 전략을 분리한다.

- 일반 성공 테스트: 테스트 독립성을 위해 `timestamp` 같은 고유 파라미터를 사용할 수 있다.
- restart 테스트: `timestamp`, `run.id`처럼 매번 달라지는 identifying parameter를 사용하지 않는다.
- 멱등성 테스트: 같은 비즈니스 파라미터로 반복 실행했을 때 결과 데이터가 중복되지 않는지 확인한다.

restart를 관찰할 때는 `BATCH_JOB_INSTANCE`는 같은 row를 유지하고,
`BATCH_JOB_EXECUTION`에 새 실행 기록이 추가되는지 직접 확인한다.

자세한 관찰 기준은 `docs/metadata-tables.md`와 `docs/chapter-checklist.md`를 따른다.

---

## 3. 실행 방법

```bash
# 특정 Job 실행 (JobParameters 포함)
./gradlew bootRun --args='--spring.batch.job.name=settlementJob \
  --run.date=2026-04-24'

# 빌드
./gradlew build

# 전체 테스트 실행
./gradlew test

# 특정 패키지 테스트만 실행
./gradlew test --tests "com.example.batch.job.settlement.*"
```

`application.yml`에 아래 설정을 두면 애플리케이션 기동 시 자동으로 모든 Job이 실행되는 것을 막을 수 있습니다.

```yaml
spring:
  batch:
    job:
      enabled: false   # CLI에서 --spring.batch.job.name=xxx 로 명시적으로 실행
    jdbc:
      initialize-schema: always   # 학습 단계에서만, 운영에선 never 권장
```

---

## 4. 로컬 테스트 환경

- **로컬 DB**: MySQL (로컬 설치 DB, 메타데이터 테이블 확인 용도)
- **통합 테스트 DB**: Testcontainers + MySQL 8
- **Job 실행 유틸**: `JobLauncherTestUtils` (`spring-batch-test` 제공)
- **단위 테스트**: JUnit 5 + AssertJ + (선택적) Mockito

```bash
# 메타데이터 테이블 직접 확인 (MySQL CLI 예시)
mysql -u root -p spring_batch

# 예시 쿼리
SELECT * FROM BATCH_JOB_INSTANCE ORDER BY JOB_INSTANCE_ID DESC;
SELECT * FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC;
SELECT * FROM BATCH_STEP_EXECUTION ORDER BY STEP_EXECUTION_ID DESC;
```

---

## 5. 커밋 컨벤션

```
<type>: <subject>

[body - optional]
```

| type | 의미 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 리팩터링 |
| `test` | 테스트 코드 추가/수정 |
| `docs` | 문서 수정 |
| `chore` | 빌드, 설정 변경 |
| `study` | 학습 목적 실험 코드 |

예시:
```
feat: 일별 정산 Job Chunk 처리 구현

- JdbcPagingItemReader로 주문 테이블 페이지 단위 읽기
- SettlementProcessor에서 금액 집계 DTO 변환
- JdbcBatchItemWriter로 settlement 테이블 적재
- Skip: ArithmeticException 10건까지 허용
```

---

## 6. 브랜치 전략 (학습 프로젝트)

```
main                              # 안정적인 코드
  └── step1/job-basics            # 1단계: Job & Step 기초
  └── step2/reader-writer         # 2단계: Reader / Writer
  └── step3/processor             # 3단계: ItemProcessor
  └── step4/flow-listener         # 4단계: Flow & Listener
  └── step5/skip-retry-restart    # 5단계: 예외 처리 & 재시작
  └── step6/performance           # 6단계: 성능 & 확장
  └── step7/ops                   # 7단계: 운영 & 스케줄링
```

---

## 7. 체크리스트 (PR/커밋 전)

- [ ] Job / Step 단위 테스트 통과 확인 (`JobLauncherTestUtils`)
- [ ] 같은 JobParameters로 재실행했을 때 데이터가 중복되지 않는지 확인 (**멱등성**)
- [ ] 실패 시 재시작(restart) 테스트가 포함됐는지 확인
- [ ] Reader/Processor/Writer 내부에 공유 필드 상태가 없는지 확인
- [ ] `./gradlew build` 성공
- [ ] Chunk 크기/페이지 사이즈가 의미 있는 값인지 한 번 더 검토
- [ ] 로그에 민감 정보가 노출되지 않는지 확인
- [ ] `application.yml`에 비밀 정보가 하드코딩되지 않았는지 확인
- [ ] 디버그 코드·주석 정리
