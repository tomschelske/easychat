package com.example.chat.service;

import com.example.chat.model.Room;
import com.example.chat.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    static final Duration EXPIRY = Duration.ofMinutes(10);

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final RoomRepository roomRepository;

    public RoomManager(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public Room createRoom(String creatorToken) {
        String code;
        Room room;
        do {
            code = generateCode();
            room = new Room(code, creatorToken);
        } while (rooms.putIfAbsent(code, room) != null);

        roomRepository.save(code, creatorToken, room.getCreatedAt());
        return room;
    }

    public Optional<Room> findRoom(String roomCode) {
        return Optional.ofNullable(rooms.get(roomCode.toUpperCase()));
    }

    public boolean removeRoom(String roomCode) {
        String code = roomCode.toUpperCase();
        boolean removed = rooms.remove(code) != null;
        if (removed) roomRepository.delete(code);
        return removed;
    }

    public int activeRoomCount() {
        return rooms.size();
    }

    public Iterable<Room> allRooms() {
        return rooms.values();
    }

    /** Called once on startup to restore rooms from SQLite. */
    public void rebuildFromDatabase() {
        Instant cutoff = Instant.now().minus(EXPIRY);
        int loaded = 0, purged = 0;

        for (RoomRepository.RoomRow row : roomRepository.findAll()) {
            boolean expired = row.lastEmptiedAt() != null && row.lastEmptiedAt().isBefore(cutoff);
            if (expired) {
                roomRepository.delete(row.roomCode());
                purged++;
                continue;
            }
            Room room = new Room(row.roomCode(), row.creatorToken());
            if (row.lastEmptiedAt() != null) {
                room.setLastEmptiedAt(row.lastEmptiedAt());
            }
            rooms.put(row.roomCode(), room);
            loaded++;
        }
        log.info("Startup rebuild: {} rooms loaded, {} expired rooms purged", loaded, purged);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
