package com.foresight.fcms.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialYearRepository extends JpaRepository<FinancialYear, Long> {

    List<FinancialYear> findAllByOrderByFyearDesc();

    Optional<FinancialYear> findFirstByOrderByFyearDesc();

    Optional<FinancialYear> findByFyear(String fyear);
}
