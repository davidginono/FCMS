package com.foresight.fcms.client.saccos.dto;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public class SaccosImportUpload {
    private UUID requestKey;
    private MultipartFile file;

    public UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(UUID value) {
        requestKey = value;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile value) {
        file = value;
    }
}
