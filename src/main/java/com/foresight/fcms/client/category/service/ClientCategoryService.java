package com.foresight.fcms.client.category.service;

import com.foresight.fcms.shared.validation.FieldValidationException;
import com.foresight.fcms.client.category.dto.ClientCategoryFilter;
import com.foresight.fcms.client.category.dto.ClientCategoryForm;
import com.foresight.fcms.client.category.dto.ClientCategoryRowDto;
import com.foresight.fcms.client.category.dto.ClientCategoryTotals;
import com.foresight.fcms.client.category.model.ClientCategory;
import com.foresight.fcms.client.category.model.ClientCategoryReportType;
import com.foresight.fcms.client.saccos.service.SaccosStorageService;
import com.foresight.fcms.reference.ClientStatus;
import com.foresight.fcms.reference.ClientStatusRepository;
import com.foresight.fcms.reference.FinancialYear;
import com.foresight.fcms.reference.FinancialYearRepository;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ClientCategoryService {
    private final com.foresight.fcms.audit.service.AuditService audit;
    private final com.foresight.fcms.auth.service.AccessService access;

    private final jakarta.validation.Validator validator;
    private final JdbcTemplate jdbcTemplate;
    private final ClientStatusRepository clientStatusRepository;
    private final FinancialYearRepository financialYearRepository;
    private final SaccosStorageService storageService;
    private final org.springframework.transaction.support.TransactionTemplate writes;

    public ClientCategoryService(
            com.foresight.fcms.audit.service.AuditService audit,
            com.foresight.fcms.auth.service.AccessService access,
            JdbcTemplate jdbcTemplate,
            ClientStatusRepository clientStatusRepository,
            FinancialYearRepository financialYearRepository,
            SaccosStorageService storageService,
            org.springframework.transaction.PlatformTransactionManager transactions, jakarta.validation.Validator validator) {
        this.validator = validator;
        this.audit = audit;
        this.access = access;
        this.jdbcTemplate = jdbcTemplate;
        this.clientStatusRepository = clientStatusRepository;
        this.financialYearRepository = financialYearRepository;
        this.storageService = storageService;
        this.writes = new org.springframework.transaction.support.TransactionTemplate(transactions);
    }

    @Transactional(readOnly = true)
    public ClientCategoryListResult findClients(
            ClientCategory category, ClientCategoryFilter filter, Pageable pageable) {
        access.require("client.read");
        Long yearId = category.isFinancialYearSupported() ? resolveFinancialYearId(filter.getFinancialYearId()) : null;
        String search = searchPattern(filter.normalizedSearch());
        long totalRows = countRows(category, yearId, filter.getPaymentStatusId(), search);
        List<ClientCategoryRowDto> rows =
                queryRows(
                        category,
                        yearId,
                        filter.getPaymentStatusId(),
                        search,
                        " order by " + order(category, filter) + " limit ? offset ?",
                        List.of(pageable.getPageSize(), pageable.getOffset()));
        filter.setFinancialYearId(yearId);
        Page<ClientCategoryRowDto> page = new PageImpl<>(rows, pageable, totalRows);
        return new ClientCategoryListResult(
                page, summarize(category, yearId, filter.getPaymentStatusId(), search), yearId);
    }

    @Transactional(readOnly = true)
    public List<ClientCategoryRowDto> exportClients(
            ClientCategory category, ClientCategoryFilter filter) {
        access.require("client.export");
        Long yearId = category.isFinancialYearSupported() ? resolveFinancialYearId(filter.getFinancialYearId()) : null;
        return queryRows(
                category,
                yearId,
                filter.getPaymentStatusId(),
                searchPattern(filter.normalizedSearch()),
                " order by " + order(category, filter) + " limit 1001",
                List.of());
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ClientCategoryListExport exportClientsWithTotals(
            ClientCategory category, ClientCategoryFilter filter) {
        access.require("client.export");
        Long yearId = category.isFinancialYearSupported() ? resolveFinancialYearId(filter.getFinancialYearId()) : null;
        String search = searchPattern(filter.normalizedSearch());
        List<ClientCategoryRowDto> rows =
                queryRows(
                        category,
                        yearId,
                        filter.getPaymentStatusId(),
                        search,
                        " order by " + order(category, filter) + " limit 1001",
                        List.of());
        return new ClientCategoryListExport(
                rows,
                summarize(category, yearId, filter.getPaymentStatusId(), search),
                yearId,
                financialYearLabel(yearId));
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ClientCategoryReportResult revenueReport(
            ClientCategory category, ClientCategoryFilter filter) {
        access.require("client.read");
        Long yearId = category.isFinancialYearSupported() ? resolveFinancialYearId(filter.getFinancialYearId()) : null;
        String search = searchPattern(filter.normalizedSearch());
        ClientCategoryReportType type =
                ClientCategoryReportType.from(filter.getReportType(), category);
        List<ClientCategoryRowDto> rows =
                queryRows(
                        category,
                        yearId,
                        filter.getPaymentStatusId(),
                        search,
                        " order by " + order(category, filter) + " limit 1001",
                        List.of());
        return new ClientCategoryReportResult(
                category,
                rows,
                summarize(category, yearId, filter.getPaymentStatusId(), search),
                yearId,
                filter.getPaymentStatusId(),
                type,
                financialYearLabel(yearId),
                paymentStatusLabel(filter.getPaymentStatusId()));
    }

    @Transactional(readOnly = true)
    public ClientCategoryRowDto findClient(ClientCategory category, Long id) {
        access.require("client.read");
        List<Object> args = new ArrayList<>();
        String sql = selectRowsSql(category) + " and c.id = ?";
        args.add(id);
        List<ClientCategoryRowDto> rows = jdbcTemplate.query(sql, this::mapRow, args.toArray());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    category.getSingularLabel() + " record was not found");
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public ClientCategoryForm formFor(ClientCategory category, Long id) {
        access.require("client.read");
        var form = ClientCategoryForm.from(findClient(category, id));
        form.setVersion(
                jdbcTemplate.queryForObject(
                        "select version from "
                                + category.getTableName()
                                + " where id=? and visible",
                        Long.class,
                        id));
        return form;
    }

    @Transactional(readOnly = true)
    public ClientCategoryForm newForm(ClientCategory category) {
        access.require("client.read");
        ClientCategoryForm form = new ClientCategoryForm();
        if (category.isFinancialYearSupported()) form.setFinancialYearId(resolveFinancialYearId(null));
        clientStatusRepository
                .findById(2L)
                .ifPresent(status -> form.setPaymentStatusId(status.getId()));
        return form;
    }

    /** Retained for callers creating a fiscal-year client. */
    @Transactional(readOnly=true)
    public ClientCategoryForm newForm() { return newForm(ClientCategory.AMCOS); }

    public Long createClient(
            ClientCategory category, ClientCategoryForm form, MultipartFile contractUpload) {
        access.require("client.write");
        validate(category, form);
        if (form.getExpireDate() != null && form.getExpireDate().isBefore(form.getEntryDate()))
            throw new FieldValidationException("error.dates", "expireDate");
        String stored = storageService.storeContract(contractUpload);
        var registered = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            return writes.execute(status -> {
                storageService.discardOnRollback(stored);
                registered.set(true);
                var actor = access.require("client.write");
                List<ColumnValue> columns = columnValues(category, form, stored, true, true);
                String sql =
                        "insert into "
                                + category.getTableName()
                                + " ("
                                + names(columns)
                                + ", created_at, updated_at) values ("
                                + placeholders(columns.size())
                                + ", current_timestamp, current_timestamp) returning id";
                Long id = jdbcTemplate.queryForObject(sql, Long.class, values(columns).toArray());
                audit.record(
                        actor.id(),
                        "client.create",
                        category.getTableName(),
                        id,
                        "Client created; contract=" + stored);
                return id;
            });
        } catch (RuntimeException e) {
            if (!registered.get()) storageService.discardUncommitted(stored);
            throw e;
        }
    }

    public void updateClient(
            ClientCategory category,
            Long id,
            ClientCategoryForm form,
            MultipartFile contractUpload) {
        access.require("client.write");
        validate(category, form);
        if (form.getExpireDate() != null && form.getExpireDate().isBefore(form.getEntryDate()))
            throw new FieldValidationException("error.dates", "expireDate");
        String stored = storageService.storeContract(contractUpload);
        var registered = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            writes.executeWithoutResult(status -> {
                storageService.discardOnRollback(stored);
                registered.set(true);
                var actor = access.require("client.write");
                var previous =
                        jdbcTemplate.query(
                                "select contract_file" + (category.isOperationalStatusSupported() ? ",operational_status" : ",cast(null as varchar) as operational_status") + " from "
                                        + category.getTableName()
                                        + " where id=? and version=? and visible",
                                (r, n) -> new PriorState(r.getString(1), r.getString(2)),
                                id,
                                form.getVersion());
                if (previous.isEmpty())
                    throw new org.springframework.dao.OptimisticLockingFailureException("stale");
                List<ColumnValue> columns = columnValues(category, form, stored, stored != null, false);
                List<String> assignments = columns.stream().map(c -> c.name() + " = ?").toList();
                String sql =
                        "update "
                                + category.getTableName()
                                + " set "
                                + String.join(", ", assignments)
                                + ", version=version+1, updated_at=current_timestamp where"
                                + " visible and id=? and version=?";
                List<Object> args = values(columns);
                args.add(id);
                args.add(form.getVersion());
                if (jdbcTemplate.update(sql, args.toArray()) != 1)
                    throw new org.springframework.dao.OptimisticLockingFailureException("stale");
                if (category.isOperationalStatusSupported() && !java.util.Objects.equals(previous.getFirst().status(),form.getOperationalStatus()))
                    audit.record(actor.id(),"client.status.update",category.getTableName(),id,"before="+previous.getFirst().status()+"; after="+form.getOperationalStatus());
                audit.record(
                        actor.id(),
                        "client.update",
                        category.getTableName(),
                        id,
                        stored == null
                                ? "Client state updated"
                                : "Contract replaced; before="
                                        + previous.getFirst().contract()
                                        + "; after="
                                        + stored);
            });
        } catch (RuntimeException e) {
            if (!registered.get()) storageService.discardUncommitted(stored);
            throw e;
        }
    }

    @Transactional
    public void hideClient(ClientCategory category, Long id) {
        access.require("client.write");
        int updated =
                jdbcTemplate.update(
                        "update "
                                + category.getTableName()
                                + " set visible = false, updated_at = current_timestamp where"
                                + " visible = true and id = ?",
                        id);
        if (updated == 0) {
            throw new IllegalArgumentException(
                    category.getSingularLabel() + " record was not found");
        }
    }

    @Transactional(readOnly = true)
    public Resource contract(ClientCategory category, Long id) {
        access.require("client.read");
        return storageService.loadContract(findClient(category, id).contractFile());
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

    public List<ClientCategoryReportType> reportTypes(ClientCategory category) {
        access.require("client.read");
        return ClientCategoryReportType.selectableTypes(category);
    }

    private List<ClientCategoryRowDto> queryRows(
            ClientCategory category,
            Long financialYearId,
            Long paymentStatusId,
            String search,
            String suffix,
            List<Object> suffixArgs) {
        List<Object> args = new ArrayList<>();
        String sql =
                selectRowsSql(category)
                        + filters(financialYearId, paymentStatusId, search, args)
                        + suffix;
        args.addAll(suffixArgs);
        var rows = jdbcTemplate.query(sql, this::mapRow, args.toArray());
        if (rows.size() > 1000) throw new IllegalArgumentException("error.narrowReport");
        return rows;
    }

    private long countRows(
            ClientCategory category, Long financialYearId, Long paymentStatusId, String search) {
        List<Object> args = new ArrayList<>();
        String sql =
                "select count(*) from "
                        + category.getTableName()
                        + " c left join client_status cs on cs.id = c.client_status_id where"
                        + " c.visible = true"
                        + filters(financialYearId, paymentStatusId, search, args);
        Number count = jdbcTemplate.queryForObject(sql, Number.class, args.toArray());
        return count == null ? 0 : count.longValue();
    }

    private ClientCategoryTotals summarize(
            ClientCategory category, Long financialYearId, Long paymentStatusId, String search) {
        List<Object> args = new ArrayList<>();
        String sql =
                """
                select
                    count(*) as total_rows,
                    coalesce(sum(c.annual_fee), 0) as annual_fee,
                    coalesce(sum(c.amount), 0) as amount,
                """
                        + " "
                        + sumColumn(category.isWithholdingTaxSupported(), "withholding_tax")
                        + " as withholding_tax,"
                        + " "
                        + sumColumn(category.isCommissionSupported(), "commission")
                        + " as commission,"
                        + " coalesce(sum(c.programmer_cost), 0) as programmer_cost,"
                        + " coalesce(sum(c.system_install_fee), 0) as system_install_fee,"
                        + " coalesce(sum(c.training_fee), 0) as training_fee,"
                        + " coalesce(sum(c.android_fee), 0) as android_fee,"
                        + " coalesce(sum(c.system_reinstall_fee), 0) as system_reinstall_fee,"
                        + " "
                        + sumColumn(category.isBulkSmsSupported(), "bulk_sms_fee")
                        + " as bulk_sms_fee,"
                        + " "
                        + sumColumn(category.isHostingSupported(), "hosting_fee")
                        + " as hosting_fee"
                        + " from "
                        + category.getTableName()
                        + " c left join client_status cs on cs.id = c.client_status_id where"
                        + " c.visible = true"
                        + filters(financialYearId, paymentStatusId, search, args);
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, args.toArray());
        return new ClientCategoryTotals(
                ((Number) row.get("total_rows")).longValue(),
                decimal(row, "annual_fee"),
                decimal(row, "amount"),
                decimal(row, "withholding_tax"),
                decimal(row, "commission"),
                decimal(row, "programmer_cost"),
                decimal(row, "system_install_fee"),
                decimal(row, "training_fee"),
                decimal(row, "android_fee"),
                decimal(row, "system_reinstall_fee"),
                decimal(row, "bulk_sms_fee"),
                decimal(row, "hosting_fee"));
    }

    private String selectRowsSql(ClientCategory category) {
        return """
        select
            c.id,
            c.client_name,
            c.entry_date,
            c.expire_date,
            c.region,
            c.contract_file,
            c.annual_fee,
            c.amount,
        """
                + " "
                + column(category.isWithholdingTaxSupported(), "withholding_tax")
                + " as withholding_tax,"
                + " "
                + column(category.isCommissionSupported(), "commission")
                + " as commission,"
                + """
                    c.programmer_cost,
                    c.system_install_fee,
                    c.training_fee,
                    c.android_fee,
                    c.system_reinstall_fee,
                """
                + " "
                + column(category.isBulkSmsSupported(), "bulk_sms_fee")
                + " as bulk_sms_fee,"
                + " "
                + column(category.isHostingSupported(), "hosting_fee")
                + " as hosting_fee,"
                + """
                    c.financial_year_id,
                    fy.fyear as financial_year,
                    c.client_status_id,
                    cs.name as payment_status
                """
                + (category.isOperationalStatusSupported() ? ", c.operational_status" : ", cast(null as varchar) as operational_status")
                + " from "
                + category.getTableName()
                + " c"
                + " left join financial_year fy on fy.id = c.financial_year_id"
                + " left join client_status cs on cs.id = c.client_status_id"
                + " where c.visible = true";
    }

    private String filters(
            Long financialYearId, Long paymentStatusId, String search, List<Object> args) {
        StringBuilder sql = new StringBuilder();
        if (financialYearId != null) {
            sql.append(" and c.financial_year_id = ?");
            args.add(financialYearId);
        }
        if (paymentStatusId != null) {
            sql.append(" and c.client_status_id = ?");
            args.add(paymentStatusId);
        }
        if (search != null) {
            sql.append(
                    """
                     and (
                        lower(c.client_name) like ?
                        or lower(c.region) like ?
                        or lower(coalesce(cs.name, '')) like ?
                    )
                    """);
            args.add(search);
            args.add(search);
            args.add(search);
        }
        return sql.toString();
    }

    private ClientCategoryRowDto mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ClientCategoryRowDto(
                resultSet.getLong("id"),
                resultSet.getString("client_name"),
                resultSet.getObject("entry_date", java.time.LocalDate.class),
                resultSet.getObject("expire_date", java.time.LocalDate.class),
                resultSet.getString("region"),
                resultSet.getString("contract_file"),
                decimal(resultSet, "annual_fee"),
                decimal(resultSet, "amount"),
                decimal(resultSet, "withholding_tax"),
                decimal(resultSet, "commission"),
                decimal(resultSet, "programmer_cost"),
                decimal(resultSet, "system_install_fee"),
                decimal(resultSet, "training_fee"),
                decimal(resultSet, "android_fee"),
                decimal(resultSet, "system_reinstall_fee"),
                decimal(resultSet, "bulk_sms_fee"),
                decimal(resultSet, "hosting_fee"),
                nullableLong(resultSet, "financial_year_id"),
                resultSet.getString("financial_year"),
                nullableLong(resultSet, "client_status_id"),
                resultSet.getString("payment_status"),
                resultSet.getString("operational_status"));
    }

    private List<ColumnValue> columnValues(
            ClientCategory category,
            ClientCategoryForm form,
            String contractFile,
            boolean includeContract, boolean creating) {
        if (category.isFinancialYearSupported() && (creating || category.isFinancialYearEditable()) && form.getFinancialYearId() == null)
            throw new FieldValidationException("NotNull", "financialYearId");
        if (category.isFinancialYearSupported() && (creating || category.isFinancialYearEditable()) && !financialYearRepository.existsById(form.getFinancialYearId()))
            throw new FieldValidationException("error.invalid", "financialYearId");
        List<ColumnValue> columns = new ArrayList<>();
        columns.add(new ColumnValue("client_name", form.getClientName()));
        columns.add(new ColumnValue("entry_date", form.getEntryDate()));
        columns.add(new ColumnValue("expire_date", form.getExpireDate()));
        columns.add(new ColumnValue("region", form.getRegion()));
        if (includeContract) {
            columns.add(new ColumnValue("contract_file", contractFile));
        }
        columns.add(new ColumnValue("annual_fee", zeroIfNull(form.getAnnualFee())));
        columns.add(new ColumnValue("amount", zeroIfNull(form.getAmount())));
        if (category.isWithholdingTaxSupported()) {
            columns.add(new ColumnValue("withholding_tax", zeroIfNull(form.getWithholdingTax())));
        }
        if (category.isCommissionSupported()) {
            columns.add(new ColumnValue("commission", zeroIfNull(form.getCommission())));
        }
        if (!creating || category.isProgrammerCostOnCreate())
            columns.add(new ColumnValue("programmer_cost", zeroIfNull(form.getProgrammerCost())));
        columns.add(new ColumnValue("system_install_fee", zeroIfNull(form.getSystemInstallFee())));
        columns.add(new ColumnValue("training_fee", zeroIfNull(form.getTrainingFee())));
        columns.add(new ColumnValue("android_fee", zeroIfNull(form.getAndroidFee())));
        columns.add(
                new ColumnValue("system_reinstall_fee", zeroIfNull(form.getSystemReinstallFee())));
        if (category.isBulkSmsSupported()) {
            columns.add(new ColumnValue("bulk_sms_fee", zeroIfNull(form.getBulkSmsFee())));
        }
        if (category.isHostingSupported()) {
            columns.add(new ColumnValue("hosting_fee", zeroIfNull(form.getHostingFee())));
        }
        columns.add(new ColumnValue("client_status_id", form.getPaymentStatusId()));
        if (category.isFinancialYearSupported() && (creating || category.isFinancialYearEditable()))
            columns.add(new ColumnValue("financial_year_id", form.getFinancialYearId()));
        if (category.isOperationalStatusSupported())
            columns.add(new ColumnValue("operational_status", form.getOperationalStatus()));
        return columns;
    }

    private record PriorState(String contract, String status) {}

    private void validate(ClientCategory category, ClientCategoryForm form) {
        var violations=validator.validate(form);
        if(!violations.isEmpty()) throw new FieldValidationException("error.invalid", violations.iterator().next().getPropertyPath().toString());
        if (form.getPaymentStatusId() == null || !clientStatusRepository.existsById(form.getPaymentStatusId()))
            throw new FieldValidationException("error.invalid", "paymentStatusId");
        if (category.isOperationalStatusSupported() &&
                !java.util.Set.of("ACTIVE", "INACTIVE").contains(form.getOperationalStatus() == null ? "" : form.getOperationalStatus()))
            throw new FieldValidationException("error.invalid", "operationalStatus");

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

    private String order(ClientCategory category, ClientCategoryFilter f) {
        String property = switch (f.getSort() == null ? "name" : f.getSort()) {
            case "region" -> "c.region";
            case "entryDate" -> "c.entry_date";
            case "expireDate" -> "c.expire_date";
            case "contract" -> "c.contract_file";
            case "annualFee" -> "c.annual_fee";
            case "amount" -> "c.amount";
            case "programmerCost" -> "c.programmer_cost";
            case "systemInstallFee" -> "c.system_install_fee";
            case "trainingFee" -> "c.training_fee";
            case "androidFee" -> "c.android_fee";
            case "systemReinstallFee" -> "c.system_reinstall_fee";
            case "withholdingTax" -> category.isWithholdingTaxSupported() ? "c.withholding_tax" : "c.client_name";
            case "commission" -> category.isCommissionSupported() ? "c.commission" : "c.client_name";
            case "bulkSmsFee" -> category.isBulkSmsSupported() ? "c.bulk_sms_fee" : "c.client_name";
            case "hostingFee" -> category.isHostingSupported() ? "c.hosting_fee" : "c.client_name";
            case "operationalStatus" -> category.isOperationalStatusSupported() ? "c.operational_status" : "c.client_name";
            case "paymentStatus" -> "cs.name";
            case "financialYear" -> category.isFinancialYearSupported() ? "fy.fyear" : "c.client_name";
            default -> "c.client_name";
        };
        return property + " " + f.getDirection() + ", c.id " + f.getDirection();
    }

    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String column(boolean supported, String name) {
        return supported ? "c." + name : "cast(0 as numeric)";
    }

    private String sumColumn(boolean supported, String name) {
        return supported ? "coalesce(sum(c." + name + "), 0)" : "cast(0 as numeric)";
    }

    private String names(List<ColumnValue> columns) {
        return String.join(", ", columns.stream().map(ColumnValue::name).toList());
    }

    private String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private List<Object> values(List<ColumnValue> columns) {
        return new ArrayList<>(columns.stream().map(ColumnValue::value).toList());
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal decimal(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
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

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record ColumnValue(String name, Object value) {}

    public record ClientCategoryListResult(
            Page<ClientCategoryRowDto> page,
            ClientCategoryTotals totals,
            Long selectedFinancialYearId) {}

    public record ClientCategoryListExport(
            List<ClientCategoryRowDto> rows,
            ClientCategoryTotals totals,
            Long selectedFinancialYearId,
            String selectedFinancialYearLabel) {}

    public record ClientCategoryReportResult(
            ClientCategory category,
            List<ClientCategoryRowDto> rows,
            ClientCategoryTotals totals,
            Long selectedFinancialYearId,
            Long selectedPaymentStatusId,
            ClientCategoryReportType reportType,
            String selectedFinancialYearLabel,
            String selectedPaymentStatusLabel) {}
}
