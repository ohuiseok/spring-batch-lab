# src 작업 가이드

`src/main` 하위 구현은 루트 `AGENTS.md`와 `.claude/` 문서를 따른다.
이 문서는 실제 코드 작성 시 우선 확인할 추가 규칙이다.

## 패키지 배치

- Job 정의는 `job/` 아래에 둔다.
- Job 규모가 커지면 `job/settlement/`처럼 Job 단위 하위 패키지로 묶는다.
- 공통 설정은 `config/`에 둔다.
- 도메인 로직은 가능하면 Reader/Processor/Writer/Listener로 분리한다.
- `@Configuration` 클래스에는 Job/Step 조립 책임만 둔다.

## Spring Batch 구현 규칙

- Spring Batch 5+ 스타일의 `JobBuilder`, `StepBuilder`를 직접 사용한다.
- `JobBuilderFactory`, `StepBuilderFactory`는 사용하지 않는다.
- Step의 chunk 설정에는 `PlatformTransactionManager`를 명시한다.
- JobParameters가 필요한 Bean은 `@StepScope` 또는 `@JobScope`를 사용한다.
- Reader/Processor/Writer 내부에 처리 상태를 인스턴스 필드로 누적하지 않는다.

## 주석 작성 규칙

- 이 프로젝트는 교육용 Spring Batch 프로젝트이므로, 코드에는 학습자가 개념과 실행 흐름을 이해할 수 있는 설명 주석을 작성한다.
- 주석은 기본적으로 한국어로 작성한다.
- Job, Step, Tasklet, Chunk, Reader, Processor, Writer, Listener, JobParameters, ExecutionContext, Batch 메타데이터 테이블을 다루는 코드는 왜 필요한지와 어떤 Batch 개념인지 설명한다.
- 테스트 코드에서는 어떤 실행 결과와 Batch 메타데이터를 검증하는지 주석으로 드러낸다.
- 단순 변수 대입이나 메서드명만으로 충분히 이해되는 코드에는 형식적인 주석을 붙이지 않는다.

## 멱등성

- 같은 비즈니스 JobParameters로 재실행해도 결과 데이터가 중복되지 않아야 한다.
- 출력 테이블에는 가능한 UNIQUE 제약 또는 upsert 전략을 둔다.
- 이미 처리한 데이터를 Reader 쿼리에서 제외하는 방식도 고려한다.

## 로깅

- Job/Step 시작과 종료는 Listener로 모은다.
- 아이템 단위 상세 로그는 `DEBUG`로 제한한다.
- 민감 정보는 로그와 ExecutionContext에 남기지 않는다.

## 네이밍

- Job Bean: `xxxJob`
- Step Bean: `xxxStep`
- Tasklet: `XxxTasklet`
- Reader: `XxxItemReader`
- Processor: `XxxItemProcessor`
- Writer: `XxxItemWriter`
- Listener: `XxxJobListener`, `XxxStepListener`, `XxxSkipListener`
