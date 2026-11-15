package com.foresight.fcms.notifications.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.List;

@Service
public class MailOutboxService {
    private final JdbcTemplate db;

    public MailOutboxService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String recipient, String subject, String body) {
        db.update(
                "insert into mail_outbox(recipient,subject,body) values (?,?,?)",
                recipient,
                subject,
                body);
    }

    @Transactional
    public List<Mail> claim() {
        db.update(
                "with expired as (select id from mail_outbox where status='SENDING' and attempts>=5"
                    + " and available_at<current_timestamp order by id for update skip locked limit"
                    + " 10) update mail_outbox m set status='FAILED',body='' from expired e where"
                    + " m.id=e.id");
        return db.query(
                "with candidate as (select id from mail_outbox where status in"
                    + " ('PENDING','SENDING') and available_at<current_timestamp and attempts<5"
                    + " order by id for update skip locked limit 10) update mail_outbox m set"
                    + " status='SENDING',attempts=attempts+1,available_at=current_timestamp+interval"
                    + " '5 minutes' from candidate c where m.id=c.id returning"
                    + " m.id,m.recipient,m.subject,m.body,m.attempts",
                (r, n) ->
                        new Mail(
                                r.getLong(1),
                                r.getString(2),
                                r.getString(3),
                                r.getString(4),
                                r.getInt(5)));
    }

    @Transactional
    public void sent(long id, int attempt) {
        db.update(
                "update mail_outbox set status='SENT',sent_at=current_timestamp,body='' where id=?"
                        + " and status='SENDING' and attempts=?",
                id,
                attempt);
    }

    @Transactional
    public void failed(long id, int attempt) {
        db.update(
                "update mail_outbox set status=case when attempts>=5 then 'FAILED' else 'PENDING'"
                        + " end,body=case when attempts>=5 then '' else body end,"
                        + " available_at=current_timestamp+interval '5 minutes' where id=?"
                        + " and status='SENDING' and attempts=?",
                id,
                attempt);
    }

    public record Mail(long id, String recipient, String subject, String body, int attempt) {}
}
