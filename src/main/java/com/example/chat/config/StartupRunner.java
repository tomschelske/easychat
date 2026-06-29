package com.example.chat.config;

import com.example.chat.service.RoomManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner implements ApplicationRunner {

    private final RoomManager roomManager;

    public StartupRunner(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        roomManager.rebuildFromDatabase();
    }
}
