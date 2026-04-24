# Spring Batch 학습 중 자주 막히는 지점

## 같은 Job을 다시 실행했더니 이미 완료됐다는 예외가 난다

대표 예외:

```text
JobInstanceAlreadyCompleteException
```

원인:

- 같은 Job 이름과 같은 identifying JobParameters로 이미 `COMPLETED`된 JobInstance를 다시 실행했다.

해결:

- 매번 새 실행을 원하는 테스트라면 `timestamp` 같은 고유 identifying parameter를 추가한다.
- restart를 배우는 테스트라면 고유 파라미터를 추가하지 않는다. 첫 실행이 `FAILED` 상태여야 같은 파라미터로 재시작할 수 있다.

## restart 테스트가 이어서 처리되지 않고 새로 시작한다

원인:

- `timestamp`, `run.id` 등 매번 달라지는 identifying parameter 때문에 새 JobInstance가 만들어졌다.
- 실패한 Step이 아니라 이미 완료된 새 JobInstance를 실행하고 있다.

해결:

- restart 테스트에서는 같은 `run.date` 같은 고정 identifying parameter만 사용한다.
- `BATCH_JOB_INSTANCE`에서 row가 새로 생기는지 확인한다. restart라면 같은 `JOB_INSTANCE_ID`에 새 `JOB_EXECUTION_ID`가 붙어야 한다.

## Processor에서 `null`을 반환했는데 skip count가 증가하지 않는다

정상 동작이다.

- `null` 반환은 필터링이다.
- `filterCount`가 증가한다.
- skip은 예외가 발생했고 fault tolerant 설정에 의해 건너뛴 경우에 증가한다.

확인:

```sql
SELECT READ_COUNT, WRITE_COUNT, FILTER_COUNT, PROCESS_SKIP_COUNT
FROM BATCH_STEP_EXECUTION
ORDER BY STEP_EXECUTION_ID DESC;
```

## Writer에 전달되는 타입이 List가 아니라 Chunk라서 컴파일이 안 된다

Spring Batch 5부터 `ItemWriter.write()` 시그니처가 바뀌었다.

```java
void write(Chunk<? extends T> chunk) throws Exception;
```

예전 자료의 `List<? extends T>` 예제를 그대로 복사하면 컴파일되지 않을 수 있다.

## JobBuilderFactory / StepBuilderFactory 예제가 deprecated로 보인다

Spring Batch 5 이후에는 아래 스타일을 사용한다.

```java
new JobBuilder("sampleJob", jobRepository)
new StepBuilder("sampleStep", jobRepository)
```

`StepBuilder`에서 chunk를 만들 때는 `PlatformTransactionManager`를 명시한다.

```java
.<Input, Output>chunk(100, transactionManager)
```

## JobParameters가 Reader에 주입되지 않는다

원인:

- `@StepScope` 또는 `@JobScope`가 빠져 있다.
- Bean 생성 시점이 Step 실행 시점보다 빨라 late binding이 동작하지 않는다.

해결:

```java
@Bean
@StepScope
public JdbcPagingItemReader<Order> orderReader(
        @Value("#{jobParameters['run.date']}") String runDate) {
    // ...
}
```

## 테스트 간 메타데이터가 섞인다

원인:

- 이전 테스트의 `BATCH_*` 메타데이터가 남아 있다.
- 비즈니스 테이블 데이터가 남아 있다.

해결:

- `JobRepositoryTestUtils.removeJobExecutions()`로 Batch 메타데이터를 정리한다.
- `orders`, `settlement` 같은 도메인 테이블은 테스트에서 직접 `DELETE`하거나 fixture 전략을 둔다.
- restart 테스트는 일부러 메타데이터를 유지해야 하므로 정리 시점을 조심한다.

## Testcontainers 테스트가 너무 느리다

학습 초반에는 정상이다. Batch 통합 테스트는 실제 DB 동작을 보는 것이 중요하다.

속도를 줄이는 방법:

- Processor 같은 순수 로직은 POJO 단위 테스트로 분리한다.
- 통합 테스트는 Job/Step 경계와 DB 결과 검증에 집중한다.
- 컨테이너를 클래스 단위 static으로 띄운다.

## 로컬 MySQL과 테스트 MySQL 동작이 다르다

가능한 원인:

- SQL 문법 차이
- 날짜/시간 타입 차이
- 예약어 사용
- 대소문자/식별자 quoting 차이
- transaction isolation 또는 locking 차이

원칙:

- 로컬 수동 실행은 설치된 MySQL로 확인한다.
- 자동화 통합 테스트는 Testcontainers MySQL을 우선한다.
- 로컬 MySQL 버전과 Testcontainers 이미지 버전이 크게 다르면 SQL 모드나 날짜 처리 차이가 날 수 있다.

## Multi-threaded Step에서 결과가 가끔 달라진다

원인:

- Reader/Processor/Writer 내부에 공유 mutable field가 있다.
- thread-safe하지 않은 Reader를 병렬 Step에서 사용했다.
- 처리 순서가 보장된다고 가정했다.

해결:

- Reader/Processor/Writer 내부에 처리 상태를 field로 누적하지 않는다.
- `JdbcPagingItemReader`처럼 병렬에 적합한 Reader를 선택한다.
- 필요하면 `SynchronizedItemStreamReader`를 검토한다.
- 결과 검증은 순서보다 집합/건수/합계 중심으로 한다.
