package dev.starryeye.token_state.outbox;

import dev.starryeye.token_state.jpa.OutboxEntity;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

	/**
	 * outbox 의 미발행 행을 Kafka 로 옮긴다.
	 *
	 * 주의. 이 주기만큼(발행이 실패하면 그 이상) 사건이 감사 쪽에 늦게 닿는다. 폐기 같은 상태 변경 자체는 늦지
	 *      않는다 — 상태 변경은 사건 행을 쓴 트랜잭션에서 이미 커밋됐고, 이 폴러는 그 사실을 알릴 뿐이다.
	 *      그래서 이 주기는 보안 창이 아니라 감사 기록의 지연이다. 원자성과 전달 보장을 얻는 대신 즉시성을
	 *      잃는 것이 outbox 의 대가다.
	 *
	 * 주의. 한 행의 발행이 실패하면 그 자리에서 멈춘다. 뒤 행을 건너뛰고 계속하면 같은 파티션 키(계열)의
	 *      순서가 뒤바뀔 수 있다. 실패한 행은 다음 주기에 다시 시도된다.
	 *
	 * 주의. 이 재시도는 "실패가 언젠가 풀린다"는 전제에서만 뜻이 있다. 어떤 행 하나가 **영구적으로**
	 *      실패하면(예: 그 payload 를 브로커가 항상 거부하는 경우) 이 루프는 매 주기 그 행에서 똑같이
	 *      멈추고 return 한다 — 뒤에 쌓인 다른 모든 미발행 행(다른 계열의 사건 포함)도 그 행이 정리되기 전까지
	 *      함께 무기한 멈춘다. 로그(log.warn) 한 줄만 남고 별도 알림·건너뛰기 경로는 없다. "Kafka 가 죽었다
	 *      살아나면 지연되지만 결국 된다"는 그 실패가 일시적(Kafka 재기동 등)일 때만 참이다.
	 *
	 * 주의. kafkaTemplate.send(...).get(5, TimeUnit.SECONDS) 의 5초는 이 호출의 실제 상한이 아니다.
	 *      KafkaTemplate.send() 내부에서 Kafka 로 넘기기 전에 KafkaProducer 가 브로커 메타데이터를
	 *      기다리는데, 그 대기가 producer 의 max.block.ms(kafka-clients 의 기본값은 60초, 이 모듈의 main
	 *      application.yml 은 이 값을 설정하지 않는다)로 먼저 막힌다 — 브로커가 응답하지 않으면 우리
	 *      .get(5, SECONDS) 가 끼어들 기회도 없이 최대 1분까지 이 스레드가 그 안에서 멈출 수 있다는 뜻이다.
	 *
	 * 주의. 발행 성공 후 표시(markPublished) 커밋 전에 죽으면 다음 주기에 같은 행을 다시 보낸다.
	 *      전달이 at-least-once 인 지점이 여기다. 다시 보내는 행은 같은 payload, 곧 같은 eventId 를 싣고
	 *      나가므로 중복은 소비자가 eventId 로 걸러야 한다. 다만 이 경로(표시 직전 크래시)를 실제로
	 *      재현하는 테스트는 없다 — 프로세스 크래시를 테스트에서 안전하게 주입할 방법이 마땅치 않다. 이
	 *      성질은 코드 구조(발행과 표시가 별도 커밋)로만 뒷받침될 뿐 테스트로 덮이지는 않는다는 뜻이다.
	 *
	 * 주의. 행 하나를 표시하는 데 별도 @Transactional 메서드 대신 TransactionTemplate 을 직접 쓴다. 같은
	 *      클래스 안에서 @Transactional 메서드를 this.method(...) 로 부르면(암묵적 self-invocation) Spring
	 *      프록시를 우회해 그 애노테이션이 조용히 무시된다 — findById 가 돌려주는 행은 그 조회만의 짧은
	 *      트랜잭션이 끝나며 이미 detached 상태가 되고, 그 뒤 필드만 바꿔도 flush 할 트랜잭션이 없어 DB 에는
	 *      반영되지 않는다. TransactionTemplate 은 publishPending() 자신의 코드 안에서 트랜잭션 경계를
	 *      직접 여는 것이라 self-invocation 자체가 성립하지 않는다. OutboxPublisherTest 의 "발행 표시가
	 *      남는다" 단언이 이 경계가 실제로 커밋을 일으키는지 본다. @Transactional 애노테이션과
	 *      TransactionTemplate 을 함께 쓰지 않는다 — 경계가 둘이면 어느 쪽이 실제 경계인지 헷갈린다.
	 *
	 * 주의. initialDelayString 을 fixedDelayString 과 같은 값으로 둔다. 지정하지 않으면 @Scheduled 의
	 *      첫 실행이 컨텍스트 기동과 거의 동시에(지연 없이) 백그라운드 스레드에서 돈다 — 테스트가
	 *      my.outbox-poll-interval-ms 를 길게 잡아 자동 실행을 꺼둔 셈 치고 publishPending() 을 직접
	 *      호출하는 도중에, 그 첫 자동 실행이 스레드풀 기동 지연 등으로 늦게 겹치면 같은 행을 두 스레드가
	 *      동시에 집어 경합할 수 있다. 초기 지연도 같은 주기만큼 주면 이 겹침 창이 사실상 사라진다.
	 */

	private final OutboxEntityRepository repository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final TransactionTemplate transactionTemplate;

	@Scheduled(initialDelayString = "${my.outbox-poll-interval-ms}", fixedDelayString = "${my.outbox-poll-interval-ms}")
	public void publishPending() {
		List<OutboxEntity> pending = repository.findTop100ByPublishedAtIsNullOrderByIdAsc();
		for (OutboxEntity row : pending) {
			try {
				kafkaTemplate.send(row.getTopic(), row.getPartitionKey(), row.getPayload())
						.get(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				log.warn("outbox publish failed, will retry next cycle: id={} eventId={}",
						row.getId(), row.getEventId(), e);
				return; // 순서를 지키려고 그 자리에서 멈춘다
			}
			Long id = row.getId();
			transactionTemplate.executeWithoutResult(status ->
					repository.findById(id).ifPresent(r -> r.markPublished(Instant.now())));
		}
	}
}
