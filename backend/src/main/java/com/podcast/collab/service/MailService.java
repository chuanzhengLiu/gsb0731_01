package com.podcast.collab.service;

/** 发件通道。生产环境替换为 SMTP/邮件服务商实现 */
public interface MailService {
    void send(String to, String subject, String body);
}
