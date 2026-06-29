package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    public enum Type { CHAT, JOIN, LEAVE, ERROR, SYSTEM }

    private Type type;
    private String sender;
    private String content;
    private String timestamp;
    private Integer presenceCount;

    public ChatMessage() {}

    public ChatMessage(Type type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.timestamp = Instant.now().toString();
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public Integer getPresenceCount() { return presenceCount; }
    public void setPresenceCount(Integer presenceCount) { this.presenceCount = presenceCount; }
}
