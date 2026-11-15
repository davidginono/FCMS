package com.foresight.fcms.auth.service;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

@Service
public class SessionRevocationService {
    private final SessionRegistry sessions;

    public SessionRevocationService(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    public void revoke(String email) {
        Runnable expire =
                () ->
                        sessions.getAllPrincipals().stream()
                                .filter(
                                        p ->
                                                p instanceof UserDetails u
                                                        && u.getUsername().equalsIgnoreCase(email))
                                .forEach(
                                        p ->
                                                sessions.getAllSessions(p, false)
                                                        .forEach(s -> s.expireNow()));
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            expire.run();
                        }
                    });
        else expire.run();
    }
}
