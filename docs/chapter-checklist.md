# 챕터별 실습 체크리스트

각 챕터는 "구현", "테스트", "메타데이터 관찰", "회고 질문"까지 끝나야 완료로 본다.
코드가 실행되는 것에서 멈추지 말고 Spring Batch가 내부적으로 무엇을 기록했는지 확인한다.

## 공통 완료 기준

- Job/Step 이름이 구체적이고 다른 챕터와 충돌하지 않는다.
- `@SpringBatchTest` 기반 통합 테스트가 있다.
- 필요한 경우 Reader/Processor/Writer 단위 테스트가 있다.
- `ExitStatus`와 `StepExecution` count를 함께 검증한다.
- 결과 테이블이 있다면 `JdbcTemplate` 또는 SQL로 직접 검증한다.
- 같은 JobParameters 재실행 시 동작을 의도적으로 확인한다.

## 0단계: 선수 지식

학습 목표:

- Job, JobInstance, JobExecution, StepExecution의 관계를 말로 설명할 수 있다.
- Tasklet과 Chunk 지향 처리의 차이를 설명할 수 있다.
- identifying JobParameters가 JobInstance를 결정한다는 점을 이해한다.

완료 기준:

- [ ] `docs/metadata-tables.md`를 읽고 핵심 테이블의 역할을 정리했다.
- [ ] `JobInstanceAlreadyCompleteException`이 언제 나는지 설명할 수 있다.
- [ ] restart와 새 JobInstance 실행의 차이를 설명할 수 있다.

## 1-1: Hello Tasklet Job

구현:

- `HelloTasklet`
- `HelloTaskletJobConfig`
- 단일 Step Job

테스트:

- `ExitStatus.COMPLETED`
- Step 수 1개
- `commitCount` 확인

메타데이터 관찰:

- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_STEP_EXECUTION`

회고 질문:

- Tasklet Step에서도 StepExecution이 생기는가?
- Tasklet은 chunk size를 가지는가?

## 1-2: ListItemReader Chunk Job

구현:

- `ListItemReader`로 5건 읽기
- chunk size 2
- `ItemWriter`에서 로그 또는 테스트용 테이블에 쓰기

테스트:

- `readCount = 5`
- `writeCount = 5`
- `filterCount = 0`
- `skipCount = 0`

메타데이터 관찰:

- `BATCH_STEP_EXECUTION.READ_COUNT`
- `BATCH_STEP_EXECUTION.WRITE_COUNT`
- `BATCH_STEP_EXECUTION.COMMIT_COUNT`

회고 질문:

- 마지막 chunk가 chunk size보다 작아도 commit되는가?
- chunk size를 1, 2, 3으로 바꾸면 commit count가 어떻게 달라지는가?

## 1-3: JobParameters

구현:

- `run.date` JobParameter를 받아 로그 또는 처리 결과에 반영
- `@StepScope`와 late binding 사용

테스트:

- `run.date`가 없으면 실패하거나 기본값 처리 정책을 검증
- 다른 `run.date`로 실행하면 다른 JobInstance가 생성되는지 확인

메타데이터 관찰:

- `BATCH_JOB_EXECUTION_PARAMS`
- `BATCH_JOB_INSTANCE.JOB_KEY`

회고 질문:

- identifying parameter와 non-identifying parameter의 차이는 무엇인가?
- `timestamp`를 넣으면 restart 학습에 어떤 영향을 주는가?

## 2단계: Reader / Writer

각 챕터 공통 확인:

- Reader가 읽은 원본 건수와 `readCount`가 일치한다.
- Writer가 쓴 결과와 `writeCount`가 일치한다.
- DB Writer는 결과 테이블을 직접 SELECT해서 검증한다.

추가 회고 질문:

- Cursor Reader와 Paging Reader는 커넥션 사용 방식이 어떻게 다른가?
- page size와 chunk size를 다르게 두면 어떤 일이 생길 수 있는가?
- JPA Reader에서 영속성 컨텍스트가 계속 커지면 어떤 문제가 생기는가?

## 3단계: Processor

각 챕터 공통 확인:

- Processor는 가능하면 Spring Context 없이 POJO 단위 테스트를 먼저 작성한다.
- `null` 반환은 filter이고, 예외는 skip/retry/fail 대상이라는 점을 구분한다.

필수 검증:

- 정상 변환
- 필터링 조건
- 경계값
- 잘못된 입력 처리 정책

회고 질문:

- `filterCount`와 `processSkipCount`는 언제 각각 증가하는가?
- Processor를 여러 개로 나누면 테스트가 어떻게 쉬워지는가?

## 4단계: Flow / Listener

각 챕터 공통 확인:

- Step 흐름이 의도대로 이어지는지 `StepExecution` 목록으로 확인한다.
- 조건부 분기는 `ExitStatus`를 명확히 검증한다.
- Listener는 본 로직을 침범하지 않고 로깅/집계 책임만 가진다.

회고 질문:

- `BatchStatus`와 `ExitStatus`는 어떻게 다른가?
- Listener에서 예외를 던지면 Job 결과에 어떤 영향을 주는가?

## 5단계: Skip / Retry / Restart

각 챕터 공통 확인:

- 성공 케이스뿐 아니라 실패 케이스를 먼저 만든다.
- skip limit 직전과 직후를 검증한다.
- retry 횟수를 검증 가능한 방식으로 만든다.
- restart 테스트에서는 같은 identifying JobParameters를 사용한다.

restart 테스트 주의:

- `timestamp`를 identifying parameter로 넣지 않는다.
- `RunIdIncrementer`로 매번 새 인스턴스를 만들지 않는다.
- 첫 실행은 `FAILED`, 두 번째 실행은 `COMPLETED`를 기대한다.
- 첫 실행에서 이미 commit된 chunk가 중복 처리되지 않는지 결과 테이블로 확인한다.

회고 질문:

- 실패한 chunk 전체가 rollback되는가?
- skip/retry가 켜지면 rollback count가 어떻게 변하는가?
- `ExecutionContext`에는 어떤 값이 저장되는가?

## 6단계: 성능 / 확장

각 챕터 공통 확인:

- 먼저 단일 스레드 기준 처리 시간을 측정한다.
- 병렬 처리 후 처리 시간과 결과 정합성을 모두 비교한다.
- Reader/Processor/Writer가 thread-safe한지 확인한다.

회고 질문:

- Multi-threaded Step에서 item 처리 순서는 보장되는가?
- `JdbcCursorItemReader`가 병렬 처리에 부적합한 이유는 무엇인가?
- Partitioning에서 각 worker가 같은 데이터를 읽지 않게 하려면 무엇이 필요한가?

## 7단계: 운영 / 스케줄링

각 챕터 공통 확인:

- 중복 실행 방지 정책을 명확히 둔다.
- 운영에서는 batch schema 자동 생성 정책을 `never`로 두는 것을 고려한다.
- 모니터링 지표와 로그가 Job/Step 단위로 추적 가능해야 한다.

회고 질문:

- 이미 실행 중인 Job을 다시 시작하려 하면 어떻게 막을 것인가?
- 운영에서 `RunIdIncrementer`를 무조건 쓰면 어떤 문제가 생길 수 있는가?

