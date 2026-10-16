package com.hospital.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.Priority;
import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.notification.dto.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Kafka → notification-service → real STOMP-over-SockJS client. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
@Testcontainers
class NotificationServiceApplicationTests {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @LocalServerPort int port;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;

    private static final String ALICE = "AliceAccessCode0000001";
    private static final String BOB = "BobAccessCode000000001";

    @Test
    void patientReceivesLivePositionsAndCurrentStateOnSubscribe() throws Exception {
        StompSession session = connect();
        BlockingQueue<NotificationMessage> bob = subscribe(session, "/topic/patient/" + BOB);
        BlockingQueue<NotificationMessage> alice = subscribe(session, "/topic/patient/" + ALICE);
        Thread.sleep(500); // let subscriptions register before publishing

        publish(1, List.of(waiting(ALICE, 1), waiting(BOB, 2)), List.of());
        assertThat(bob.poll(30, TimeUnit.SECONDS)).satisfies(m -> {
            assertThat(m.status()).isEqualTo("WAITING");
            assertThat(m.position()).isEqualTo(2);
            assertThat(m.tokenNumber()).isEqualTo("IT-2");
        });

        publish(2, List.of(waiting(BOB, 1)), List.of(active(ALICE)));
        assertThat(bob.poll(30, TimeUnit.SECONDS).position()).isEqualTo(1);
        NotificationMessage aliceView = drainLast(alice, "IN_CONSULTATION");
        assertThat(aliceView.roomNumber()).isEqualTo("OPD-1");

        // A stale snapshot is dropped: Bob is not pushed back to position 2.
        publish(1, List.of(waiting(ALICE, 1), waiting(BOB, 2)), List.of());
        assertThat(bob.poll(3, TimeUnit.SECONDS)).isNull();

        // A page that connects now gets the current state immediately.
        BlockingQueue<NotificationMessage> initial = subscribe(connect(), "/app/patient/" + BOB);
        assertThat(initial.poll(10, TimeUnit.SECONDS)).satisfies(m -> {
            assertThat(m.position()).isEqualTo(1);
            assertThat(m.version()).isEqualTo(2);
        });
    }

    private StompSession connect() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);
        return client.connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
        }).get(10, TimeUnit.SECONDS);
    }

    private static BlockingQueue<NotificationMessage> subscribe(StompSession session, String destination) {
        BlockingQueue<NotificationMessage> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return NotificationMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((NotificationMessage) payload);
            }
        });
        return received;
    }

    private static NotificationMessage drainLast(BlockingQueue<NotificationMessage> queue, String status)
            throws InterruptedException {
        NotificationMessage message;
        do {
            message = queue.poll(30, TimeUnit.SECONDS);
        } while (message != null && !status.equals(message.status()));
        assertThat(message).isNotNull();
        return message;
    }

    private void publish(long version, List<QueueUpdatedEvent.WaitingEntry> waiting,
                         List<QueueUpdatedEvent.ActiveEntry> active) throws Exception {
        QueueUpdatedEvent event = new QueueUpdatedEvent("e" + version, "IT", version, "FIFO", waiting, active,
                Instant.now());
        kafkaTemplate.send(KafkaTopics.QUEUE_UPDATED, "IT", objectMapper.writeValueAsString(event)).get();
    }

    private static QueueUpdatedEvent.WaitingEntry waiting(String code, int position) {
        return new QueueUpdatedEvent.WaitingEntry("q-" + code, "p-" + code, "Name", "IT-" + position, code,
                Priority.NORMAL, position, (position - 1) * 10, Instant.now());
    }

    private static QueueUpdatedEvent.ActiveEntry active(String code) {
        return new QueueUpdatedEvent.ActiveEntry("q-" + code, "p-" + code, "Name", "IT-1", code, "IN_CONSULTATION",
                "Dr. Sharma", "OPD-1", Instant.now());
    }
}
