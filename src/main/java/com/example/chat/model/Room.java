package com.example.chat.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {

    public static final int MESSAGE_BUFFER_SIZE = 10;

    private final String roomCode;
    private final String creatorToken;
    private final Instant createdAt;

    // principalId → displayName (source of truth for who is in the room)
    private final ConcurrentHashMap<String, String> activeNames = new ConcurrentHashMap<>();

    // name.toLowerCase() → principalId (used for atomic name-uniqueness claim via putIfAbsent)
    private final ConcurrentHashMap<String, String> claimedNames = new ConcurrentHashMap<>();

    // ordered list of connected principal IDs (safe for concurrent iteration during broadcast)
    private final CopyOnWriteArrayList<String> sessions = new CopyOnWriteArrayList<>();

    // ring buffer — synchronize externally before reading or writing
    private final Deque<ChatMessage> messageBuffer = new ArrayDeque<>(MESSAGE_BUFFER_SIZE);

    private volatile Instant lastEmptiedAt;

    public Room(String roomCode, String creatorToken) {
        this.roomCode = roomCode;
        this.creatorToken = creatorToken;
        this.createdAt = Instant.now();
    }

    public String getRoomCode() { return roomCode; }
    public String getCreatorToken() { return creatorToken; }
    public Instant getCreatedAt() { return createdAt; }
    public ConcurrentHashMap<String, String> getActiveNames() { return activeNames; }
    public ConcurrentHashMap<String, String> getClaimedNames() { return claimedNames; }
    public CopyOnWriteArrayList<String> getSessions() { return sessions; }
    public Deque<ChatMessage> getMessageBuffer() { return messageBuffer; }
    public Instant getLastEmptiedAt() { return lastEmptiedAt; }
    public void setLastEmptiedAt(Instant t) { this.lastEmptiedAt = t; }

    public int presenceCount() { return activeNames.size(); }
}
