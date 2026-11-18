package com.foresight.fcms.client.saccos.service;

import com.foresight.fcms.shared.validation.FieldValidationException;
import com.foresight.fcms.client.saccos.dto.SaccosFilter;
import com.foresight.fcms.client.saccos.dto.SaccosForm;
import com.foresight.fcms.client.saccos.dto.SaccosRowDto;
import com.foresight.fcms.client.saccos.dto.SaccosTotals;
import com.foresight.fcms.client.saccos.model.Saccos;
import com.foresight.fcms.client.saccos.model.SaccosReportType;
import com.foresight.fcms.client.saccos.repository.SaccosRepository;
import com.foresight.fcms.reference.ClientStatus;
import com.foresight.fcms.reference.ClientStatusRepository;
import com.foresight.fcms.reference.FinancialYear;
import com.foresight.fcms.reference.FinancialYearRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SaccosService {
    private final com.foresight.fcms.audit.service.AuditService audit;
    private final com.foresight.fcms.auth.service.AccessService access;

    private final SaccosRepository saccosRepository;
    private final ClientStatusRepository clientStatusRepository;
    private final FinancialYearRepository financialYearRepository;
    private final SaccosStorageService storageService;
    private final JdbcTemplate jdbcTemplate;
    private final org.springframework.transaction.support.TransactionTemplate writes;
    private final jakarta.validation.Validator validator;

    public SaccosService(
            com.foresight.fcms.audit.service.AuditService audit,
            com.foresight.fcms.auth.service.AccessService access,
            SaccosRepository saccosRepository,
            ClientStatusRepository clientStatusRepository,
            FinancialYearRepository financialYearRepository,
            SaccosStorageService storageService,
            JdbcTemplate jdbcTemplate,
            org.springframework.transaction.PlatformTransactionManager transactions,
            jakarta.validation.Validator validator) {
        this.audit = audit;
        this.access = access;
        this.saccosRepository = saccosRepository;
        this.clientStatusRepository = clientStatusRepository;
        this.financialYearRepository = financialYearRepository;
        this.storageService = storageService;
        this.jdbcTemplate = jdbcTemplate;
        this.writes = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public SaccosListResult findSaccos(SaccosFilter filter, Pageable pageable) {
        access.require("client.read");
        Long yearId = resolveFinancialYearId(filter.getFinancialYearId());
        String search = searchPattern(filter.normalizedSearch());
        Page<SaccosRowDto> page =
                saccosRepository
                        .search(yearId, filter.getPaymentStatusId(), search, pageable)
                        .map(SaccosRowDto::from);
        SaccosTotals totals = summarize(yearId, filter.getPaymentStatusId(), search);
        filter.setFinancialYearId(yearId);
        return new SaccosListResult(page, totals, yearId);
    }

    public Sort registerSort(SaccosFilter filter) {
        String property = switch (filter.getSort() == null ? "name" : filter.getSort()) {
            case "name" -> "saccosName";
            case "financialYear" -> "financialYear.fyear";
            case "paymentStatus" -> "status.name";
            case "contract" -> "contractFile";
            case "region", "entryDate", "expireDate", "annualFee", "amount", "datePaid",
                    "withholdingTax", "commission", "programmerCost", "systemInstallFee",
                    "trainingFee", "androidFee", "androidCost", "systemReinstallFee",
                    "bulkSmsFee", "hostingFee", "essFee", "cloudBackupFee", "cloudBackupCost",
                    "operationalStatus", "invoiceStatus" -> filter.getSort();
            default -> "saccosName";
        };
        return Sort.by(Sort.Direction.fromString(filter.getDirection()), property)
                .and(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public long countSaccos() {
        access.require("client.read");
        return summarize(null, null, null).totalRows();
    }

    @Transactional(readOnly = true)
    public List<SaccosRowDto> exportSaccos(SaccosFilter filter) {
        access.require("client.export");
        Long yearId = resolveFinancialYearId(filter.getFinancialYearId());
        var rows =
                saccosRepository
                        .findForExport(
                                yearId,
                                filter.getPaymentStatusId(),
                                searchPattern(filter.normalizedSearch()),
                                PageRequest.of(0, 1001, registerSort(filter)))
                        .stream()
                        .map(SaccosRowDto::from)
                        .toList();
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return rows;
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SaccosListExport exportSaccosWithTotals(SaccosFilter filter) {
        access.require("client.export");
        Long yearId = resolveFinancialYearId(filter.getFinancialYearId());
        String search = searchPattern(filter.normalizedSearch());
        List<SaccosRowDto> rows =
                saccosRepository
                        .findForExport(
                                yearId,
                                filter.getPaymentStatusId(),
                                search,
                                PageRequest.of(0, 1001, registerSort(filter)))
                        .stream()
                        .map(SaccosRowDto::from)
                        .toList();
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return new SaccosListExport(
                rows,
                summarize(yearId, filter.getPaymentStatusId(), search),
                yearId,
                financialYearLabel(yearId));
    }

    @Transactional(readOnly = true)
    public SaccosRowDto findSaccos(Long id) {
        access.require("client.read");
        return SaccosRowDto.from(loadEntity(id));
    }

    private Saccos loadEntity(Long id) {
        return saccosRepository
                .findByIdAndVisibleTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("error.missing"));
    }

    @Transactional(readOnly = true)
    public SaccosForm formFor(Long id) {
        access.require("client.read");
        return SaccosForm.from(loadEntity(id));
    }

    @Transactional(readOnly = true)
    public SaccosForm newForm() {
        access.require("client.read");
        SaccosForm form = new SaccosForm();
        form.setFinancialYearId(resolveFinancialYearId(null));
        clientStatusRepository
                .findById(2L)
                .ifPresent(status -> form.setPaymentStatusId(status.getId()));
        return form;
    }

    public Long createSaccos(SaccosForm form, MultipartFile contractFile) {
        access.require("client.write");
        validate(form);
        if (form.getExpireDate() != null && form.getExpireDate().isBefore(form.getEntryDate()))
            throw new FieldValidationException("error.dates", "expireDate");
        String stored = storageService.storeContract(contractFile);
        var registered = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            return writes.execute(status -> {
                storageService.discardOnRollback(stored);
                registered.set(true);
                var actor = access.require("client.write");
                return createRecord(form, stored, actor.id());
            });
        } catch (RuntimeException e) {
            if (!registered.get()) storageService.discardUncommitted(stored);
            throw e;
        }
    }

    private Long createRecord(SaccosForm form, String stored, long actor) {
        Saccos saccos = new Saccos();
        applyForm(saccos, form, stored);
        Long id = saccosRepository.save(saccos).getId();
        audit.record(actor, "client.create", "saccos", id, "Client created; contract=" + stored);
        return id;
    }

    public record ImportReferences(Map<Long, String> years, Map<Long, String> statuses) {
        public ImportReferences {
            years = Map.copyOf(years);
            statuses = Map.copyOf(statuses);
        }
    }

    @Transactional(readOnly = true)
    public ImportReferences importReferences(
            java.util.Set<Long> ids,
            java.util.Set<String> labels,
            java.util.Set<Long> statuses,
            java.util.Set<String> codes) {
        var actor = access.require("client.import");
        actor.require("client.read");
        actor.require("client.write");
        if (ids.size() + labels.size() > 100 || statuses.size() + codes.size() > 100)
            throw new IllegalArgumentException("error.invalid");
        var years = new java.util.HashMap<Long, String>();
        financialYearRepository
                .findByIdInOrFyearIn(ids, labels)
                .forEach(y -> years.put(y.getId(), y.getFyear()));
        var values = new java.util.HashMap<Long, String>();
        clientStatusRepository
                .importChoices(statuses, codes)
                .forEach(s -> values.put(s.getId(), s.getName()));
        return new ImportReferences(years, values);
    }

    /** Lock reviewed references in stable order until the caller's batch transaction completes. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ImportReferences lockImportReferences(
            java.util.Set<Long> years, java.util.Set<Long> statuses) {
        var actor = access.require("client.import");
        actor.require("client.read");
        actor.require("client.write");
        if (years.isEmpty() || years.size() > 100 || statuses.isEmpty() || statuses.size() > 100)
            throw new IllegalArgumentException("error.invalid");
        var yearValues = new java.util.HashMap<Long, String>();
        financialYearRepository
                .lockImportYears(years)
                .forEach(y -> yearValues.put(y.getId(), y.getFyear()));
        var statusValues = new java.util.HashMap<Long, String>();
        clientStatusRepository
                .lockImportStatuses(statuses)
                .forEach(s -> statusValues.put(s.getId(), s.getName()));
        return new ImportReferences(yearValues, statusValues);
    }

    /** Importer owns the surrounding locked-batch transaction; there is no file operation here. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<Long> createImportedSaccos(List<SaccosForm> forms) {
        var actor = access.require("client.import");
        actor.require("client.read");
        actor.require("client.write");
        if (forms == null || forms.isEmpty() || forms.size() > 100)
            throw new IllegalArgumentException("error.invalid");
        for (var f : forms)
            if (f == null
                    || !validator.validate(f).isEmpty()
                    || f.getSaccosName().length() > 255
                    || f.getRegion().length() > 255
                    || !"ACTIVE".equals(f.getOperationalStatus())
                    || !"NO".equals(f.getInvoiceStatus())
                    || f.getExpireDate() != null && f.getExpireDate().isBefore(f.getEntryDate()))
                throw new IllegalArgumentException("error.invalid");
        // Warm the bounded reference sets in this transaction, avoiding per-row database loads.
        var years =
                forms.stream()
                        .map(SaccosForm::getFinancialYearId)
                        .collect(java.util.stream.Collectors.toSet());
        var statuses =
                forms.stream()
                        .map(SaccosForm::getPaymentStatusId)
                        .collect(java.util.stream.Collectors.toSet());
        if (financialYearRepository.findAllById(years).size() != years.size()
                || clientStatusRepository.findAllById(statuses).size() != statuses.size())
            throw new IllegalArgumentException("error.invalid");
        var ids = new ArrayList<Long>();
        for (var f : forms) ids.add(createRecord(f, null, actor.id()));
        return List.copyOf(ids);
    }

    public void updateSaccos(Long id, SaccosForm form, MultipartFile contractFile) {
        access.require("client.write");
        validate(form);
        String stored = storageService.storeContract(contractFile);
        var registered = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            writes.executeWithoutResult(status -> {
                storageService.discardOnRollback(stored);
                registered.set(true);
                var actor = access.require("client.write");
                Saccos saccos = loadEntity(id);
                if (saccos.getVersion() != form.getVersion())
                    throw new org.springframework.dao.OptimisticLockingFailureException("stale");
                String previous = saccos.getContractFile();
                String previousStatus = saccos.getOperationalStatus();
                form.setFinancialYearId(saccos.getFinancialYear() == null ? null : saccos.getFinancialYear().getId());
                applyForm(saccos, form, stored);
                if(!java.util.Objects.equals(previousStatus,form.getOperationalStatus()))
                    audit.record(actor.id(),"client.status.update","saccos",id,"before="+previousStatus+"; after="+form.getOperationalStatus());
                audit.record(
                        actor.id(),
                        "client.update",
                        "saccos",
                        id,
                        stored == null
                                ? "Version " + form.getVersion() + " updated"
                                : "Contract replaced; before=" + previous + "; after=" + stored);
            });
        } catch (RuntimeException e) {
            if (!registered.get()) storageService.discardUncommitted(stored);
            throw e;
        }
    }

    @Transactional
    public void hideSaccos(Long id) {
        access.require("client.write");
        loadEntity(id).hide();
        audit.record(access.current().id(), "client.archive", "saccos", id, "Archived");
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource contract(Long id) {
        access.require("client.read");
        return storageService.loadContract(loadEntity(id).getContractFile());
    }

    @Transactional(readOnly = true)
    public List<ClientStatus> paymentStatuses() {
        access.require("client.read");
        return clientStatusRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<FinancialYear> financialYears() {
        access.require("client.read");
        return financialYearRepository.findAllByOrderByFyearDesc();
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SaccosReportResult revenueReport(
            Long financialYearId, Long paymentStatusId, String reportType) {
        access.require("client.read");
        return revenueReport(financialYearId, paymentStatusId, reportType, null);
    }

    @Transactional(readOnly = true)
    public SaccosReportResult revenueReport(
            Long financialYearId, Long paymentStatusId, String reportType, String search) {
        access.require("client.read");
        Long yearId = resolveFinancialYearId(financialYearId);
        SaccosReportType type = SaccosReportType.from(reportType);
        String searchTerm = searchPattern(search);
        List<SaccosRowDto> rows =
                saccosRepository
                        .findForExport(
                                yearId,
                                paymentStatusId,
                                searchTerm,
                                PageRequest.of(0, 1001, Sort.by("saccosName").ascending()))
                        .stream()
                        .map(SaccosRowDto::from)
                        .toList();
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        SaccosTotals totals = summarize(yearId, paymentStatusId, searchTerm);
        return new SaccosReportResult(
                rows,
                totals,
                yearId,
                paymentStatusId,
                type,
                financialYearLabel(yearId),
                paymentStatusLabel(paymentStatusId));
    }

    @Transactional(readOnly = true)
    public List<SaccosReportType> reportTypes() {
        access.require("client.read");
        return SaccosReportType.selectableTypes();
    }

    private Long resolveFinancialYearId(Long requestedYearId) {
        if (requestedYearId != null) {
            return requestedYearId;
        }
        return financialYearRepository
                .findFirstByOrderByFyearDesc()
                .map(FinancialYear::getId)
                .orElse(null);
    }

    private String financialYearLabel(Long financialYearId) {
        if (financialYearId == null) {
            return "All Years";
        }
        return financialYearRepository
                .findById(financialYearId)
                .map(FinancialYear::getFyear)
                .orElse("All Years");
    }

    private String paymentStatusLabel(Long paymentStatusId) {
        if (paymentStatusId == null) {
            return "All Payment Statuses";
        }
        return clientStatusRepository
                .findById(paymentStatusId)
                .map(ClientStatus::getName)
                .orElse("All Payment Statuses");
    }

    private void validate(SaccosForm form) {
        var violations=validator.validate(form);
        if(!violations.isEmpty()) throw new FieldValidationException("error.invalid", violations.iterator().next().getPropertyPath().toString());
    }

    private void applyForm(Saccos saccos, SaccosForm form, String storedContract) {
        if (form.getExpireDate() != null && form.getExpireDate().isBefore(form.getEntryDate()))
            throw new FieldValidationException("error.dates", "expireDate");
        ClientStatus status =
                clientStatusRepository
                        .findById(form.getPaymentStatusId())
                        .orElseThrow(
                                () -> new FieldValidationException("error.invalid", "paymentStatusId"));
        if (saccos.getId() == null && form.getFinancialYearId() == null) throw new FieldValidationException("NotNull", "financialYearId");
        FinancialYear financialYear = saccos.getId() != null ? saccos.getFinancialYear() :
                financialYearRepository
                        .findById(form.getFinancialYearId())
                        .orElseThrow(
                                () -> new FieldValidationException("error.invalid", "financialYearId"));
        saccos.apply(form, status, financialYear, storedContract);
    }

    private SaccosTotals summarize(Long financialYearId, Long paymentStatusId, String search) {
        String pattern = search;
        StringBuilder sql =
                new StringBuilder(
                        """
                        select
                            count(*) as total_rows,
                            coalesce(sum(annual_fee), 0) as annual_fee,
                            coalesce(sum(amount), 0) as amount,
                            coalesce(sum(withholding_tax), 0) as withholding_tax,
                            coalesce(sum(commission), 0) as commission,
                            coalesce(sum(programmer_cost), 0) as programmer_cost,
                            coalesce(sum(system_install_fee), 0) as system_install_fee,
                            coalesce(sum(training_fee), 0) as training_fee,
                            coalesce(sum(android_fee), 0) as android_fee,
                            coalesce(sum(android_cost), 0) as android_cost,
                            coalesce(sum(system_reinstall_fee), 0) as system_reinstall_fee,
                            coalesce(sum(bulk_sms_fee), 0) as bulk_sms_fee,
                            coalesce(sum(hosting_fee), 0) as hosting_fee,
                            coalesce(sum(ess_fee), 0) as ess_fee,
                            coalesce(sum(cloud_backup_fee), 0) as cloud_backup_fee,
                            coalesce(sum(cloud_backup_cost), 0) as cloud_backup_cost
                        from saccos
                        where visible = true
                        """);
        List<Object> args = new ArrayList<>();
        if (financialYearId != null) {
            sql.append(" and financial_year_id = ?");
            args.add(financialYearId);
        }
        if (paymentStatusId != null) {
            sql.append(" and client_status_id = ?");
            args.add(paymentStatusId);
        }
        if (pattern != null) {
            sql.append(
                    """
                     and (
                        lower(saccos_name) like ?
                        or lower(region) like ?
                        or lower(operational_status) like ?
                        or lower(invoice_status) like ?
                    )
                    """);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        return new SaccosTotals(
                ((Number) row.get("total_rows")).longValue(),
                decimal(row, "annual_fee"),
                decimal(row, "amount"),
                decimal(row, "withholding_tax"),
                decimal(row, "commission"),
                decimal(row, "programmer_cost"),
                decimal(row, "system_install_fee"),
                decimal(row, "training_fee"),
                decimal(row, "android_fee"),
                decimal(row, "android_cost"),
                decimal(row, "system_reinstall_fee"),
                decimal(row, "bulk_sms_fee"),
                decimal(row, "hosting_fee"),
                decimal(row, "ess_fee"),
                decimal(row, "cloud_backup_fee"),
                decimal(row, "cloud_backup_cost"));
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    public record SaccosListResult(
            Page<SaccosRowDto> page, SaccosTotals totals, Long selectedFinancialYearId) {}

    public record SaccosListExport(
            List<SaccosRowDto> rows,
            SaccosTotals totals,
            Long selectedFinancialYearId,
            String selectedFinancialYearLabel) {}

    public record SaccosReportResult(
            List<SaccosRowDto> rows,
            SaccosTotals totals,
            Long selectedFinancialYearId,
            Long selectedPaymentStatusId,
            SaccosReportType reportType,
            String selectedFinancialYearLabel,
            String selectedPaymentStatusLabel) {}
}
