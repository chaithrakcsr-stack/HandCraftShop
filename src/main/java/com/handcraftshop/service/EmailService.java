package com.handcraftshop.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/** Optional SMTP notifications. Configure SMTP_* environment variables to enable delivery. */
public final class EmailService {
    private EmailService() {}

    public static void send(String to, String subject, String body) {
        String host = System.getenv("SMTP_HOST");
        String from = System.getenv("SMTP_FROM");
        if (host == null || host.isBlank() || from == null || from.isBlank() || to == null || to.isBlank()) {
            System.out.println("[HandcraftShop email not sent: SMTP is not configured] " + subject + " -> " + to);
            return;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", System.getenv().getOrDefault("SMTP_PORT", "587"));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            String user = System.getenv("SMTP_USER");
            String pass = System.getenv("SMTP_PASSWORD");
            Session session = Session.getInstance(props, new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass);
                }
            });
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
        } catch (Exception e) {
            System.err.println("HandcraftShop email error: " + e.getMessage());
        }
    }
}
