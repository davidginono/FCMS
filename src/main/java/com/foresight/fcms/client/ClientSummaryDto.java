package com.foresight.fcms.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientSummaryDto(
    Integer id,
    String name,
    String region,
    String status,
    LocalDate entryDate,
    LocalDate expireDate,
    BigDecimal amount
) {
    static ClientSummaryDto from(Saccos saccos) {
        String statusName = saccos.getStatus() == null ? "" : saccos.getStatus().getName();
        return new ClientSummaryDto(
            saccos.getId(),
            saccos.getSaccosName(),
            saccos.getRegion(),
            statusName,
            saccos.getEntryDate(),
            saccos.getExpireDate(),
            saccos.getAmount()
        );
    }
}
