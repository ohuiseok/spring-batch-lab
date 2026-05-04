# Spring Batch 학습 프로젝트

Spring Batch의 핵심 개념을 단계별로 직접 구현하며 익히는 학습용 프로젝트입니다.
단순히 설정 파일을 따라 치는 것이 아니라, **`spring-batch-test` + Testcontainers 기반 테스트를 먼저 작성하고 구현**하는 흐름(TDD)으로 진행합니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.x |
| Batch | Spring Batch (Boot 번들 버전) |
| ORM | Spring Data JPA / JDBC |
| Build | Gradle |
| Local DB | MySQL (로컬 설치 DB, 메타데이터 확인용) |
| Test DB | Testcontainers + MySQL |
| Test | JUnit 5, `spring-batch-test`, AssertJ |
| Utility | Lombok |

> Spring Batch는 WebFlux가 아닌 전통적인 동기 블로킹 모델 위에서 동작합니다.
> 각 Step은 트랜잭션 단위로 묶이며, Chunk 지향 처리에서는 **read → process → write**가 하나의 트랜잭션으로 커밋됩니다.

---

## 프로젝트 구조

```
src
├── main/java/com/example/batch
│   ├── config/           # DataSource, Batch 공통 설정
│   ├── job/              # Job 정의 (도메인별로 하위 패키지 구성)
│   │   └── settlement/   # 예: 정산 배치
│   ├── step/             # Step / Tasklet 구현
│   ├── reader/           # 커스텀 ItemReader
│   ├── processor/        # ItemProcessor 구현
│   ├── writer/           # 커스텀 ItemWriter
│   ├── listener/         # Job/Step/Skip/Retry Listener
│   ├── domain/           # Entity, DTO
│   ├── repository/       # Spring Data Repository
│   └── BatchApplication.java
└── test/java/com/example/batch
    ├── job/              # Job 단위 통합 테스트
    ├── step/             # Step 단위 테스트
    ├── reader/           # Reader POJO 테스트
    ├── processor/        # Processor POJO 테스트
    ├── writer/           # Writer 단위 테스트
    └── support/          # TestContainers, 테스트 공통 설정
```

---

## 실행 방법

```bash
# 특정 Job 실행 (JobParameters 포함)
./gradlew bootRun --args='--spring.batch.job.name=settlementJob \
  --run.date=2026-04-24'

# 전체 빌드
./gradlew build

# 테스트만 실행
./gradlew test

# 특정 단계 테스트만 실행
./gradlew test --tests "com.example.batch.job.*"
```

> Spring Batch 5/6에서는 `spring.batch.job.enabled=false`로 자동 실행을 끄고,
> `--spring.batch.job.name=<jobBeanName>`으로 특정 Job만 선택 실행하는 패턴을 권장합니다.

---

## 학습 계획 및 진행 체크

### 보강 문서

학습 중에는 아래 문서를 함께 참고합니다.

- [메타데이터 테이블 관찰 가이드](docs/metadata-tables.md): `BATCH_*` 테이블별 역할과 확인 SQL
- [챕터별 실습 체크리스트](docs/chapter-checklist.md): 각 단계의 구현/테스트/관찰 완료 기준
- [트러블슈팅](docs/troubleshooting.md): restart, JobParameters, Testcontainers 등 자주 막히는 지점

### 선수 지식 — Spring Batch 기초 개념

본격 구현 전에 아래 개념을 먼저 이해해두면 이후 단계가 훨씬 수월합니다.

| 번호 | 학습 항목 | 완료 |
|------|----------|:----:|
| 0-1 | Job / JobInstance / JobExecution / StepExecution 관계 이해 | ☐ |
| 0-2 | Tasklet 방식 vs Chunk 지향 처리 차이 | ☐ |
| 0-3 | JobRepository / JobLauncher / JobOperator 역할 | ☐ |
| 0-4 | 메타데이터 테이블 9종(`BATCH_JOB_INSTANCE` 등) 직접 SELECT 해보기 | ☐ |
| 0-5 | JobParameters 와 identifying parameter 개념 | ☐ |

---

### 1단계 — Job & Step 기초

Spring Batch의 가장 기본이 되는 Job / Step / Tasklet / Chunk 골격을 잡습니다.
여기서는 실제 비즈니스 로직보다 **메타데이터 테이블이 어떻게 쌓이는지**를 관찰하는 것이 핵심입니다.

| 챕터 | 구현 내용 | 핵심 개념 | 완료 |
|------|----------|----------|:----:|
| 1-1 | "Hello, Batch!"를 출력하는 Tasklet 기반 단일 Step Job | `Tasklet`, `StepBuilder` | ☑ |
| 1-2 | 메모리 리스트(`ListItemReader`)를 읽어 로그로 쓰는 Chunk Job | chunk 지향 처리 | ☑ |
| 1-3 | `JobParameters`로 실행 날짜(`run.date`)를 받아 로그에 반영 | `JobParameters`, identifying | ☑ |

**브랜치**: `step1/job-basics`

---

### 2단계 — ItemReader / ItemWriter

실제 데이터를 읽고 쓰는 주력 Reader/Writer를 단계적으로 다룹니다.
`Cursor` vs `Paging`의 차이, 트랜잭션과의 관계를 반드시 체감하세요.

| 챕터 | 구현 내용 | 핵심 개념 | 완료 |
|------|----------|----------|:----:|
| 2-1 | `FlatFileItemReader`로 CSV 읽어 로그 출력 | `LineMapper`, `DefaultLineMapper` | ☑ |
| 2-2 | `JdbcPagingItemReader`로 DB 테이블 읽기 (페이지 사이즈 = 청크 크기) | Paging vs Cursor | ☐ |
| 2-3 | `JpaPagingItemReader`로 JPA Entity 읽기, `clear()` 이슈 체감 | EntityManager, 영속성 컨텍스트 | ☐ |
| 2-4 | `JdbcBatchItemWriter`로 대량 INSERT 수행 | `batchUpdate`, 성능 측정 | ☐ |

**브랜치**: `step2/reader-writer`

---

### 3단계 — ItemProcessor

Reader와 Writer 사이에서 데이터를 변환하고 걸러내는 Processor를 다룹니다.
`null` 반환의 의미(해당 아이템을 쓰지 않음)와 Processor 체이닝을 익히세요.

| 챕터 | 구현 내용 | 핵심 개념 | 완료 |
|------|----------|----------|:----:|
| 3-1 | 입력 Entity → 출력 DTO로 변환하는 Processor 구현 | `ItemProcessor<I, O>` | ☐ |
| 3-2 | 조건에 맞지 않는 아이템은 `null` 반환해 필터링 | 필터링 시 메타데이터 `filter_count` 확인 | ☐ |
| 3-3 | `CompositeItemProcessor`로 Processor 2개 체이닝 | 단일 책임 분리 | ☐ |

**브랜치**: `step3/processor`

---

### 4단계 — Flow 제어 & Listener

여러 Step을 조건에 따라 흐르게 하고, Listener로 로깅/집계를 붙입니다.
이 단계부터 "배치답다"는 느낌이 많이 붙습니다.

| 챕터 | 구현 내용 | 핵심 개념 | 완료 |
|------|----------|----------|:----:|
| 4-1 | Step A → Step B 로 이어지는 다중 Step Job 구성 | `.next()` | ☐ |
| 4-2 | Step A 결과에 따라 분기(`on("FAILED").to(stepC)`) | Flow API, ExitStatus | ☐ |
| 4-3 | `JobExecutionDecider`로 외부 조건에 따라 분기 | Decider 패턴 | ☐ |
| 4-4 | `JobExecutionListener` / `StepExecutionListener`로 실행 전후 로그·집계 | Listener 종류 | ☐ |

**브랜치**: `step4/flow-listener`

---

### 5단계 — 예외 처리 & 재시작

Spring Batch의 진짜 강점인 **안전한 재실행**을 학습합니다.
일부러 중간에 예외를 던지고 재실행하며 어디서부터 다시 시작되는지 관찰하세요.

| 챕터 | 구현 내용 | 핵심 개념 | 완료 |
|------|----------|----------|:----:|
| 5-1 | 특정 예외를 `skip()`으로 건너뛰기 (`skipLimit` 설정) | Skip 정책, `SkipListener` | ☐ |
| 5-2 | 일시적 예외를 `retry()`로 재시도 | Retry 정책, Backoff | ☐ |
| 5-3 | 실패한 Job을 같은 JobParameters로 재실행 → 실패 지점부터 이어서 처리 | Restart 동작, `allowStartIfComplete` | ☐ |
| 5-4 | `ExecutionContext`에 진행 상태 저장 → Restart 시 복구 | `ExecutionContext`, `ItemStream` | ☐ |

**브랜치**: `step5/skip-retry-restart`

---

### 6단계 — 성능 & 확장

단일 스레드 Chunk 처리의 한계를 벗어나 병렬/파티셔닝으로 확장합니다.
**먼저 단일 스레드 처리 시간을 측정**한 뒤 개선 효과를 수치로 비교하는 게 중요합니다.

| 챕터 | 구현 내용 | 핵심 개념 | 완료 |
|------|----------|----------|:----:|
| 6-1 | `TaskExecutor`로 Multi-threaded Step 구성, 동시성 주의사항 확인 | thread-safe Reader 필요 | ☐ |
| 6-2 | `Flow.split()`으로 독립된 Step 두 개를 병렬 실행 | Parallel Steps | ☐ |
| 6-3 | `Partitioner`로 ID 범위를 나눠 로컬 파티셔닝 처리 | Master/Worker, `gridSize` | ☐ |

**브랜치**: `step6/performance`

---

### 7단계 — 운영 & 스케줄링

실제 운영에서 필요한 스케줄링, 중복 실행 방지, 모니터링을 붙입니다.

| 챕터 | 구현 내용 | 핵심 개념 | 완료 |
|------|----------|----------|:----:|
| 7-1 | `@Scheduled` 또는 CLI에서 Job 주기 실행, 중복 실행 방지 | `JobOperator`, `RunIdIncrementer` | ☐ |
| 7-2 | Spring Boot Actuator + Micrometer로 Job 메트릭 수집 | `spring.batch.job.*` 메트릭 | ☐ |

**브랜치**: `step7/ops`

---

## 전체 진행 현황

| 단계 | 챕터 수 | 완료 수 | 진행률 |
|------|:-------:|:-------:|:------:|
| 0 (기초 개념) | 5 | 0 | 0% |
| 1 (Job & Step 기초) | 3 | 3 | 100% |
| 2 (Reader/Writer) | 4 | 1 | 25% |
| 3 (Processor) | 3 | 0 | 0% |
| 4 (Flow & Listener) | 4 | 0 | 0% |
| 5 (Skip/Retry/Restart) | 4 | 0 | 0% |
| 6 (성능 & 확장) | 3 | 0 | 0% |
| 7 (운영 & 스케줄링) | 2 | 0 | 0% |
| **합계** | **28** | **4** | **14%** |

---

## 테스트 전략

각 챕터는 아래 두 가지 테스트를 함께 작성합니다.

**통합 테스트** (`@SpringBatchTest` + `@SpringBootTest` + Testcontainers)
- Testcontainers로 실제 MySQL 띄움
- `JobLauncherTestUtils.launchJob(jobParameters)`로 Job 전체 실행
- `JobExecution.getExitStatus()`, `StepExecution.getWriteCount()` 등을 검증
- DB 결과 테이블을 직접 SELECT하여 비교

**단위 테스트** (POJO)
- Processor / 커스텀 Reader / Writer를 외부 의존성 없이 객체 단위로 검증
- 빠른 피드백을 위한 1차 방어선

```
[Test] --JobLauncherTestUtils--> [Job/Step] --read/write--> [Testcontainers MySQL]
```

### JobParameters 전략

`JobParameters`는 Spring Batch 학습에서 가장 헷갈리기 쉬운 부분이므로 테스트 목적에 따라 다르게 사용합니다.

| 목적 | 파라미터 전략 |
|------|---------------|
| 일반 성공 테스트 | `timestamp` 같은 고유 파라미터로 매번 새 JobInstance 생성 가능 |
| restart 테스트 | `timestamp` 금지, 같은 identifying JobParameters로 실패 후 재실행 |
| 멱등성 테스트 | 같은 비즈니스 파라미터로 반복 실행해 결과 데이터 중복 여부 확인 |

특히 restart 테스트에서는 첫 실행이 `FAILED`, 두 번째 실행이 `COMPLETED`가 되어야 하며,
`BATCH_JOB_INSTANCE`는 같은 row를 유지하고 `BATCH_JOB_EXECUTION`만 새로 추가되는지 확인합니다.

---

## 커밋 컨벤션

```
<type>: <subject>
```

| type | 의미 |
|------|------|
| `feat` | 새로운 기능 구현 |
| `test` | 테스트 코드 작성/수정 |
| `fix` | 버그 수정 |
| `refactor` | 리팩터링 |
| `docs` | 문서 수정 |
| `chore` | 빌드, 설정 변경 |
| `study` | 학습/실험용 코드 |
