package org.hcz.notification.service;

import jakarta.annotation.PreDestroy;
import org.hcz.notification.entity.Notification;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class NotificationSseHub {

    private static final long NO_TIMEOUT = 0L;

    private final ConcurrentMap<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user is required for notification events");
        }

        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.compute(userId, (id, connections) -> {
            Set<SseEmitter> userConnections = connections == null
                    ? ConcurrentHashMap.newKeySet()
                    : connections;
            userConnections.add(emitter);
            return userConnections;
        });

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException error) {
            cleanup.run();
            emitter.completeWithError(error);
        }
        return emitter;
    }

    public void publish(Long userId, Notification notification, long unreadCount) {
        Set<SseEmitter> userConnections = emitters.get(userId);
        if (userConnections == null || userConnections.isEmpty()) {
            return;
        }

        NotificationSsePayload payload = new NotificationSsePayload(notification, unreadCount);
        for (SseEmitter emitter : userConnections) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException error) {
                remove(userId, emitter);
                emitter.completeWithError(error);
            }
        }
    }

    @Scheduled(fixedRateString = "${notification.sse.heartbeat-ms:15000}")
    public void sendHeartbeats() {
        emitters.forEach((userId, userConnections) -> {
            for (SseEmitter emitter : userConnections) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException error) {
                    remove(userId, emitter);
                    emitter.completeWithError(error);
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, connections) -> {
            connections.remove(emitter);
            return connections.isEmpty() ? null : connections;
        });
    }

    @PreDestroy
    public void closeConnections() {
        emitters.values().forEach(connections -> connections.forEach(SseEmitter::complete));
        emitters.clear();
    }
}
