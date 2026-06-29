package com.example.chat.service;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionRegistry {

    public record SessionInfo(String roomCode, String displayName) {}

    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void register(String principal, String roomCode, String displayName) {
        sessions.put(principal, new SessionInfo(roomCode, displayName));
    }

    public Optional<SessionInfo> remove(String principal) {
        return Optional.ofNullable(sessions.remove(principal));
    }
}
