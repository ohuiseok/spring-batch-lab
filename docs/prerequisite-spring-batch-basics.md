# 선수 지식 - Spring Batch 기초 개념

이 문서는 Spring Batch를 처음 배우기 전에 알아두면 좋은 기본 개념을 아주 쉽게 설명합니다.

Spring Batch는 처음 보면 `Job`, `Step`, `JobRepository`, `ExecutionContext`, `Chunk` 같은 낯선 단어가 많이 나옵니다. 하지만 핵심은 단순합니다.

> Spring Batch는 "많은 데이터를 안정적으로, 나누어서, 기록을 남기며 처리하는 도구"입니다.

웹 API처럼 사용자가 버튼을 누를 때마다 즉시 응답하는 프로그램이 아니라, 정해진 시점에 많은 데이터를 한 번에 처리하는 프로그램을 만들 때 사용합니다.

## 읽기 전에: 원래 헷갈리는 부분

Spring Batch를 처음 볼 때 어려운 부분은 보통 코드 문법이 아닙니다. 비슷한 이름의 개념들이 한꺼번에 나오기 때문에 헷갈립니다.

특히 아래 개념은 한 번에 이해되지 않는 것이 자연스럽습니다.

- `Job`, `JobInstance`, `JobExecution`: 이름은 비슷하지만 서로 다른 층위의 개념입니다.
- `JobParameters`: 단순 입력값이면서 동시에 JobInstance를 구분하는 기준입니다.
- `Chunk`: 반복 처리 단위이면서 트랜잭션 commit 단위와 연결됩니다.
- `ExecutionContext`: 그냥 Map처럼 보이지만 재시작을 위한 상태 저장소입니다.
- `filter`, `skip`, `retry`: 모두 "정상 처리되지 않은 데이터"처럼 보이지만 의미가 다릅니다.

따라서 이 문서는 어려운 단어가 나오면 바로 정의만 외우지 않고, 작은 주문 정산 예시로 다시 설명합니다. 처음 읽을 때는 완벽히 외우기보다 "이 용어가 어느 상황에서 필요한가?"를 잡는 것이 더 중요합니다.

---

## 1. 배치란 무엇인가?

배치(batch)는 여러 일을 모아서 한 번에 처리하는 방식입니다.

예를 들어 이런 작업들이 배치에 잘 어울립니다.

- 매일 새벽 2시에 어제 주문 내역을 정산한다.
- 매일 오전 9시에 휴면 회원을 찾아 알림 메일을 보낸다.
- 매달 1일에 구독 결제 대상자를 계산한다.
- 대량의 CSV 파일을 읽어서 DB에 저장한다.
- 오래된 로그 데이터를 다른 테이블로 옮긴다.

공통점은 다음과 같습니다.

- 처리할 데이터가 많다.
- 즉시 응답을 줄 필요가 없다.
- 중간에 실패할 수 있다.
- 실패하면 어디까지 처리했는지 알아야 한다.
- 다시 실행해도 데이터가 망가지면 안 된다.

웹 API는 보통 "한 요청에 대해 빠르게 응답"하는 것이 중요합니다. 반면 배치는 "많은 데이터를 끝까지 안정적으로 처리"하는 것이 중요합니다.

---

## 2. Spring Batch가 필요한 이유

직접 `for` 문을 돌려서 데이터를 처리할 수도 있습니다.

```java
for (Order order : orders) {
    Settlement settlement = calculate(order);
    settlementRepository.save(settlement);
}
```

작은 프로그램이라면 괜찮습니다. 하지만 데이터가 많아지면 질문이 생깁니다.

- 100만 건 중 60만 건까지 처리하다 실패하면 처음부터 다시 해야 할까?
- 이미 저장된 60만 건은 어떻게 구분할까?
- 몇 건을 읽었고, 몇 건을 저장했고, 몇 건이 실패했는지 어떻게 알까?
- 일부 데이터만 잘못됐을 때 전체 작업을 실패시켜야 할까?
- 실패한 작업을 같은 조건으로 다시 실행하면 이어서 처리할 수 있을까?
- 처리 중인 작업의 상태를 어디에 기록할까?

Spring Batch는 이런 문제를 풀기 위해 만들어진 프레임워크입니다.

Spring Batch는 단순히 반복문을 대신해 주는 도구가 아닙니다. 반복 처리뿐 아니라 실행 이력, 트랜잭션, 재시작, 실패 처리, 통계 정보까지 함께 관리합니다.

---

## 3. Spring Batch 전체 그림

Spring Batch의 가장 큰 구조는 `Job`과 `Step`입니다.

```text
Job
  Step 1
  Step 2
  Step 3
```

쉽게 말하면 다음과 같습니다.

- `Job`: 하나의 배치 작업 전체
- `Step`: Job을 이루는 하나의 단계

예를 들어 "일일 주문 정산 Job"이 있다고 해봅니다.

```text
일일 주문 정산 Job
  Step 1: 어제 주문 데이터를 읽는다.
  Step 2: 주문별 정산 금액을 계산한다.
  Step 3: 정산 결과를 DB에 저장한다.
```

실제 Spring Batch에서는 Step 하나가 읽기, 계산, 저장을 모두 포함할 수도 있고, 여러 Step으로 나누어 구성할 수도 있습니다.

처음에는 이렇게 기억하면 충분합니다.

> Job은 전체 작업이고, Step은 그 작업을 쪼갠 실행 단위입니다.

### 이 문서에서 계속 사용할 예시

이후 설명에서는 아래 주문 데이터를 계속 예시로 사용하겠습니다.

```text
주문 데이터 5건

1번 주문: 정상 주문, 금액 10,000원
2번 주문: 정상 주문, 금액 20,000원
3번 주문: 취소 주문
4번 주문: 정상 주문이지만 외부 API가 잠깐 실패할 수 있음
5번 주문: 금액 값이 잘못된 주문
```

이 데이터를 정산하는 Job을 만든다고 가정합니다.

```text
dailySettlementJob
  dailySettlementStep
    Reader: 주문 5건을 읽는다.
    Processor: 취소 주문은 제외하고, 정상 주문은 정산 데이터로 바꾼다.
    Writer: 정산 데이터를 DB에 저장한다.
```

처음에는 코드보다 이 그림을 먼저 떠올리면 좋습니다.

```text
주문 데이터 -> Reader -> Processor -> Writer -> 정산 결과 테이블
```

이 문서에 나오는 어려운 용어들은 대부분 이 흐름을 안정적으로 실행하기 위해 존재합니다.

---

## 4. Job

`Job`은 배치 작업 하나를 의미합니다.

예를 들어 다음은 모두 Job이 될 수 있습니다.

- `dailySettlementJob`
- `inactiveMemberNotificationJob`
- `monthlyBillingJob`
- `csvImportJob`

Job은 "무엇을 할 것인가"에 대한 큰 정의입니다. 실제 작업은 Job 안에 들어 있는 Step들이 수행합니다.

중요한 점은 Job이 단순한 메서드 호출이 아니라는 것입니다. Spring Batch는 Job을 실행할 때 실행 기록을 DB에 남깁니다.

예를 들어 `dailySettlementJob`을 실행하면 Spring Batch는 다음과 같은 정보를 기록합니다.

- 어떤 Job이 실행됐는가?
- 어떤 파라미터로 실행됐는가?
- 언제 시작했는가?
- 언제 끝났는가?
- 성공했는가, 실패했는가?
- 내부 Step들은 각각 몇 건을 읽고 썼는가?

이 기록 덕분에 배치 작업을 추적하고, 실패한 작업을 다시 실행할 수 있습니다.

---

## 5. Step

`Step`은 Job 안의 실제 실행 단계입니다.

Job이 책 한 권이라면, Step은 그 안의 장(chapter)이라고 볼 수 있습니다.

예를 들어 회원 휴면 처리 Job은 이렇게 나눌 수 있습니다.

```text
inactiveMemberJob
  findInactiveMembersStep
  updateInactiveStatusStep
  sendNotificationStep
```

Step을 나누는 이유는 책임을 분리하기 위해서입니다.

- 어떤 단계에서 실패했는지 알기 쉽다.
- 성공한 단계와 실패한 단계를 구분할 수 있다.
- 재시작할 때 이미 끝난 Step을 건너뛸 수 있다.
- 테스트하기 쉬워진다.

Step은 크게 두 가지 방식으로 만들 수 있습니다.

- Tasklet 방식
- Chunk 지향 처리 방식

---

## 6. Tasklet 방식

`Tasklet`은 "한 번 실행할 작업"을 표현할 때 사용합니다.

예를 들면 이런 작업에 어울립니다.

- 특정 파일을 삭제한다.
- 임시 테이블을 비운다.
- 간단한 SQL 한 번을 실행한다.
- 외부 API를 한 번 호출한다.
- 로그를 한 줄 남긴다.

Tasklet은 구조가 단순합니다.

```text
Step 시작
  Tasklet 실행
Step 종료
```

데이터를 한 건씩 읽고, 가공하고, 저장하는 반복 작업보다는 "이 작업을 한 번 수행하라"는 성격에 가깝습니다.

처음 배우는 단계에서는 `Hello, Batch!`를 출력하는 Job을 Tasklet으로 만들어보면 좋습니다. Spring Batch의 Job과 Step 실행 흐름을 가장 작게 경험할 수 있기 때문입니다.

### Tasklet은 스케줄러와 무엇이 다를까?

처음에는 이런 생각이 들 수 있습니다.

> "한 번 실행할 작업이면 그냥 `@Scheduled`로 메서드 하나 돌리면 되는 것 아닌가?"

맞습니다. 정말 단순히 "정해진 시간에 메서드 하나 실행"만 필요하다면 스케줄러만으로도 충분할 수 있습니다.

```java
@Scheduled(cron = "0 0 2 * * *")
public void deleteTempFiles() {
    tempFileService.deleteOldFiles();
}
```

하지만 스케줄러와 Tasklet은 역할이 다릅니다.

```text
스케줄러: 언제 실행할지 결정한다.
Tasklet: 배치 Job 안에서 무엇을 실행할지 정의한다.
```

즉, 스케줄러는 실행 시점을 담당하고, Tasklet은 Spring Batch 안의 Step으로 실행될 작업을 담당합니다.

예를 들어 매일 새벽 2시에 정산 Job을 실행한다고 해봅니다.

```text
@Scheduled
  -> dailySettlementJob 실행
       -> cleanupStep
       -> settlementStep
       -> reportStep
```

이때 스케줄러는 `dailySettlementJob`을 시작시키는 역할만 합니다. 실제 배치 내부의 단계는 Step들이 담당합니다.

Tasklet을 쓰면 단순 작업도 Spring Batch의 관리 안으로 들어옵니다.

- `BATCH_STEP_EXECUTION`에 실행 기록이 남는다.
- 성공/실패 상태를 Job 흐름 안에서 관리할 수 있다.
- 앞 Step이 실패하면 뒤 Step을 실행하지 않게 할 수 있다.
- 실패한 Step을 기준으로 재시작 흐름을 잡을 수 있다.
- 전체 Job의 일부로 테스트할 수 있다.
- Listener로 실행 시간, 실패 로그, 알림을 붙일 수 있다.

반대로 `@Scheduled` 메서드 안에서 직접 SQL이나 파일 삭제를 실행하면 Spring Batch 입장에서는 그 작업을 모릅니다.

```text
스케줄러 메서드에서 직접 실행
  -> Spring Batch 메타데이터에 Step 기록 없음
  -> Job 흐름과 연결 안 됨
  -> 어떤 단계에서 실패했는지 Batch 기준으로 추적하기 어려움
```

그래서 기준은 이렇게 잡으면 됩니다.

```text
단독으로 간단히 주기 실행만 필요하다
  -> @Scheduled만으로 충분

배치 Job의 한 단계로 기록, 실패 관리, 흐름 제어, 재시작, 테스트가 필요하다
  -> Tasklet Step 사용
```

### Tasklet과 Chunk는 섞어서 쓸 수 있다

Tasklet과 Chunk는 둘 중 하나만 골라야 하는 관계가 아닙니다. 한 Job 안에서 자연스럽게 섞어서 사용할 수 있습니다.

실제 배치에서는 오히려 이런 구조가 자주 나옵니다.

```text
dailySettlementJob
  Step 1: 임시 테이블 초기화        -> Tasklet
  Step 2: 주문 데이터 정산 처리     -> Chunk
  Step 3: 결과 요약 테이블 갱신     -> Tasklet
  Step 4: 완료 알림 전송            -> Tasklet
```

각 방식의 역할은 다릅니다.

```text
Tasklet
  한 번 실행하면 되는 작업
  예: 테이블 초기화, 파일 이동, 단일 SQL 실행, 알림 전송

Chunk
  많은 데이터를 반복해서 읽고 처리하고 저장하는 작업
  예: 주문 100만 건 읽기 -> 정산 계산 -> DB 저장
```

Spring Batch 관점에서는 Tasklet Step도 Step이고, Chunk Step도 Step입니다.

```text
Job
  Step(Tasklet)
  Step(Chunk)
  Step(Tasklet)
```

그래서 `.start()`, `.next()`로 자연스럽게 이어 붙일 수 있습니다.

```java
@Bean
public Job dailySettlementJob(
        JobRepository jobRepository,
        Step cleanupStep,
        Step settlementStep,
        Step reportStep
) {
    return new JobBuilder("dailySettlementJob", jobRepository)
            .start(cleanupStep)
            .next(settlementStep)
            .next(reportStep)
            .build();
}
```

핵심은 이렇습니다.

> Tasklet과 Chunk는 경쟁 관계가 아니라, 서로 다른 성격의 Step 구현 방식입니다.

"한 번 할 일"은 Tasklet, "여러 건을 반복 처리할 일"은 Chunk로 잡으면 됩니다.

---

## 7. Chunk 지향 처리

Spring Batch에서 가장 중요한 처리 방식은 `Chunk` 지향 처리입니다.

Chunk는 "덩어리"라는 뜻입니다. Spring Batch에서는 데이터를 일정 개수만큼 모아서 처리하는 단위를 의미합니다.

예를 들어 주문 데이터 1,000건을 처리해야 하고 chunk size가 100이라면 다음처럼 처리합니다.

```text
1번 chunk: 1번부터 100번 주문 처리 후 commit
2번 chunk: 101번부터 200번 주문 처리 후 commit
3번 chunk: 201번부터 300번 주문 처리 후 commit
...
10번 chunk: 901번부터 1000번 주문 처리 후 commit
```

왜 한 건씩 commit하지 않고 묶어서 처리할까요?

한 건마다 DB에 저장하고 commit하면 너무 느릴 수 있습니다. 반대로 1,000건 전체를 한 번에 처리하다가 999번째에서 실패하면 너무 많은 작업이 rollback될 수 있습니다.

Chunk는 그 중간 지점입니다.

> 너무 자주 commit하지도 않고, 너무 크게 묶지도 않기 위해 데이터를 적당한 크기로 나눕니다.

조금 더 작은 예시로 보겠습니다.

주문 5건을 chunk size 2로 처리하면 다음과 같습니다.

```text
chunk size = 2

1번 chunk: 1번 주문, 2번 주문 처리 후 commit
2번 chunk: 3번 주문, 4번 주문 처리 후 commit
3번 chunk: 5번 주문 처리 후 commit
```

마지막 chunk는 꼭 chunk size만큼 꽉 차지 않아도 됩니다. 남은 데이터가 1건뿐이면 1건만 처리하고 commit합니다.

여기서 예상할 수 있는 commit 횟수는 3번입니다.

```text
총 5건 / chunk size 2
=> 2건 + 2건 + 1건
=> commit 3번
```

이런 식으로 chunk size를 바꾸면 commit 횟수도 달라집니다.

```text
chunk size 1: 1건마다 commit, 총 5번 commit
chunk size 2: 2건마다 commit, 총 3번 commit
chunk size 3: 3건마다 commit, 총 2번 commit
```

그래서 chunk size는 단순한 숫자가 아닙니다. 성능, rollback 범위, 메모리 사용량에 영향을 주는 중요한 설정입니다.

---

## 8. Reader, Processor, Writer

Chunk 지향 처리는 보통 세 단계로 이루어집니다.

```text
read -> process -> write
```

Spring Batch에서는 각각을 다음 인터페이스로 표현합니다.

- `ItemReader`: 데이터를 읽는다.
- `ItemProcessor`: 데이터를 가공하거나 걸러낸다.
- `ItemWriter`: 데이터를 저장하거나 출력한다.

예를 들어 주문 정산 배치를 생각해봅니다.

```text
ItemReader
  주문 데이터를 DB에서 읽는다.

ItemProcessor
  주문 금액, 할인 금액, 수수료를 계산해 정산 DTO로 바꾼다.

ItemWriter
  정산 결과를 DB에 저장한다.
```

코드로 보면 흐름은 대략 이렇습니다.

```java
Order order = reader.read();
Settlement settlement = processor.process(order);
writer.write(settlement);
```

실제로는 Spring Batch가 이 흐름을 chunk 단위로 반복해줍니다.

---

## 9. ItemReader

`ItemReader`는 데이터를 하나씩 읽는 역할을 합니다.

중요한 특징은 `read()`가 한 번 호출될 때 item 하나를 반환한다는 점입니다.

```java
public interface ItemReader<T> {
    T read() throws Exception;
}
```

읽을 데이터가 더 이상 없으면 `null`을 반환합니다.

이 `null`은 아주 중요합니다.

> Reader가 `null`을 반환하면 Spring Batch는 "이제 읽을 데이터가 끝났다"고 판단합니다.

Reader는 다양한 곳에서 데이터를 읽을 수 있습니다.

- CSV 파일
- JSON 파일
- XML 파일
- JDBC로 DB 조회
- JPA로 Entity 조회
- 메모리 List
- 외부 API

초반 학습에서는 `ListItemReader`처럼 단순한 Reader로 시작하는 것이 좋습니다. 처음부터 DB Reader를 사용하면 SQL, 트랜잭션, 페이징 개념이 한꺼번에 섞여 어렵게 느껴질 수 있습니다.

---

## 10. ItemProcessor

`ItemProcessor`는 읽은 데이터를 가공하는 역할을 합니다.

```java
public interface ItemProcessor<I, O> {
    O process(I item) throws Exception;
}
```

`I`는 입력 타입이고, `O`는 출력 타입입니다.

예를 들어 `Order`를 읽어서 `Settlement`로 바꿀 수 있습니다.

```java
public class SettlementItemProcessor implements ItemProcessor<Order, Settlement> {

    @Override
    public Settlement process(Order order) {
        return new Settlement(order.getId(), order.getAmount());
    }
}
```

Processor는 단순 변환뿐 아니라 필터링에도 사용됩니다.

Processor가 `null`을 반환하면 해당 item은 Writer로 전달되지 않습니다.

```java
if (order.isCanceled()) {
    return null;
}
```

이 경우 Spring Batch는 "처리 대상에서 제외했다"고 보고 `filterCount`를 증가시킵니다.

주의할 점은 `null` 반환과 예외 발생은 다르다는 것입니다.

- `null` 반환: 정상적으로 걸러낸 데이터
- 예외 발생: 처리 중 문제가 생긴 데이터

이 둘을 구분해야 테스트와 운영 로그를 정확하게 이해할 수 있습니다.

---

## 11. ItemWriter

`ItemWriter`는 처리된 데이터를 저장하거나 출력하는 역할을 합니다.

Spring Batch 5 이후 Writer는 `Chunk`를 받습니다.

```java
public interface ItemWriter<T> {
    void write(Chunk<? extends T> chunk) throws Exception;
}
```

Reader와 Processor는 item을 하나씩 다루지만, Writer는 chunk 단위로 묶인 데이터를 받습니다.

예를 들어 chunk size가 100이라면 Writer는 최대 100개의 item을 한 번에 받습니다.

```text
Reader: 1개씩 읽음
Processor: 1개씩 가공
Writer: chunk로 모아서 저장
```

Writer는 보통 다음 일을 합니다.

- DB에 insert/update 한다.
- 파일에 쓴다.
- 메시지 큐에 보낸다.
- 로그로 출력한다.

대량 DB 저장에서는 `JdbcBatchItemWriter`를 자주 사용합니다. 이름처럼 JDBC batch 기능을 이용해 여러 insert/update를 묶어서 실행합니다.

---

## 12. Chunk와 트랜잭션

Chunk는 트랜잭션과 함께 이해해야 합니다.

트랜잭션은 "여러 작업을 하나의 성공 또는 실패 단위로 묶는 것"입니다.

예를 들어 은행 이체를 생각해봅니다.

```text
A 계좌에서 10,000원 차감
B 계좌에 10,000원 증가
```

둘 중 하나만 성공하면 안 됩니다. 둘 다 성공해야 하고, 하나라도 실패하면 둘 다 취소되어야 합니다. 이런 단위가 트랜잭션입니다.

Spring Batch의 Chunk 처리에서는 보통 chunk 하나가 하나의 트랜잭션 단위가 됩니다.

chunk size가 100이면 다음과 같이 이해할 수 있습니다.

```text
100건 읽기
100건 처리
100건 쓰기
commit
```

만약 80번째 item을 처리하다 예외가 발생하면 어떻게 될까요?

기본적으로 해당 chunk에서 수행한 작업은 rollback됩니다. 즉, 그 chunk는 성공한 것으로 기록되지 않습니다.

하지만 이전 chunk들은 이미 commit되었기 때문에 그대로 남아 있습니다.

```text
1번 chunk: commit 완료
2번 chunk: commit 완료
3번 chunk: 처리 중 실패 -> rollback
```

이 구조 덕분에 대량 데이터를 조금씩 안전하게 처리할 수 있습니다.

주문 5건, chunk size 2 예시로 다시 보겠습니다.

```text
1번 chunk: 1번 주문, 2번 주문
  Writer 성공
  commit 완료

2번 chunk: 3번 주문, 4번 주문
  3번 주문은 취소 주문이라 Processor에서 filter
  4번 주문 처리 중 예외 발생
  rollback

3번 chunk: 아직 실행되지 않음
```

이 상황에서 중요한 점은 1번 chunk는 이미 commit되었기 때문에 그대로 남는다는 것입니다. 실패한 것은 2번 chunk입니다.

```text
이미 commit된 chunk: 유지됨
실패한 chunk: rollback됨
아직 시작하지 않은 chunk: 처리되지 않음
```

그래서 배치에서는 "어디까지 commit됐는가?"가 매우 중요합니다. 재시작할 때도 이 기준으로 다시 처리할 범위가 결정됩니다.

---

## 13. JobParameters

`JobParameters`는 Job을 실행할 때 전달하는 입력값입니다.

예를 들어 일일 정산 Job은 어떤 날짜를 정산할지 알아야 합니다.

```bash
--spring.batch.job.name=dailySettlementJob --run.date=2026-04-30
```

여기서 `run.date=2026-04-30`이 JobParameter입니다.

JobParameters는 단순한 입력값 이상의 의미를 가집니다. Spring Batch에서는 JobParameters가 JobInstance를 구분하는 기준이 됩니다.

쉽게 말하면 다음과 같습니다.

```text
Job 이름 + JobParameters = JobInstance
```

예를 들어 같은 Job이라도 날짜가 다르면 다른 JobInstance입니다.

```text
dailySettlementJob + run.date=2026-04-29
dailySettlementJob + run.date=2026-04-30
```

이 둘은 서로 다른 실행 대상입니다.

반대로 같은 Job 이름과 같은 JobParameters로 다시 실행하면 Spring Batch는 "같은 JobInstance를 다시 실행하려는구나"라고 판단합니다.

이 개념은 재시작과 멱등성을 이해할 때 매우 중요합니다.

조금 더 현실적으로 보면 JobParameters는 "이번 실행의 업무 조건"입니다.

```text
run.date=2026-04-30
```

이 값은 "2026년 4월 30일 주문을 정산한다"는 뜻입니다. 따라서 날짜가 다르면 처리 대상 자체가 달라집니다.

```text
run.date=2026-04-30 -> 4월 30일 주문 정산
run.date=2026-05-01 -> 5월 1일 주문 정산
```

반면 아래 값은 처리 대상보다는 실행 옵션에 가깝습니다.

```text
request.user=admin
log.level=debug
```

이런 값들이 달라졌다고 해서 "다른 날짜의 정산"이 되는 것은 아닙니다.

---

## 14. identifying parameter

JobParameters 중에는 JobInstance를 구분하는 데 사용되는 값이 있습니다. 이를 identifying parameter라고 합니다.

말이 어렵지만 뜻은 단순합니다.

> identifying parameter는 "이 실행이 같은 작업인지 다른 작업인지 구분하는 값"입니다.

예를 들어 정산 날짜는 보통 identifying parameter입니다.

```text
run.date=2026-04-30
```

4월 30일 정산과 5월 1일 정산은 다른 작업이어야 하기 때문입니다.

반면 로그 출력 여부 같은 값은 JobInstance를 구분하는 기준이 아닐 수 있습니다.

```text
debug=true
```

이 값이 다르다고 완전히 다른 정산 작업이라고 보기는 어렵습니다.

초반에는 이렇게 생각하면 됩니다.

- 비즈니스 처리 대상을 결정하는 값: identifying일 가능성이 높다.
- 실행 편의나 옵션에 가까운 값: non-identifying일 가능성이 있다.

테스트에서 `timestamp`를 매번 넣으면 매번 새로운 JobInstance가 만들어집니다. 성공 테스트에는 편하지만, restart 테스트에는 방해가 됩니다. restart를 확인하려면 같은 identifying JobParameters로 다시 실행해야 합니다.

아래 표처럼 생각하면 더 쉽습니다.

| 실행 | Job 이름 | identifying parameter | 같은 JobInstance인가? |
|------|----------|------------------------|------------------------|
| 1번째 | `dailySettlementJob` | `run.date=2026-04-30` | 기준 실행 |
| 2번째 | `dailySettlementJob` | `run.date=2026-04-30` | 같음 |
| 3번째 | `dailySettlementJob` | `run.date=2026-05-01` | 다름 |
| 4번째 | `monthlyBillingJob` | `run.date=2026-04-30` | 다름 |

즉, JobInstance를 구분할 때는 Job 이름과 identifying parameter를 함께 봅니다.

가장 많이 하는 실수는 restart 테스트에서 매번 timestamp를 identifying parameter로 넣는 것입니다.

```text
1번째 실행: run.date=2026-04-30, timestamp=100
2번째 실행: run.date=2026-04-30, timestamp=200
```

사람 눈에는 둘 다 4월 30일 정산처럼 보입니다. 하지만 Spring Batch 입장에서는 identifying parameter가 달라졌으므로 서로 다른 JobInstance입니다.

---

## 15. JobInstance

`JobInstance`는 "특정 Job과 특정 JobParameters 조합으로 만들어지는 논리적인 실행 대상"입니다.

예를 들어 다음은 하나의 JobInstance입니다.

```text
dailySettlementJob + run.date=2026-04-30
```

이 JobInstance는 여러 번 실행 기록을 가질 수 있습니다.

처음 실행했다가 실패할 수 있습니다.

```text
JobInstance: dailySettlementJob, run.date=2026-04-30
  1번째 실행: FAILED
```

같은 파라미터로 다시 실행하면 같은 JobInstance에 새로운 실행 기록이 추가됩니다.

```text
JobInstance: dailySettlementJob, run.date=2026-04-30
  1번째 실행: FAILED
  2번째 실행: COMPLETED
```

이때 1번째 실행과 2번째 실행은 각각 `JobExecution`입니다.

비유하자면 JobInstance는 "시험 과목"에 가깝고, JobExecution은 "그 시험에 응시한 횟수"에 가깝습니다.

```text
JobInstance
  2026-04-30 정산이라는 시험 과목

JobExecution
  첫 번째 응시: 실패
  두 번째 응시: 성공
```

과목 자체가 바뀐 것은 아닙니다. 같은 과목에 다시 응시한 것입니다.

---

## 16. JobExecution

`JobExecution`은 JobInstance를 실제로 한 번 실행한 기록입니다.

JobInstance가 "무엇을 실행하려는가"라면, JobExecution은 "그것을 실제로 실행한 한 번의 시도"입니다.

예를 들어 2026년 4월 30일 정산 Job이 첫 번째 실행에서 실패하고 두 번째 실행에서 성공했다면 다음처럼 볼 수 있습니다.

```text
JobInstance
  dailySettlementJob + run.date=2026-04-30

JobExecution 1
  status: FAILED

JobExecution 2
  status: COMPLETED
```

Spring Batch는 JobExecution에 다음 정보를 기록합니다.

- 시작 시간
- 종료 시간
- 실행 상태
- 종료 상태
- 실패 예외
- JobExecutionContext

처음에는 JobInstance와 JobExecution을 이렇게 구분하면 됩니다.

- JobInstance: 같은 작업인가, 다른 작업인가를 구분하는 단위
- JobExecution: 그 작업을 실제로 실행한 시도 한 번

메타데이터 테이블로 보면 대략 이렇게 됩니다.

```text
BATCH_JOB_INSTANCE
  JOB_INSTANCE_ID=1, JOB_NAME=dailySettlementJob, JOB_KEY=run.date=2026-04-30

BATCH_JOB_EXECUTION
  JOB_EXECUTION_ID=10, JOB_INSTANCE_ID=1, STATUS=FAILED
  JOB_EXECUTION_ID=11, JOB_INSTANCE_ID=1, STATUS=COMPLETED
```

`BATCH_JOB_INSTANCE`는 1줄인데, `BATCH_JOB_EXECUTION`은 2줄입니다. 같은 작업을 두 번 시도했기 때문입니다.

---

## 17. StepExecution

`StepExecution`은 Step을 실제로 실행한 기록입니다.

JobExecution 하나 안에는 여러 StepExecution이 들어갈 수 있습니다.

```text
JobExecution
  StepExecution 1
  StepExecution 2
  StepExecution 3
```

StepExecution에는 배치 학습에서 자주 보는 중요한 count들이 기록됩니다.

- `readCount`: Reader가 읽은 item 수
- `writeCount`: Writer가 쓴 item 수
- `filterCount`: Processor에서 `null`로 걸러진 item 수
- `readSkipCount`: 읽는 중 skip된 수
- `processSkipCount`: 처리 중 skip된 수
- `writeSkipCount`: 쓰는 중 skip된 수
- `commitCount`: commit 횟수
- `rollbackCount`: rollback 횟수

테스트에서는 단순히 Job이 성공했는지만 확인하면 부족합니다.

예를 들어 "5건 중 취소 주문 1건은 제외하고 4건만 저장해야 한다"는 요구사항이 있다면 다음처럼 확인해야 합니다.

```text
ExitStatus = COMPLETED
readCount = 5
filterCount = 1
writeCount = 4
```

이런 count를 확인해야 Spring Batch가 의도대로 동작했는지 알 수 있습니다.

앞에서 든 주문 5건 예시를 count로 바꿔보면 다음과 같습니다.

```text
1번 주문: 정상 처리
2번 주문: 정상 처리
3번 주문: 취소 주문이라 filter
4번 주문: 정상 처리
5번 주문: 데이터 오류로 skip
```

이 경우 결과는 대략 이렇게 기대할 수 있습니다.

```text
readCount = 5
filterCount = 1
writeCount = 3
processSkipCount = 1
```

왜 writeCount가 3일까요?

```text
전체 5건
- 취소 주문 1건 filter
- 오류 주문 1건 skip
= 실제 저장 3건
```

이런 식으로 count를 읽을 수 있으면 테스트가 훨씬 명확해집니다.

---

## 18. JobRepository

`JobRepository`는 Spring Batch의 실행 기록을 저장하고 조회하는 저장소입니다.

이름만 보면 코드 저장소처럼 느껴질 수 있지만, 여기서는 배치 메타데이터 저장소라고 이해하면 됩니다.

Spring Batch는 Job을 실행하면서 다음과 같은 메타데이터를 DB에 저장합니다.

- JobInstance
- JobExecution
- StepExecution
- JobParameters
- ExecutionContext

이 정보들이 저장되는 대표 테이블은 다음과 같습니다.

- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_JOB_EXECUTION_PARAMS`
- `BATCH_STEP_EXECUTION`
- `BATCH_JOB_EXECUTION_CONTEXT`
- `BATCH_STEP_EXECUTION_CONTEXT`

처음에는 테이블 이름이 많아서 부담스러울 수 있습니다. 하지만 핵심 테이블 3개만 먼저 보면 됩니다.

- `BATCH_JOB_INSTANCE`: 어떤 JobInstance가 있는가?
- `BATCH_JOB_EXECUTION`: 그 JobInstance를 몇 번 실행했는가?
- `BATCH_STEP_EXECUTION`: 각 Step은 어떻게 실행됐는가?

Spring Batch를 공부할 때는 이 테이블들을 직접 SELECT 해보는 것이 매우 좋습니다. 눈으로 보면 JobInstance와 JobExecution의 차이가 훨씬 빨리 이해됩니다.

---

## 19. JobLauncher

`JobLauncher`는 Job을 실행하는 역할을 합니다.

이름 그대로 Job을 launch, 즉 시작합니다.

```text
JobLauncher
  Job + JobParameters를 받아 실행한다.
```

테스트에서는 `JobLauncherTestUtils`를 자주 사용합니다. 내부적으로 JobLauncher를 이용해 Job을 실행하고, 실행 결과인 JobExecution을 돌려줍니다.

```java
JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);
```

운영 환경에서는 CLI, 스케줄러, API, JobOperator 등을 통해 Job이 실행될 수 있지만, 결국 핵심은 같습니다.

> Job은 JobParameters와 함께 실행된다.

---

## 20. JobOperator

`JobOperator`는 실행 중이거나 과거에 실행된 Job을 운영 관점에서 다루기 위한 도구입니다.

예를 들어 다음과 같은 일을 할 수 있습니다.

- Job 시작
- Job 중지
- 실패한 Job 재시작
- 실행 중인 Job 목록 조회
- JobExecution 정보 조회

초반에는 JobLauncher만 알아도 됩니다. 다만 운영 단계로 가면 JobOperator가 중요해집니다.

간단히 구분하면 다음과 같습니다.

- `JobLauncher`: Job을 실행하는 기본 도구
- `JobOperator`: 실행된 Job을 조회, 중지, 재시작하는 운영 도구

---

## 21. ExecutionContext

`ExecutionContext`는 Spring Batch가 재시작을 위해 상태를 저장하는 공간입니다.

예를 들어 파일을 10,000줄 읽는 배치가 있다고 해봅니다. 6,000줄까지 처리하고 실패했습니다.

다시 실행할 때 처음부터 읽을 수도 있지만, 가능하다면 6,001번째 줄부터 이어서 읽는 것이 좋습니다.

이때 "어디까지 읽었는지" 같은 정보를 저장하는 곳이 ExecutionContext입니다.

ExecutionContext는 두 종류가 있습니다.

- JobExecutionContext
- StepExecutionContext

대부분의 Reader/Writer 상태는 StepExecutionContext에 저장됩니다.

주의할 점도 있습니다.

- 민감 정보를 저장하면 안 됩니다.
- 너무 큰 데이터를 저장하면 안 됩니다.
- 임시 캐시처럼 아무 값이나 넣으면 안 됩니다.
- 재시작에 필요한 최소한의 상태만 저장해야 합니다.

ExecutionContext는 편리한 Map처럼 보이지만, 실제로는 DB에 저장되는 배치 메타데이터입니다.

조금 더 구체적으로 보겠습니다.

파일을 읽는 Reader가 있고, 현재 6,000번째 줄까지 읽었다고 해봅니다. Spring Batch는 재시작을 위해 이런 정보를 저장할 수 있습니다.

```text
current.line=6000
```

DB를 paging으로 읽는 Reader라면 이런 정보가 저장될 수 있습니다.

```text
current.page=60
```

물론 실제 key 이름과 값은 Reader 구현체에 따라 다릅니다. 중요한 것은 "비즈니스 데이터 전체"를 저장하는 곳이 아니라 "다시 시작하기 위해 필요한 위치 정보"를 저장하는 곳이라는 점입니다.

나쁜 사용 예:

```text
customer.name=홍길동
customer.phone=010-...
order.fullPayload={...아주 큰 JSON...}
```

좋은 사용 예:

```text
lastProcessedOrderId=6000
currentResourceIndex=3
```

ExecutionContext를 이해할 때는 "재시작용 책갈피"라고 생각하면 쉽습니다.

---

## 22. BatchStatus와 ExitStatus

Spring Batch에는 상태를 나타내는 값이 두 종류 있습니다.

- `BatchStatus`
- `ExitStatus`

처음에는 둘이 비슷해 보여 헷갈립니다.

`BatchStatus`는 Spring Batch가 판단하는 실행 상태입니다.

예를 들면 다음과 같습니다.

- `STARTING`
- `STARTED`
- `COMPLETED`
- `FAILED`
- `STOPPED`

반면 `ExitStatus`는 Step이나 Job이 어떤 종료 결과로 끝났는지 나타냅니다.

기본적으로는 `COMPLETED`, `FAILED`처럼 BatchStatus와 비슷하게 흘러갑니다. 하지만 개발자가 커스텀 ExitStatus를 만들 수도 있습니다.

예를 들어 "처리는 성공했지만 스킵이 있었다"는 의미를 따로 표현하고 싶을 수 있습니다.

```text
BatchStatus: COMPLETED
ExitStatus: COMPLETED_WITH_SKIPS
```

처음에는 이렇게 이해하면 충분합니다.

- `BatchStatus`: 시스템이 보는 실행 상태
- `ExitStatus`: 다음 Flow 분기나 운영 판단에 사용할 수 있는 종료 코드

예를 들어 정산 Step에서 일부 데이터가 skip됐지만, skipLimit 안이라 전체 배치는 성공했다고 해봅니다.

```text
BatchStatus: COMPLETED
ExitStatus: COMPLETED
skipCount: 3
```

이 상태도 충분히 가능합니다. 시스템 입장에서는 정책 안에서 정상 종료했기 때문입니다.

하지만 운영자가 "skip이 있던 성공"을 따로 보고 싶다면 Listener나 Step 설정을 통해 ExitStatus를 다르게 만들 수 있습니다.

```text
BatchStatus: COMPLETED
ExitStatus: COMPLETED_WITH_SKIPS
skipCount: 3
```

그래서 테스트에서는 `BatchStatus` 또는 `ExitStatus`만 보지 말고 count도 함께 확인하는 것이 좋습니다.

---

## 23. Restart

Restart는 실패한 Job을 같은 JobParameters로 다시 실행하는 것입니다.

예를 들어 다음 JobInstance가 실패했다고 해봅니다.

```text
dailySettlementJob + run.date=2026-04-30
```

이 Job을 같은 `run.date=2026-04-30`으로 다시 실행하면 Spring Batch는 새 JobInstance를 만들지 않고 기존 실패 JobInstance를 재시작하려고 합니다.

```text
1번째 실행: FAILED
2번째 실행: COMPLETED
```

이때 중요한 것은 "같은 JobParameters"입니다.

만약 재실행할 때 `timestamp`를 새로 넣어버리면 Spring Batch는 완전히 다른 JobInstance라고 생각할 수 있습니다.

```text
dailySettlementJob + run.date=2026-04-30 + timestamp=100
dailySettlementJob + run.date=2026-04-30 + timestamp=200
```

이렇게 되면 restart를 테스트하는 것이 아니라 새로운 Job을 두 번 실행하는 셈이 됩니다.

Restart를 공부할 때는 다음을 꼭 확인해야 합니다.

- 첫 실행이 `FAILED`인지
- 두 번째 실행이 같은 identifying JobParameters를 사용하는지
- `BATCH_JOB_INSTANCE` row가 새로 생기지 않는지
- `BATCH_JOB_EXECUTION` row만 추가되는지
- 이미 commit된 데이터가 중복 처리되지 않는지

다시 주문 5건, chunk size 2 예시로 보겠습니다.

```text
1번 chunk: 1번, 2번 주문 commit 성공
2번 chunk: 3번, 4번 주문 처리 중 실패
```

이 상태에서 같은 JobParameters로 재시작하면 이미 성공한 1번 chunk를 어떻게 다룰지 고민해야 합니다.

Spring Batch는 Reader의 상태 저장, Step 상태, 트랜잭션 기록을 바탕으로 재시작을 지원합니다. 하지만 Writer가 멱등하지 않으면 문제가 생길 수 있습니다.

예를 들어 1번, 2번 주문 정산 결과가 이미 저장되어 있는데 재시작 중 다시 insert하면 중복 데이터가 생길 수 있습니다.

그래서 restart를 배울 때는 항상 두 가지를 같이 봐야 합니다.

```text
Spring Batch 메타데이터: 어디까지 실행됐는가?
비즈니스 테이블: 결과 데이터가 중복되지 않는가?
```

---

## 24. 멱등성

멱등성은 같은 작업을 여러 번 실행해도 결과가 망가지지 않는 성질입니다.

말은 어렵지만 예시는 쉽습니다.

어떤 주문의 정산 결과를 저장한다고 해봅니다.

나쁜 방식:

```text
실행할 때마다 settlement 테이블에 무조건 insert
```

이렇게 하면 같은 날짜 정산 Job을 두 번 실행했을 때 같은 주문의 정산 결과가 두 줄 생길 수 있습니다.

좋은 방식:

```text
order_id와 settlement_date 기준으로 이미 있으면 update 또는 skip
```

이렇게 하면 같은 Job이 다시 실행되어도 결과가 중복되지 않습니다.

Spring Batch는 재시작 기능을 제공하지만, 비즈니스 데이터의 멱등성까지 자동으로 보장해주지는 않습니다.

즉, 개발자가 직접 고민해야 합니다.

- 같은 JobParameters로 다시 실행하면 결과가 중복되지 않는가?
- 실패 후 재시작하면 이미 처리된 데이터가 다시 저장되지 않는가?
- Writer가 insert만 해도 괜찮은가?
- unique key나 upsert가 필요한가?

배치에서는 멱등성이 매우 중요합니다. 배치는 실패할 수 있고, 실패한 배치는 다시 실행해야 하기 때문입니다.

---

## 25. Skip

Skip은 특정 item에서 예외가 발생했을 때 그 item을 건너뛰고 계속 처리하는 기능입니다.

예를 들어 10,000건 중 1건의 데이터 형식이 잘못됐다고 전체 배치를 실패시킬 필요는 없을 수 있습니다.

```text
1번 처리 성공
2번 처리 성공
3번 데이터 오류 -> skip
4번 처리 성공
...
```

Skip을 사용할 때는 반드시 제한을 둬야 합니다.

```text
skipLimit = 10
```

이 말은 "최대 10건까지는 skip을 허용하지만, 그 이상이면 실패시킨다"는 뜻입니다.

왜 제한이 필요할까요?

데이터 10,000건 중 9,000건이 오류인데도 전부 skip하고 성공 처리하면 더 큰 문제가 됩니다. 배치가 성공했다고 보이지만 실제로는 대부분 처리하지 못한 상태이기 때문입니다.

Skip은 편리하지만 조심해서 사용해야 합니다.

- 어떤 예외를 skip할지 명확해야 한다.
- skipLimit을 반드시 설정해야 한다.
- skip된 데이터는 로그나 별도 테이블로 추적할 수 있어야 한다.
- skip이 많으면 성공이 아니라 데이터 품질 문제로 봐야 한다.

filter와 skip은 비슷해 보이지만 다릅니다.

```text
3번 주문: 취소 주문
  -> 우리 정책상 정산 대상이 아님
  -> Processor가 null 반환
  -> filterCount 증가

5번 주문: 금액이 "abc"처럼 잘못 들어옴
  -> 처리하려 했지만 예외 발생
  -> skip 정책에 포함된 예외라면 skip
  -> processSkipCount 또는 readSkipCount 증가
```

filter는 "정상적으로 제외"이고, skip은 "문제가 있었지만 허용하고 건너뜀"입니다.

---

## 26. Retry

Retry는 일시적인 실패가 발생했을 때 다시 시도하는 기능입니다.

예를 들어 외부 API 호출이나 네트워크 문제는 잠깐 실패했다가 다시 성공할 수 있습니다.

```text
1번째 시도: 외부 API timeout
2번째 시도: 외부 API timeout
3번째 시도: 성공
```

이런 경우에는 바로 실패시키기보다 몇 번 재시도하는 것이 좋습니다.

하지만 모든 예외를 retry하면 안 됩니다.

예를 들어 주민등록번호 형식이 잘못된 데이터는 100번 다시 처리해도 성공하지 않습니다. 이런 문제는 retry가 아니라 skip 또는 fail 대상입니다.

간단히 구분하면 다음과 같습니다.

- Retry에 어울리는 예외: 네트워크 오류, 일시적 DB 연결 오류, timeout
- Retry에 어울리지 않는 예외: 데이터 형식 오류, 필수값 누락, 비즈니스 규칙 위반

Retry도 제한이 필요합니다.

```text
retryLimit = 3
```

무한 재시도는 운영 장애로 이어질 수 있습니다.

Retry와 Skip이 함께 있으면 이렇게 흐를 수 있습니다.

```text
4번 주문 처리 중 외부 API timeout 발생
  1번째 시도 실패
  2번째 시도 실패
  3번째 시도 성공
  -> 최종 성공, skip 아님

5번 주문 처리 중 금액 형식 오류 발생
  다시 시도해도 성공 가능성이 낮음
  -> retry 대상이 아니라 skip 또는 fail 대상
```

판단 기준은 "다시 하면 성공할 가능성이 있는가?"입니다.

```text
다시 하면 성공할 수 있음 -> retry
다시 해도 똑같이 실패함 -> skip 또는 fail
```

---

## 27. Listener

Listener는 Spring Batch 실행 흐름 중간중간에 끼어들어 로그를 남기거나 통계를 수집하는 도구입니다.

예를 들어 다음 시점에 동작할 수 있습니다.

- Job 시작 전
- Job 종료 후
- Step 시작 전
- Step 종료 후
- item 읽기 전후
- item 처리 전후
- item 쓰기 전후
- skip 발생 시
- retry 발생 시

Listener는 비즈니스 로직을 넣는 곳이라기보다 관찰과 부가 작업을 넣는 곳에 가깝습니다.

좋은 사용 예:

- Job 실행 시간 로그
- Step별 read/write/skip count 로그
- skip된 데이터 기록
- 실패 알림 발송

조심해야 할 사용 예:

- 핵심 비즈니스 계산을 Listener에 넣기
- Listener에서 DB 데이터를 크게 변경하기
- Listener 예외로 Job 흐름을 복잡하게 만들기

처음에는 Listener를 "배치 실행을 관찰하는 도구"로 이해하면 좋습니다.

---

## 28. Flow

Flow는 Step들이 어떤 순서로 실행될지 정의하는 흐름입니다.

가장 단순한 흐름은 순차 실행입니다.

```text
step1 -> step2 -> step3
```

하지만 실제 배치에서는 조건에 따라 다른 Step으로 가야 할 수 있습니다.

예를 들어 검증 Step이 실패하면 정산 Step으로 가지 않고 오류 처리 Step으로 보낼 수 있습니다.

```text
validateStep 성공 -> settlementStep
validateStep 실패 -> errorReportStep
```

Spring Batch에서는 Step의 `ExitStatus`를 기준으로 흐름을 나눌 수 있습니다.

Flow를 배우기 전에는 먼저 단순한 `.next()` 흐름을 익히고, 그 다음 조건 분기를 공부하는 것이 좋습니다.

---

## 29. Cursor와 Paging

DB에서 많은 데이터를 읽을 때 Reader 방식으로 Cursor와 Paging을 자주 만납니다.

Cursor는 DB 조회 결과에 커서를 열어두고 한 줄씩 가져오는 방식입니다.

```text
SELECT 결과를 열어둠
  한 줄 읽기
  한 줄 읽기
  한 줄 읽기
```

장점은 흐름이 단순하다는 것입니다. 하지만 DB 커넥션을 오래 잡고 있을 수 있습니다.

Paging은 데이터를 페이지 단위로 끊어서 조회하는 방식입니다.

```text
1페이지 조회: 1~100
2페이지 조회: 101~200
3페이지 조회: 201~300
```

대량 처리와 확장성 측면에서는 Paging을 선호하는 경우가 많습니다.

처음에는 이렇게 기억해도 충분합니다.

- Cursor: 결과를 열어두고 한 줄씩 읽는다.
- Paging: 일정 크기로 나누어 여러 번 조회한다.

Spring Batch 학습에서는 `JdbcPagingItemReader`를 먼저 익히는 것이 좋습니다.

---

## 30. page size와 chunk size

`page size`와 `chunk size`는 이름이 비슷하지만 다릅니다.

- page size: Reader가 DB에서 한 번에 가져오는 데이터 수
- chunk size: 몇 개를 처리한 뒤 commit할지 정하는 수

예를 들어 다음 설정을 생각해봅니다.

```text
page size = 100
chunk size = 50
```

Reader는 DB에서 100개씩 가져오지만, 트랜잭션 commit은 50개마다 일어날 수 있습니다.

반대로 다음도 가능합니다.

```text
page size = 50
chunk size = 100
```

이 경우 Reader는 50개씩 두 번 읽어서 100개를 모은 뒤 write/commit할 수 있습니다.

초반에는 둘을 같게 두면 이해하기 쉽습니다.

```text
page size = chunk size = 100
```

운영에서는 데이터 크기, DB 성능, 메모리 사용량, 처리 속도를 측정해 조정합니다.

---

## 31. Spring Batch 메타데이터 테이블

Spring Batch는 실행 기록을 `BATCH_`로 시작하는 테이블에 저장합니다.

처음부터 모든 테이블을 외울 필요는 없습니다.

먼저 아래 흐름만 이해하면 됩니다.

```text
BATCH_JOB_INSTANCE
  Job 이름과 JobParameters 기준으로 JobInstance 저장

BATCH_JOB_EXECUTION
  실제 실행 시도 기록 저장

BATCH_STEP_EXECUTION
  각 Step 실행 기록과 count 저장
```

예를 들어 같은 JobParameters로 처음 실패하고 다시 성공하면 대략 이런 그림이 됩니다.

```text
BATCH_JOB_INSTANCE
  1 row

BATCH_JOB_EXECUTION
  2 rows
  - 첫 번째 실행 FAILED
  - 두 번째 실행 COMPLETED

BATCH_STEP_EXECUTION
  각 JobExecution에 대한 Step 실행 기록
```

학습할 때는 Job을 실행한 뒤 다음 질문을 던져보면 좋습니다.

- `BATCH_JOB_INSTANCE`에 row가 새로 생겼는가?
- `BATCH_JOB_EXECUTION`은 몇 개 생겼는가?
- `BATCH_STEP_EXECUTION`의 `READ_COUNT`, `WRITE_COUNT`는 예상과 같은가?
- 실패 후 재시작했을 때 새 JobInstance가 생겼는가, 기존 JobInstance에 Execution만 추가됐는가?

---

## 32. Spring Batch 테스트에서 봐야 할 것

Spring Batch 테스트는 단순히 "예외가 안 났다"로 끝내면 부족합니다.

기본적으로 다음을 확인해야 합니다.

- Job의 `ExitStatus`
- Step의 `readCount`
- Step의 `writeCount`
- Step의 `filterCount`
- skip/retry count
- DB에 저장된 최종 결과
- 메타데이터 테이블의 실행 기록

예를 들어 5건을 읽고, 1건을 필터링하고, 4건을 저장하는 배치라면 다음을 확인합니다.

```text
ExitStatus = COMPLETED
readCount = 5
filterCount = 1
writeCount = 4
DB 저장 결과 = 4건
```

재시작 테스트라면 다음도 확인합니다.

```text
첫 실행 = FAILED
두 번째 실행 = COMPLETED
JobInstance = 동일
JobExecution = 추가됨
결과 데이터 = 중복 없음
```

이런 검증을 해야 Spring Batch의 핵심인 안정성과 재시작 가능성을 제대로 확인할 수 있습니다.

---

## 33. 처음 학습할 때 추천 순서

처음부터 모든 기능을 배우려고 하면 어렵습니다. 아래 순서로 가면 이해가 자연스럽습니다.

1. Tasklet으로 `Hello, Batch!` 출력
2. 단일 Step Job 실행
3. JobParameters 전달
4. `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION` 직접 조회
5. `ListItemReader`로 Chunk 처리
6. Reader, Processor, Writer 분리
7. Processor에서 `null` 반환으로 필터링
8. DB Reader/Writer 적용
9. Skip과 Retry 적용
10. 실패 후 Restart 테스트

중요한 것은 "코드가 실행된다"에서 멈추지 않는 것입니다.

매번 메타데이터 테이블을 확인하면서 Spring Batch가 내부적으로 무엇을 기록하는지 봐야 합니다.

---

## 34. 자주 헷갈리는 개념 정리

### Job과 Step

Job은 전체 배치 작업입니다. Step은 Job 안의 실행 단계입니다.

```text
Job = 일일 정산 전체
Step = 주문 읽기, 정산 계산, 결과 저장 같은 단계
```

### JobInstance와 JobExecution

JobInstance는 같은 작업인지 구분하는 논리 단위입니다. JobExecution은 실제 실행 한 번의 기록입니다.

```text
JobInstance = dailySettlementJob + run.date=2026-04-30
JobExecution = 그 JobInstance를 실행한 1번째 시도, 2번째 시도
```

### Tasklet과 Chunk

Tasklet은 한 번 실행하는 작업에 어울립니다. Chunk는 많은 데이터를 읽고, 처리하고, 쓰는 반복 작업에 어울립니다.

```text
Tasklet = 파일 삭제, 간단한 SQL 실행
Chunk = 주문 100만 건 읽어서 정산 결과 저장
```

### filter와 skip

filter는 Processor가 `null`을 반환해 정상적으로 제외한 것입니다. skip은 예외가 발생했지만 정책에 따라 건너뛴 것입니다.

```text
filter = 취소 주문이라 저장 대상에서 제외
skip = 데이터 형식 오류가 발생했지만 허용 범위라 건너뜀
```

### restart와 새 실행

restart는 같은 identifying JobParameters로 실패한 JobInstance를 다시 실행하는 것입니다. 새 실행은 다른 JobParameters로 새로운 JobInstance를 만드는 것입니다.

```text
restart = 같은 run.date로 다시 실행
새 실행 = 다른 run.date 또는 다른 identifying parameter로 실행
```

---

## 35. 한 문장으로 정리

Spring Batch는 많은 데이터를 안정적으로 처리하기 위해 Job과 Step으로 작업을 나누고, Chunk 단위로 읽기/처리/쓰기를 반복하며, 실행 상태를 메타데이터 테이블에 기록해서 실패 처리와 재시작을 가능하게 해주는 프레임워크입니다.

이 문장을 조금 더 풀면 다음과 같습니다.

```text
Job은 전체 작업이다.
Step은 Job 안의 단계다.
Chunk는 데이터를 나누어 처리하는 단위다.
Reader는 읽고, Processor는 가공하고, Writer는 쓴다.
JobParameters는 실행 대상을 구분한다.
JobRepository는 실행 기록을 저장한다.
ExecutionContext는 재시작에 필요한 상태를 저장한다.
Spring Batch 학습의 핵심은 메타데이터 테이블을 직접 보며 이 흐름을 이해하는 것이다.
```

---

## 36. 다음 문서로 이어가기

이 문서를 읽은 뒤에는 다음 문서를 함께 보면 좋습니다.

- [메타데이터 테이블 관찰 가이드](metadata-tables.md)
- [챕터별 실습 체크리스트](chapter-checklist.md)
- [트러블슈팅](troubleshooting.md)

처음에는 모든 용어를 완벽하게 외우려고 하지 않아도 됩니다. 대신 작은 Job을 하나 실행하고, DB의 `BATCH_` 테이블을 직접 보면서 "아, 이 실행이 이렇게 기록되는구나"를 확인하는 것이 가장 빠른 학습 방법입니다.
