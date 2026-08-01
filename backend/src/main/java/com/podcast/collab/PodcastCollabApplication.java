package com.podcast.collab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PodcastCollabApplication {
    public static void main(String[] args) {
        SpringApplication.run(PodcastCollabApplication.class, args);
    }
}
