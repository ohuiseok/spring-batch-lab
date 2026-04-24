# src/test 작업 가이드

`src/test` 하위 작업은 루트 `AGENTS.md`, `.claude/test_convention.md`, 이 문서를 함께 따른다.

## 테스트 우선순위

- Job 전체 동작은 `@SpringBatchTest` + `JobLauncherTestUtils`로 검증한다.
- DB가 필요한 통합 테스트는 Testcontainers MySQL을 우선한다.
- Processor처럼 순수 로직은 Spring Context 없이 POJO 단위 테스트를 먼저 작성한다.
- Reader/Writer는 외부 의존성에 따라 POJO 테스트 또는 DB 통합 테스트로 나눈다.

## 필수 검증 항목

- `ExitStatus`
- `readCount`
- `writeCount`
- `filterCount`
- `skipCount`
- 필요 시 `commitCount`, `rollbackCount`
- 결과 테이블 직접 SELECT

## JobParameters 전략

- 일반 성공 테스트는 테스트 독립성을 위해 `timestamp` 같은 고유 파라미터를 사용할 수 있다.
- restart 테스트에서는 `timestamp`나 `RunIdIncrementer`로 새 JobInstance를 만들지 않는다.
- restart 테스트는 같은 identifying JobParameters로 첫 실행 `FAILED`, 두 번째 실행 `COMPLETED`를 검증한다.
- 멱등성 테스트는 결과 테이블에 중복 데이터가 생기지 않는지 직접 확인한다.

## 테스트 데이터 정리

- Batch 메타데이터는 `JobRepositoryTestUtils.removeJobExecutions()`로 정리한다.
- 도메인 테이블은 테스트에서 직접 정리한다.
- restart 테스트는 실패 기록이 필요하므로 메타데이터 정리 시점을 신중히 둔다.

## 네이밍

- Job 통합 테스트: `XxxJobIntegrationTest`
- Step 통합 테스트: `XxxStepIntegrationTest`
- 단위 테스트: `XxxTest`
- 테스트 메서드: `대상_시나리오_기대결과` 또는 `@DisplayName` 한글 설명
