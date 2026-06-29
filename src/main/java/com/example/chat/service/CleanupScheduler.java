package com.example.chat.service;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@EnableScheduling
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messaging;

    public CleanupScheduler(RoomManager roomManager, SimpMessagingTemplate messaging) {
        this.roomManager = roomManager;
        this.messaging = messaging;
    }

    @Scheduled(fixedDelay = 120_000)
    public void purgeExpiredRooms() {
        Instant cutoff = Instant.now().minus(RoomManager.EXPIRY);
        List<String> toRemove = new ArrayList<>();

        // Collect expired rooms without modifying the map during iteration
        for (Room room : roomManager.allRooms()) {
            Instant emptied = room.getLastEmptiedAt();
            if (emptied != null && emptied.isBefore(cutoff)) {
                toRemove.add(room.getRoomCode());
            }
        }

        for (String code : toRemove) {
            messaging.convertAndSend("/topic/room/" + code,
                new ChatMessage(ChatMessage.Type.SYSTEM, "server", "ROOM_EXPIRED"));
            roomManager.removeRoom(code);
            log.info("Purged expired room: {}", code);
        }

        if (!toRemove.isEmpty()) {
            log.info("Cleanup run: {} room(s) purged", toRemove.size());
        }
    }
}
