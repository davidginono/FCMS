package com.foresight.fcms.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientStatusRepository extends JpaRepository<ClientStatus, Long> {

    List<ClientStatus> findAllByOrderByIdAsc();

    @org.springframework.data.jpa.repository.Query(
            "select s from ClientStatus s where s.id in :ids or upper(s.name) in :codes")
    List<ClientStatus> importChoices(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids,
            @org.springframework.data.repository.query.Param("codes")
                    java.util.Collection<String> codes);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query(
            "select s from ClientStatus s where s.id in :ids order by s.id")
    List<ClientStatus> lockImportStatuses(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
