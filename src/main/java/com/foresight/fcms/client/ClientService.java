package com.foresight.fcms.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    private final SaccosRepository saccosRepository;

    public ClientService(SaccosRepository saccosRepository) {
        this.saccosRepository = saccosRepository;
    }

    @Transactional(readOnly = true)
    public Page<ClientSummaryDto> findSaccos(Pageable pageable) {
        return saccosRepository.findAll(pageable).map(ClientSummaryDto::from);
    }

    @Transactional(readOnly = true)
    public long countSaccos() {
        return saccosRepository.count();
    }
}
