package dev.starryeye.session.outbox;

import dev.starryeye.session.jpa.OutboxEntity;
import dev.starryeye.session.jpa.OutboxEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
	 * 주의. 이 주기가 곧 보안 창이다. 로그아웃하고 최대 이만큼은 refresh token 이 아직 살아 있다.
	 *      원자성과 전달 보장을 얻는 대신 즉시성을 잃는 것이 outbox 의 대가다 — 동기 REST 는 정반대였다
	 *      (창이 0이지만 전달 보장이 없다).
	 *
	 * 주의. 한 행의 발행이 실패하면 그 자리에서 멈춘다. 뒤 행을 건너뛰고 계속하면 같은 파티션 키의
	 *      순서가 뒤바뀔 수 있다. 실패한 행은 다음 주기에 다시 시도된다.
	 *
	 * 주의. 발행 성공 후 markPublished 커밋 전에 죽으면 다음 주기에 같은 행을 다시 보낸다.
	 *      전달이 at-least-once 인 지점이 여기다. 소비자의 폐기가 조건부 갱신이라 멱등이므로 그대로 둔다.
	 *
	 * 주의. markPublished 를 this.markPublished(...) 로 직접 부르지 않고 self(ObjectProvider) 를 거쳐
	 *      프록시로 부른다. 같은 클래스 안에서의 직접 호출은 Spring 의 @Transactional 프록시를 우회한다
	 *      (셀프 호출 문제) — findById 가 돌려주는 행은 그 조회 트랜잭션이 끝나며 이미 detached 상태고,
	 *      그 뒤 필드만 바꿔도 아무도 flush 하지 않아 DB 에는 반영되지 않는다. 실제로 self 없이 돌렸을 때
	 *      OutboxPublisherTest 가 "표시가 안 남는다" 로 실패하는 것을 확인한 뒤 이 형태로 고쳤다.
	 *
	 * 주의. initialDelayString 을 fixedDelayString 과 같은 값으로 둔다. 지정하지 않으면 @Scheduled 의
	 *      첫 실행이 컨텍스트 기동과 거의 동시에(지연 없이) 백그라운드 스레드에서 돈다 — 테스트가
	 *      my.outbox-poll-interval-ms 를 길게 잡아 자동 실행을 꺼둔 셈 치고 publishPending() 을 직접
	 *      호출하는 도중에, 그 첫 자동 실행이 스레드풀 기동 지연 등으로 늦게 겹치면 같은 행을 두 스레드가
	 *      동시에 집어 경합할 수 있다(테스트 스위트 전체를 여러 번 돌리는 중 실제로 한 번 관찰됐다).
	 *      초기 지연도 같은 주기만큼 주면 이 겹침 창이 사실상 사라진다.
	 */

	private final OutboxEntityRepository repository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectProvider<OutboxPublisher> self;

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
			self.getObject().markPublished(row.getId());
		}
	}

	@Transactional
	public void markPublished(Long id) {
		repository.findById(id).ifPresent(row -> row.markPublished(Instant.now()));
	}
}
