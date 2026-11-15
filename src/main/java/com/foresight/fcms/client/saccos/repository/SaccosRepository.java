package com.foresight.fcms.client.saccos.repository;

import com.foresight.fcms.client.saccos.model.Saccos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SaccosRepository extends JpaRepository<Saccos, Long> {

    @EntityGraph(attributePaths = {"status", "financialYear"})
    @Query(
            value =
                    """
                    select s from Saccos s
                    where s.visible = true
                      and (:financialYearId is null or s.financialYear.id = :financialYearId)
                      and (:paymentStatusId is null or s.status.id = :paymentStatusId)
                      and (
                          :searchTerm is null
                          or lower(s.saccosName) like :searchTerm
                          or lower(s.region) like :searchTerm
                          or lower(s.operationalStatus) like :searchTerm
                          or lower(s.invoiceStatus) like :searchTerm
                      )
                    """,
            countQuery =
                    """
                    select count(s) from Saccos s
                    where s.visible = true
                      and (:financialYearId is null or s.financialYear.id = :financialYearId)
                      and (:paymentStatusId is null or s.status.id = :paymentStatusId)
                      and (
                          :searchTerm is null
                          or lower(s.saccosName) like :searchTerm
                          or lower(s.region) like :searchTerm
                          or lower(s.operationalStatus) like :searchTerm
                          or lower(s.invoiceStatus) like :searchTerm
                      )
                    """)
    Page<Saccos> search(
            @Param("financialYearId") Long financialYearId,
            @Param("paymentStatusId") Long paymentStatusId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    @EntityGraph(attributePaths = {"status", "financialYear"})
    @Query(
            """
            select s from Saccos s
            where s.visible = true
              and (:financialYearId is null or s.financialYear.id = :financialYearId)
              and (:paymentStatusId is null or s.status.id = :paymentStatusId)
              and (
                  :searchTerm is null
                  or lower(s.saccosName) like :searchTerm
                  or lower(s.region) like :searchTerm
                  or lower(s.operationalStatus) like :searchTerm
                  or lower(s.invoiceStatus) like :searchTerm
              )
            """)
    List<Saccos> findForExport(
            @Param("financialYearId") Long financialYearId,
            @Param("paymentStatusId") Long paymentStatusId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    @EntityGraph(attributePaths = {"status", "financialYear"})
    Optional<Saccos> findByIdAndVisibleTrue(Long id);
}
