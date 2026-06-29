package com.example.chat.handler;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.Room;
import com.example.chat.repository.RoomRepository;
import com.example.chat.service.RoomManager;
import com.example.chat.service.SessionRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;

@Component
public class RoomEventListener {

    private final RoomManager roomManager;
    private final SessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messaging;
    private final RoomRepository roomRepository;

    public RoomEventListener(RoomManager roomManager,
                             SessionRegistry sessionRegistry,
                             SimpMessagingTemplate messaging,
                             RoomRepository roomRepository) {
        this.roomManager = roomManager;
        this.sessionRegistry = sessionRegistry;
        this.messaging = messaging;
        this.roomRepository = roomRepository;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() == null) return;
        String principal = event.getUser().getName();

        SessionRegistry.SessionInfo info = sessionRegistry.remove(principal).orElse(null);
        if (info == null) return;

        Room room = roomManager.findRoom(info.roomCode()).orElse(null);
        if (room == null) return;

        room.getSessions().remove(principal);
        String displayName = room.getActiveNames().remove(principal);
        if (displayName != null) {
            room.getClaimedNames().remove(displayName.toLowerCase());
        }

        if (room.getSessions().isEmpty()) {
            Instant now = Instant.now();
            room.setLastEmptiedAt(now);
            roomRepository.updateLastEmptiedAt(room.getRoomCode(), now);
        }

        String name = displayName != null ? displayName : "someone";
        ChatMessage leaveEvent = new ChatMessage(ChatMessage.Type.LEAVE, name, "");
        leaveEvent.setPresenceCount(room.presenceCount());
        messaging.convertAndSend("/topic/room/" + info.roomCode(), leaveEvent);
    }
}
