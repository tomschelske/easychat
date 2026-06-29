package com.example.chat.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;

@Repository
public class RoomRepository {

    private final JdbcTemplate jdbc;

    public RoomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void createSchema() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS rooms (
                room_code       TEXT PRIMARY KEY,
                creator_token   TEXT NOT NULL,
                is_public       INTEGER NOT NULL DEFAULT 0,
                max_users       INTEGER,
                created_at      TEXT NOT NULL,
                last_emptied_at TEXT
            )
            """);
    }

    public void save(String roomCode, String creatorToken, Instant createdAt) {
        jdbc.update(
            "INSERT INTO rooms (room_code, creator_token, created_at) VALUES (?, ?, ?)",
            roomCode, creatorToken, createdAt.toString()
        );
    }

    public void delete(String roomCode) {
        jdbc.update("DELETE FROM rooms WHERE room_code = ?", roomCode);
    }

    public void updateLastEmptiedAt(String roomCode, Instant time) {
        jdbc.update(
            "UPDATE rooms SET last_emptied_at = ? WHERE room_code = ?",
            time != null ? time.toString() : null, roomCode
        );
    }

    public List<RoomRow> findAll() {
        return jdbc.query(
            "SELECT room_code, creator_token, created_at, last_emptied_at FROM rooms",
            (rs, rowNum) -> new RoomRow(
                rs.getString("room_code"),
                rs.getString("creator_token"),
                Instant.parse(rs.getString("created_at")),
                rs.getString("last_emptied_at") != null
                    ? Instant.parse(rs.getString("last_emptied_at"))
                    : null
            )
        );
    }

    public record RoomRow(String roomCode, String creatorToken,
                          Instant createdAt, Instant lastEmptiedAt) {}
}
