package com.podcast.collab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * 本地/开发环境 mock 发件通道：
 * 1. 邮件内容落到应用日志；
 * 2. 同时追加写入 outbox 文件（默认 ./data/mock-mail.log），
 *    便于冒烟测试等外部链路按"邮件"取回重置/邀请链接。
 */
@Slf4j
@Service
public class LogMailService implements MailService {
    private final Path outbox;

    public LogMailService(@Value("${app.mock-mail-file:./data/mock-mail.log}") String outboxPath) {
        this.outbox = Paths.get(outboxPath);
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("==== MOCK MAIL ==== To: {} | Subject: {}\n{}", to, subject, body);
        try {
            if (outbox.getParent() != null) {
                Files.createDirectories(outbox.getParent());
            }
            String entry = String.format("---- %s ----%nTo: %s%nSubject: %s%n%s%n%n",
                    LocalDateTime.now(), to, subject, body);
            Files.writeString(outbox, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("mock 邮件写入 outbox 失败: {}", e.getMessage());
        }
    }
}
