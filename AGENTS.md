# Codex 작업 가이드

이 저장소에서는 `.claude/` 문서를 원본 규칙으로 사용합니다. Codex는 아래 지침을 기준으로 프로젝트 전체에서 작업합니다.

## 기준 문서

- `.claude/workflow.md`: 개발 흐름, 학습 단계, 커밋 규칙, 작업 전 체크리스트
- `.claude/code_convention.md`: 구현 규칙과 코드 스타일
- `.claude/test_convention.md`: 테스트 전략과 네이밍 규칙
- `.claude/skills.md`: Spring Batch, Job/Step, Reader/Processor/Writer 핵심 개념

## 프로젝트 맥락

- 기술 스택: Java 21, Spring Boot 4.0.x, Spring Batch (Boot 번들 버전), Spring Data JPA
- 이 프로젝트는 WebFlux/Netty 기반이 아니라 MVC/Tomcat 없이 순수 배치 애플리케이션으로 동작한다
- 배치 Job 내부에서는 동기/블로킹 I/O가 자연스러우며, 오히려 트랜잭션 경계와 청크(chunk) 단위 처리가 핵심이다
- 학습과 가독성을 위해 Job/Step 정의는 Java Config(Builder) 방식을 우선한다 (yml 설정보다 타입 안전)
- 메타데이터 테이블(BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION 등)을 직접 조회하며 동작을 이해하는 것을 우선한다

## 개발 흐름

1. 현재 학습 단계와 요구사항을 먼저 파악한다.
2. 가능하면 테스트를 먼저 작성한다.
3. `job`, `step`, `reader`, `processor`, `writer`, `listener`, `config` 패키지 중심으로 구현한다.
4. 자동화 테스트를 먼저 검증하고, 필요하면 CLI 수동 실행으로 확인을 이어서 한다.
5. 최종 코드는 아래 규칙과 일관되게 정리한다.

## 역할 분담

- Codex는 기본 구현 담당이다
- Codex는 요구사항 분석, 테스트 초안 작성, 기능 구현, 리팩터링, 실행 가능한 코드 완성을 우선한다
- Claude는 코드 품질 향상 담당이다
- Claude는 리뷰, 엣지 케이스 점검, 가독성 개선, 설계 피드백, 누락 테스트 제안에 집중한다
- Codex가 작업할 때는 Claude가 이후 품질 점검을 수행할 것을 고려해 변경 의도와 구조를 분명하게 유지한다
- 리뷰를 염두에 두고 불필요하게 복잡한 추상화보다 읽기 쉬운 구현을 우선한다

## Codex 작업 원칙

- 먼저 동작하는 최소 구현과 테스트를 만든 뒤, 필요한 범위에서 정리한다
- 사용자가 별도로 요청하지 않아도 테스트 가능한 형태의 결과물을 우선 만든다
- 구현 중 구조 개선이 필요하면 과도한 확장보다 현재 학습 단계에 맞는 단순한 설계를 선택한다
- 후속 리뷰어가 이해하기 쉽도록 네이밍, 책임 분리, 예외 흐름을 명확히 유지한다

## Claude 리뷰 원칙

- Claude의 피드백을 반영할 때는 버그 가능성, 트랜잭션/청크 경계 오용, 재시작 시 멱등성 위반, 테스트 누락을 우선 확인한다
- 스타일 의견보다 동작 안정성, 유지보수성, 경계값 검증을 먼저 반영한다
- Codex가 만든 구조를 무조건 뒤집기보다, 현재 방향을 살리면서 품질을 높이는 수정을 우선한다

## 기본 학습 순서

사용자가 다른 방향을 명시하지 않으면 다음 순서를 우선한다.

1. Job & Step 기초 (Tasklet, Chunk)
2. ItemReader / ItemWriter
3. ItemProcessor
4. Flow 제어 & Listener
5. 예외 처리 & 재시작 (Skip / Retry / Restart)
6. 성능 & 확장 (Multi-thread / Partition)
7. 운영 & 스케줄링

각 단계에서는 `spring-batch-test` + Testcontainers(MySQL) 기반 통합 테스트를 함께 추가하는 것을 기본 원칙으로 한다.

## 전역 규칙

- 배치 Job은 항상 **멱등성**을 고려해 설계한다 (같은 JobParameters로 재실행해도 데이터가 깨지지 않아야 함)
- Chunk 크기, commit-interval, fetch size는 의미 없이 큰 숫자가 아니라 측정 후 조정한다
- Reader/Processor/Writer 내부에서 **상태를 field에 저장하지 않는다** (멀티스레드 확장 시 문제)
- 민감 정보를 로그에 남기지 않고, ExecutionContext에도 저장하지 않는다
- `application.properties` 등에 비밀값을 하드코딩하지 않는다
- 작업 마무리 전 디버그 코드와 불필요한 주석은 제거한다
- 이 프로젝트는 Spring Batch 학습용 프로젝트이므로, 처음 읽는 사람이 동작 흐름과 개념을 이해할 수 있도록 필요한 곳에 자세한 설명 주석을 작성한다
- 주석은 기본적으로 한국어로 작성한다
- 주석에는 "무엇을 하는지"만 반복하지 말고, 왜 이 설정이 필요한지, Spring Batch의 어떤 개념과 연결되는지, 테스트에서 무엇을 검증하는지를 설명한다
- Job, Step, Tasklet, Chunk, Reader, Processor, Writer, Listener, JobParameters, ExecutionContext, Batch 메타데이터 테이블처럼 학습 핵심 개념이 처음 등장하는 코드에는 교육용 주석을 우선 작성한다
- 단순 getter/setter, 변수 대입, 메서드명만 봐도 자명한 코드에는 형식적인 주석을 붙이지 않는다
- 줄 길이는 가능하면 120자 이내로 유지하고 들여쓰기는 4칸 스페이스를 사용한다
- 중괄호는 K&R 스타일을 사용한다
- 의존성 주입은 생성자 주입을 우선하고 Lombok `@RequiredArgsConstructor`를 선호한다

## 네이밍 규칙

- 설정 클래스: `XxxJobConfig`, `XxxStepConfig`, `XxxBatchConfig`
- Reader: `XxxItemReader`
- Processor: `XxxItemProcessor`
- Writer: `XxxItemWriter`
- Tasklet: `XxxTasklet`
- Listener: `XxxJobListener`, `XxxStepListener`, `XxxSkipListener`
- Decider: `XxxDecider`
- Partitioner: `XxxPartitioner`
- 메서드와 변수: `camelCase`
- 상수: `private static final UPPER_SNAKE_CASE`
- Job / Step Bean 이름: `xxxJob`, `xxxStep` (소문자 카멜, 타 Job과 충돌 없도록 구체적으로)

## 패키지 역할

`src/main` 기준으로 아래 구조를 기본으로 삼는다.

- `config/`: DataSource, Batch 공통 설정
- `job/`: Job 정의 (Job 단위로 패키지 분리 가능, 예: `job/settlement/`)
- `step/`: Step 정의, Tasklet 구현
- `reader/`: 커스텀 ItemReader
- `processor/`: ItemProcessor 구현
- `writer/`: 커스텀 ItemWriter
- `listener/`: Job/Step/Skip/Retry Listener
- `domain/`: Entity, DTO
- `repository/`: Spring Data Repository (필요 시)

## 테스트 원칙

- 통합 테스트는 `@SpringBatchTest` + `JobLauncherTestUtils`를 우선 사용한다
- DB는 Testcontainers(MySQL)로 실제 DB에 가까운 환경에서 검증한다
- 단위 테스트는 Reader/Processor/Writer를 각각 격리해 POJO 수준으로 검증한다
- Job 전체 실행 결과뿐 아니라 **ExitStatus, 읽은 건수, Skip/Retry 횟수**까지 함께 검증한다
- 재시작 시나리오(첫 실행 실패 → 두 번째 실행에서 이어서 처리)는 단계별로 반드시 포함한다

## 커밋 메시지 규칙

커밋 메시지가 필요하면 아래 형식을 사용한다.

`<type>: <subject>`

사용 가능한 타입:

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`
- `study`

## 디렉터리별 추가 규칙

- `src/` 하위 작업은 `src/AGENTS.md`를 따른다
- `src/test/` 하위 작업은 `src/test/AGENTS.md`를 따른다
