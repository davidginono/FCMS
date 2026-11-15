package com.foresight.fcms.backup;

import com.foresight.fcms.backup.service.BackupExecutionService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupWorker {
    private final BackupExecutionService backups;

    public BackupWorker(BackupExecutionService b) {
        backups = b;
    }

    @Scheduled(
            fixedDelayString = "${fcms.backup.interval:10000}",
            initialDelay = 10000,
            scheduler = "backupScheduler")
    public void run() {
        backups.runNext();
    }
}
