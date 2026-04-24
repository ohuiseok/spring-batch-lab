# Spring Batch 메타데이터 테이블 관찰 가이드

Spring Batch를 제대로 이해하려면 Job 실행 결과를 로그로만 보지 말고 메타데이터 테이블을 직접 확인해야 한다.
이 문서는 각 테이블에 어떤 값이 쌓이고, 학습 단계에서 무엇을 관찰해야 하는지 정리한다.

## 핵심 흐름

```text
Job
  -> JobInstance
      -> JobExecution
          -> StepExecution
```

- `Job`: 배치 작업의 정의다. 예: `settlementJob`
- `JobInstance`: Job 이름과 identifying JobParameters 조합으로 결정되는 논리적 실행 단위다.
- `JobExecution`: 실제 한 번의 실행 기록이다. 같은 JobInstance도 실패 후 재실행하면 여러 JobExecution을 가질 수 있다.
- `StepExecution`: 각 Step의 실행 기록이다. read/write/skip/filter/commit/rollback count가 여기에 남는다.

## BATCH_JOB_INSTANCE

| 컬럼 | 의미 | 관찰 포인트 |
|------|------|-------------|
| `JOB_INSTANCE_ID` | JobInstance 식별자 | 같은 identifying JobParameters면 같은 값이 재사용된다 |
| `JOB_NAME` | Job Bean 이름 | `spring.batch.job.name`과 맞는지 확인한다 |
| `JOB_KEY` | JobParameters 기반 해시 | identifying parameter가 바뀌면 달라진다 |

확인 질문:

- 같은 `run.date`로 두 번 실행하면 `JOB_INSTANCE_ID`가 같은가?
- `timestamp`를 identifying parameter로 추가하면 매번 새 JobInstance가 생기는가?
- 이미 `COMPLETED`된 JobInstance를 같은 파라미터로 다시 실행하면 어떤 예외가 나는가?

## BATCH_JOB_EXECUTION

| 컬럼 | 의미 | 관찰 포인트 |
|------|------|-------------|
| `JOB_EXECUTION_ID` | 실제 실행 1회의 ID | 실패 후 재시작하면 새 row가 생긴다 |
| `JOB_INSTANCE_ID` | 연결된 JobInstance | restart에서는 같은 JobInstance를 가리킨다 |
| `STATUS` | BatchStatus | `STARTED`, `COMPLETED`, `FAILED` 등 |
| `EXIT_CODE` | ExitStatus 코드 | Flow 분기와 운영 판단에 중요하다 |
| `START_TIME`, `END_TIME` | 실행 시작/종료 시각 | 성능 측정의 기본값이다 |

확인 질문:

- 첫 실행 실패 후 같은 JobParameters로 재실행하면 JobExecution row가 몇 개가 되는가?
- `STATUS`와 `EXIT_CODE`가 항상 같은 의미인가?
- 실패한 Job의 `END_TIME`과 `EXIT_MESSAGE`에는 무엇이 남는가?

## BATCH_JOB_EXECUTION_PARAMS

| 컬럼 | 의미 | 관찰 포인트 |
|------|------|-------------|
| `PARAMETER_NAME` | 파라미터 이름 | `run.date`, `mode` 등 |
| `PARAMETER_VALUE` | 파라미터 값 | 문자열 날짜 포맷이 의도와 맞는지 확인 |
| `IDENTIFYING` | JobInstance 식별 여부 | restart 학습에서 가장 중요하다 |

확인 질문:

- `run.date`가 identifying parameter로 들어갔는가?
- 매 테스트마다 넣은 `timestamp`가 identifying이면 어떤 일이 생기는가?
- restart 테스트에서 identifying parameter를 바꾸면 왜 이어서 처리되지 않는가?

## BATCH_STEP_EXECUTION

| 컬럼 | 의미 | 관찰 포인트 |
|------|------|-------------|
| `STEP_NAME` | Step 이름 | Bean 이름과 충돌 없이 구체적인지 확인 |
| `READ_COUNT` | Reader가 읽은 아이템 수 | 입력 건수와 비교한다 |
| `WRITE_COUNT` | Writer로 기록된 아이템 수 | 필터링/스킵 후 결과와 비교한다 |
| `FILTER_COUNT` | Processor가 `null`을 반환한 수 | 예외 skip과 구분한다 |
| `READ_SKIP_COUNT` | read 중 skip 수 | 파일 파싱/DB read 예외 확인 |
| `PROCESS_SKIP_COUNT` | process 중 skip 수 | 비즈니스 검증 예외 확인 |
| `WRITE_SKIP_COUNT` | write 중 skip 수 | DB 제약조건 위반 등 확인 |
| `COMMIT_COUNT` | commit 횟수 | chunk size와 입력 건수의 관계를 본다 |
| `ROLLBACK_COUNT` | rollback 횟수 | 예외, retry, skip 처리에서 증가 가능 |
| `EXIT_CODE` | Step ExitStatus | Flow 분기의 기준이 될 수 있다 |

확인 질문:

- chunk size가 2이고 입력이 5건이면 commit count는 어떻게 기록되는가?
- Processor에서 1건을 `null`로 반환하면 `FILTER_COUNT`와 `WRITE_COUNT`는 어떻게 달라지는가?
- skip 예외와 filter는 어떤 count에 각각 반영되는가?

## BATCH_JOB_EXECUTION_CONTEXT / BATCH_STEP_EXECUTION_CONTEXT

ExecutionContext는 재시작에 필요한 상태를 저장하는 공간이다.
저장 형태는 Spring Batch 버전과 serializer 설정에 따라 달라질 수 있으므로, 학습 문서에서는 "직렬화된 형태"로 이해한다.

관찰 포인트:

- `ItemStream`을 구현한 Reader/Writer가 마지막 처리 위치를 저장하는가?
- 실패 후 재시작할 때 이전 위치가 복원되는가?
- 민감 정보나 대용량 데이터가 저장되지 않는가?

## 기본 확인 SQL

```sql
SELECT * FROM BATCH_JOB_INSTANCE ORDER BY JOB_INSTANCE_ID DESC;
SELECT * FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC;
SELECT * FROM BATCH_JOB_EXECUTION_PARAMS ORDER BY JOB_EXECUTION_ID DESC;
SELECT * FROM BATCH_STEP_EXECUTION ORDER BY STEP_EXECUTION_ID DESC;
SELECT * FROM BATCH_JOB_EXECUTION_CONTEXT ORDER BY JOB_EXECUTION_ID DESC;
SELECT * FROM BATCH_STEP_EXECUTION_CONTEXT ORDER BY STEP_EXECUTION_ID DESC;
```

## 학습 단계별 관찰 목표

| 단계 | 반드시 볼 테이블 | 핵심 질문 |
|------|------------------|-----------|
| 1-1 Tasklet | `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION` | 단일 Step 실행 기록이 어떻게 쌓이는가 |
| 1-2 Chunk | `BATCH_STEP_EXECUTION` | read/write/commit count가 chunk size와 어떻게 연결되는가 |
| 1-3 JobParameters | `BATCH_JOB_EXECUTION_PARAMS` | identifying parameter가 JobInstance를 어떻게 결정하는가 |
| 3-2 Filtering | `BATCH_STEP_EXECUTION` | `FILTER_COUNT`와 `WRITE_COUNT`가 어떻게 달라지는가 |
| 5-1 Skip | `BATCH_STEP_EXECUTION` | read/process/write skip count 중 어디가 증가하는가 |
| 5-3 Restart | `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION_CONTEXT` | 실패 후 같은 JobInstance에서 새 JobExecution이 생기는가 |

