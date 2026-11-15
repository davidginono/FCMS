package com.foresight.fcms.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialYearRepository extends JpaRepository<FinancialYear, Long> {

    List<FinancialYear> findAllByOrderByFyearDesc();

    List<FinancialYear> findTop1001ByOrderByFyearDesc();

    Optional<FinancialYear> findFirstByOrderByFyearDesc();

    Optional<FinancialYear> findByFyear(String fyear);

    List<FinancialYear> findByIdInOrFyearIn(
            java.util.Collection<Long> ids, java.util.Collection<String> years);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query(
            "select y from FinancialYear y where y.id in :ids order by y.id")
    List<FinancialYear> lockImportYears(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
