package com.hospital.notification.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.regex.Pattern;

/**
 * Browsers only listen. Without this guard a client could SEND straight to
 * /topic/patient/{code} (the simple broker would relay it) and spoof messages,
 * or subscribe to arbitrary destinations.
 */
public class StompDestinationGuard implements ChannelInterceptor {

    static final String ACCESS_CODE = "[A-Za-z0-9_-]{16,64}";
    private static final Pattern ALLOWED_SUBSCRIPTIONS = Pattern.compile(
            "^/topic/patient/" + ACCESS_CODE + "$|^/app/patient/" + ACCESS_CODE + "$|^/topic/department/[A-Z0-9_-]{1,32}$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Clients may not send messages");
        }
        if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (destination == null || !ALLOWED_SUBSCRIPTIONS.matcher(destination).matches()) {
                throw new MessageDeliveryException("Subscription not allowed: " + destination);
            }
        }
        return message;
    }
}
