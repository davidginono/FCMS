package com.foresight.fcms.notifications;

import com.foresight.fcms.notifications.service.MailOutboxService;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MailDispatcher {
    private final MailOutboxService outbox;
    private final ObjectProvider<JavaMailSender> sender;
    private final MeterRegistry metrics;
    private final String from;

    public MailDispatcher(
            MailOutboxService o,
            ObjectProvider<JavaMailSender> s,
            MeterRegistry m,
            @Value("${fcms.mail.from:noreply@fcms.local}") String f) {
        outbox = o;
        sender = s;
        metrics = m;
        from = f;
    }

    @Scheduled(
            fixedDelayString = "${fcms.mail.interval:30000}",
            initialDelayString = "${fcms.mail.initial-delay:30000}")
    public void deliver() {
        var transport = sender.getIfAvailable();
        if (transport == null) return;
        for (var mail : outbox.claim()) {
            try {
                var msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(mail.recipient());
                msg.setSubject(mail.subject());
                msg.setText(mail.body());
                transport.send(msg);
                outbox.sent(mail.id(), mail.attempt());
                metrics.counter("fcms.mail.sent").increment();
            } catch (MailException e) {
                outbox.failed(mail.id(), mail.attempt());
                metrics.counter("fcms.mail.failed").increment();
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("Mail delivery failed for outbox record {}", mail.id());
            }
        }
    }
}
