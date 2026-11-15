package com.foresight.fcms.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientStatusRepository extends JpaRepository<ClientStatus, Long> {

    List<ClientStatus> findAllByOrderByIdAsc();
}
