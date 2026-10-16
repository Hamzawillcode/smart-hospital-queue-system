package com.hospital.notification.service;

import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.notification.dto.NotificationMessage;

import java.util.Optional;

public interface NotificationService {

    /**
     * Pushes the new state to every affected patient.
     *
     * @return false if the snapshot was older than one already pushed (and was dropped)
     */
    boolean onQueueUpdated(QueueUpdatedEvent event);

    /** Latest known view for a patient, sent once when their page subscribes. */
    Optional<NotificationMessage> currentView(String accessCode);
}
