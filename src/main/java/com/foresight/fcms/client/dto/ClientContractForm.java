package com.foresight.fcms.client.dto;

import jakarta.validation.constraints.Min;

import org.springframework.web.multipart.MultipartFile;

public class ClientContractForm {
    @Min(0)
    private long version;

    private MultipartFile contractUpload;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }

    public MultipartFile getContractUpload() {
        return contractUpload;
    }

    public void setContractUpload(MultipartFile value) {
        contractUpload = value;
    }
}
