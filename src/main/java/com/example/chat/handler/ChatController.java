package com.example.chat.handler;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.Room;
import com.example.chat.service.RoomManager;
import com.example.chat.service.SessionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class ChatController {

    private final RoomManager roomManager;
    private final SessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messaging;

    public ChatController(RoomManager roomManager,
                          SessionRegistry sessionRegistry,
                          SimpMessagingTemplate messaging) {
        this.roomManager = roomManager;
        this.sessionRegistry = sessionRegistry;
        this.messaging = messaging;
    }

    // --- HTTP: room management ---

    @PostMapping("/api/rooms")
    @ResponseBody
    public ResponseEntity<Map<String, String>> createRoom() {
        String creatorToken = UUID.randomUUID().toString();
        Room room = roomManager.createRoom(creatorToken);
        return ResponseEntity.ok(Map.of(
            "roomCode", room.getRoomCode(),
            "creatorToken", creatorToken
        ));
    }

    @GetMapping("/api/rooms/{roomCode}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRoom(@PathVariable String roomCode) {
        return roomManager.findRoom(roomCode)
            .map(r -> ResponseEntity.ok(Map.<String, Object>of(
                "roomCode", r.getRoomCode(),
                "createdAt", r.getCreatedAt().toString(),
                "presenceCount", r.presenceCount()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/rooms/{roomCode}")
    @ResponseBody
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomCode,
                                           @RequestHeader("X-Creator-Token") String token) {
        Room room = roomManager.findRoom(roomCode).orElse(null);
        if (room == null) return ResponseEntity.notFound().build();
        if (!room.getCreatorToken().equals(token)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        // Notify everyone in the room before wiping state
        messaging.convertAndSend("/topic/room/" + roomCode.toUpperCase(),
            new ChatMessage(ChatMessage.Type.SYSTEM, "server", "ROOM_DELETED"));

        // Remove all session registrations for users in this room
        for (String principal : room.getSessions()) {
            sessionRegistry.remove(principal);
        }

        roomManager.removeRoom(roomCode);
        return ResponseEntity.noContent().build();
    }

    // --- WebSocket: join room ---

    @MessageMapping("/room/{roomCode}/join")
    public void joinRoom(@DestinationVariable String roomCode,
                         ChatMessage message,
                         SimpMessageHeaderAccessor headerAccessor) {

        String principal = headerAccessor.getUser().getName();

        Room room = roomManager.findRoom(roomCode).orElse(null);
        if (room == null) {
            sendPersonal(principal, new ChatMessage(ChatMessage.Type.ERROR, "server", "ROOM_NOT_FOUND"));
            return;
        }

        String name = message.getSender() == null ? "" : message.getSender().trim();
        if (name.isEmpty() || name.length() > 32) {
            sendPersonal(principal, new ChatMessage(ChatMessage.Type.ERROR, "server", "INVALID_NAME"));
            return;
        }

        String existingHolder = room.getClaimedNames().putIfAbsent(name.toLowerCase(), principal);
        if (existingHolder != null) {
            sendPersonal(principal, new ChatMessage(ChatMessage.Type.ERROR, "server", "NAME_TAKEN"));
            return;
        }

        room.getActiveNames().put(principal, name);
        room.getSessions().add(principal);
        room.setLastEmptiedAt(null);
        sessionRegistry.register(principal, roomCode.toUpperCase(), name);

        // Replay buffer to joining user only
        List<ChatMessage> buffered;
        synchronized (room.getMessageBuffer()) {
            buffered = List.copyOf(room.getMessageBuffer());
        }
        buffered.forEach(msg -> sendPersonal(principal, msg));

        ChatMessage joinOk = new ChatMessage(ChatMessage.Type.SYSTEM, "server", "JOIN_OK");
        joinOk.setSender(name);
        sendPersonal(principal, joinOk);

        ChatMessage joinEvent = new ChatMessage(ChatMessage.Type.JOIN, name, "");
        joinEvent.setPresenceCount(room.presenceCount());
        messaging.convertAndSend("/topic/room/" + roomCode.toUpperCase(), joinEvent);
    }

    // --- WebSocket: send message ---

    @MessageMapping("/room/{roomCode}/send")
    public void sendMessage(@DestinationVariable String roomCode,
                            ChatMessage message,
                            SimpMessageHeaderAccessor headerAccessor) {

        String principal = headerAccessor.getUser().getName();

        Room room = roomManager.findRoom(roomCode).orElse(null);
        if (room == null) return;

        String displayName = room.getActiveNames().get(principal);
        if (displayName == null) return;

        String content = message.getContent();
        if (content == null || content.isBlank()) return;

        ChatMessage outgoing = new ChatMessage(ChatMessage.Type.CHAT, displayName, content.trim());

        synchronized (room.getMessageBuffer()) {
            if (room.getMessageBuffer().size() >= Room.MESSAGE_BUFFER_SIZE) {
                room.getMessageBuffer().pollFirst();
            }
            room.getMessageBuffer().addLast(outgoing);
        }

        messaging.convertAndSend("/topic/room/" + roomCode.toUpperCase(), outgoing);
    }

    private void sendPersonal(String principal, ChatMessage message) {
        messaging.convertAndSendToUser(principal, "/queue/personal", message);
    }
}
