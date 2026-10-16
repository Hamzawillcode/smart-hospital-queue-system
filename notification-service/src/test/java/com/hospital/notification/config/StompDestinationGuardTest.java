package com.hospital.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompDestinationGuardTest {

    private final StompDestinationGuard guard = new StompDestinationGuard();

    @Test
    void allowsPatientAndDepartmentSubscriptions() {
        assertThatCode(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/patient/Abc_def-0123456789xyz"), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/app/patient/Abc_def-0123456789xyz"), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/department/OPD"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSpoofingAndGuessableTopics() {
        // clients must never publish to a patient's topic
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SEND, "/topic/patient/Abc_def-0123456789xyz"), null))
                .isInstanceOf(MessageDeliveryException.class);
        // old-style token topics are too easy to guess
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/patient/OPD-3"), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/patient/**"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private static Message<byte[]> frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
