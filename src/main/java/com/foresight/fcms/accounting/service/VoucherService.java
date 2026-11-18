package com.foresight.fcms.accounting.service;

import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.audit.service.AuditService;
import com.foresight.fcms.client.service.ClientDirectoryService;
import com.foresight.fcms.shared.dto.*;
import com.foresight.fcms.shared.validation.FieldValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.util.*;

/** Microfinance pair/split transactions, adapted to the FCMS reviewed ledger. */
@Service @Validated
public class VoucherService {
    private final JdbcTemplate db;
    private final AccessService access;
    private final PostingMappingService mappings;
    private final JournalService journals;
    private final ClientDirectoryService clients;
    private final AuditService audit;
    public VoucherService(JdbcTemplate db, AccessService access, PostingMappingService mappings,
            JournalService journals, ClientDirectoryService clients, AuditService audit) {
        this.db=db;this.access=access;this.mappings=mappings;this.journals=journals;this.clients=clients;this.audit=audit;
    }
    public static String kind(String route) {
        return switch(route) { case "receipts" -> "RECEIPT"; case "payments" -> "PAYMENT"; case "journals" -> "JOURNAL"; default -> throw new IllegalArgumentException("error.invalid"); };
    }
    public static String route(String kind) {
        return switch(kind) { case "RECEIPT" -> "receipts";case "PAYMENT" -> "payments"; default -> "journals"; };
    }
    public record Account(long id,String name,boolean cash) {}
    public record MappingChoice(long id,String name,String component,long debitAccountId,long creditAccountId,String cashFlow) {}
    public record Entry(String side,Account account,BigDecimal amount) {}
    public record Row(int ordinal,Long mappingId,String transactionName,String component,String description,
                      BigDecimal amount,Account debit,Account credit,List<Entry> entries) {
        public List<Entry> postings() { return entries.isEmpty()?List.of(new Entry("DEBIT",debit,amount),new Entry("CREDIT",credit,amount)):entries; }
    }
    public record Preview(List<Row> rows,BigDecimal total,String clientName) {}
    public record Header(long journalId,String number,String party,Long activityId,Long moneyAccountId,String clientKey,String clientName) {}
    public record NumberReservation(String number) {}

    @Transactional
    public NumberReservation reserve(String kind,UUID request,java.time.LocalDate date) {
        var actor=access.require("accounting.write");
        if(request==null || date==null || date.getYear()<1900 || date.getYear()>2200 || !Set.of("RECEIPT","PAYMENT","JOURNAL").contains(kind))throw new IllegalArgumentException("error.invalid");
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","FCMS_GL/"+actor.id()+"/"+request);
        var old=db.queryForList("select * from gl_voucher_number_reservations where actor_id=? and request_key=? for update",actor.id(),request);
        if(!old.isEmpty()) {
            var reservation=old.getFirst();
            if(!kind.equals(reservation.get("kind")))throw new IllegalArgumentException("error.conflict");
            if(date.equals(((java.sql.Date)reservation.get("effective_date")).toLocalDate()))return new NumberReservation((String)reservation.get("number"));
            if(reservation.get("journal_id")!=null)throw new IllegalArgumentException("error.conflict");
        }
        long sequence=db.queryForObject("select nextval('gl_voucher_number_sequence')",Long.class);
        String number=(kind.equals("RECEIPT")?"RV":kind.equals("PAYMENT")?"PV":"JV")+"-"+date.getYear()+"-"+String.format(Locale.ROOT,"%06d",sequence);
        db.update("insert into gl_voucher_number_reservations(actor_id,request_key,kind,effective_date,number) values (?,?,?,?,?) on conflict(actor_id,request_key) do update set effective_date=excluded.effective_date,number=excluded.number",actor.id(),request,kind,date,number);
        return new NumberReservation(number);
    }

    @Transactional(readOnly=true)
    public Optional<Header> header(long id) {
        access.require("accounting.read");
        return db.query("select * from gl_vouchers where journal_id=?",(r,n)->new Header(r.getLong("journal_id"),r.getString("number"),r.getString("party"),r.getObject("activity_id",Long.class),r.getObject("money_account_id",Long.class),r.getString("client_key"),r.getString("client_name")),id).stream().findFirst();
    }
    @Transactional(readOnly=true)
    public List<Row> rows(long id) {
        access.require("accounting.read");
        var postings=new HashMap<Integer,List<Entry>>();
        db.query("select p.ordinal,p.side,p.amount,p.account_id,l.account_code||' '||l.account_name name from gl_voucher_postings p join gl_lines l on l.journal_id=p.journal_id and l.line_number=p.line_number where p.journal_id=? order by p.ordinal,p.entry_no limit 500",
            (org.springframework.jdbc.core.RowCallbackHandler)r->postings.computeIfAbsent(r.getInt("ordinal"),key->new ArrayList<>()).add(new Entry(r.getString("side"),new Account(r.getLong("account_id"),r.getString("name"),false),r.getBigDecimal("amount"))),id);
        return db.query("select * from gl_voucher_rows where journal_id=? order by ordinal limit 50",
            (r,n)->new Row(r.getInt("ordinal"),r.getObject("mapping_id",Long.class),r.getString("transaction_name"),r.getString("component"),r.getString("description"),r.getBigDecimal("amount"),null,null,List.copyOf(postings.getOrDefault(r.getInt("ordinal"),List.of()))),id);
    }

    @Transactional(readOnly=true)
    public ChoicePage<MappingChoice> choices(String kind,Long activity,String search,int page) {
        access.require("accounting.write");
        if(!Set.of("RECEIPT","PAYMENT","JOURNAL").contains(kind) || activity==null || search==null || search.length()>100 || page<0 || page>1000)throw new IllegalArgumentException("error.invalid");
        var q=new RegisterQuery();q.setSearch(search);
        return ChoicePage.from(db.query("select m.id,t.name,r.component,r.debit_account_id,r.credit_account_id,m.cash_flow from gl_posting_mappings m join gl_transactions t on t.id=m.transaction_id join gl_activities a on a.id=t.activity_id join gl_mapping_rules r on r.mapping_id=m.id where a.id=? and a.active and t.active and m.state='APPROVED' and m.voucher_kind=? and lower(t.code||' '||t.name) like ? escape '!' order by t.code,t.id,r.component limit 26 offset ?",
                (r,n)->new MappingChoice(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),r.getLong(5),r.getString(6)),activity,kind,q.pattern(),page*25),page);
    }
    @Transactional(readOnly=true)
    public ChoicePage<Account> accounts(String search,int page,String purpose) {
        access.require("accounting.write");
        if(search==null || search.length()>100 || page<0 || page>1000 || !Set.of("","MONEY","OTHER").contains(purpose))throw new IllegalArgumentException("error.invalid");
        var q=new RegisterQuery();q.setSearch(search);
        return ChoicePage.from(db.query("select id,code||' '||name,cash_account from gl_accounts where active and depth=4 and (?='' or cash_account=(?='MONEY')) and lower(code||' '||name) like ? escape '!' order by code,id limit 26 offset ?",
                (r,n)->new Account(r.getLong(1),r.getString(2),r.getBoolean(3)),purpose,purpose,q.pattern(),page*25),page);
    }
    @Transactional
    public Preview preview(String kind,@jakarta.validation.Valid VoucherForm form) {
        access.require("accounting.write");
        return resolve(kind,form);
    }
    @Transactional
    public long create(String kind,@jakarta.validation.Valid VoucherForm form,boolean submit) {
        var actor=access.require("accounting.write");
        String hash=fingerprint(kind,form,submit);
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","FCMS_GL/"+actor.id()+"/"+form.getRequestKey());
        var previous=db.queryForList("select j.id,v.payload_hash from gl_journals j left join gl_vouchers v on v.journal_id=j.id where j.maker_id=? and j.request_key=?",actor.id(),form.getRequestKey());
        if(!previous.isEmpty()) {
            if(!hash.equals(previous.getFirst().get("payload_hash")))throw new IllegalArgumentException("error.conflict");
            return ((Number)previous.getFirst().get("id")).longValue();
        }
        var preview=resolve(kind,form);
        var journal=new JournalForm();journal.setKind(kind);journal.setDate(form.getDate());journal.setReference(form.getReference());
        journal.setDescription(form.getDescription().isBlank()?preview.rows().getFirst().description():form.getDescription());
        journal.setEvidenceReference(form.getEvidenceReference().isBlank()?form.getReference():form.getEvidenceReference());
        journal.setCashFlow(form.getCashFlow());journal.setRequestKey(form.getRequestKey());
        var lines=new ArrayList<JournalLineForm>();
        for(var row:preview.rows()) for(var posting:row.postings()) {
            var line=new JournalLineForm();line.setAccountId(posting.account().id());line.setDescription(row.description());
            if(posting.side().equals("DEBIT"))line.setDebit(posting.amount());else line.setCredit(posting.amount());lines.add(line);
        }
        journal.setLines(lines);
        long id=journals.createDraft(journal);
        String number=reserve(kind,form.getRequestKey(),form.getDate()).number();
        db.update("update gl_voucher_number_reservations set journal_id=? where actor_id=? and request_key=? and journal_id is null",id,actor.id(),form.getRequestKey());
        Long money=kind.equals("JOURNAL")?null:preview.rows().stream().flatMap(row->row.postings().stream()).filter(e->e.side().equals(kind.equals("RECEIPT")?"DEBIT":"CREDIT")).map(e->e.account().id()).findFirst().orElseThrow();
        db.update("insert into gl_vouchers(journal_id,number,party,activity_id,money_account_id,client_key,client_name,payload_hash,row_count,total,reference) values (?,?,?,?,?,?,?,?,?,?,?)",id,number,form.getParty().trim(),form.getActivityId(),money,form.getClientKey(),preview.clientName(),hash,preview.rows().size(),preview.total(),form.getReference().trim());
        int lineNumber=0;
        var capturedRows=new ArrayList<Object[]>();var entries=new ArrayList<Object[]>();var references=new TreeSet<Long>();
        for(var row:preview.rows()) {
            capturedRows.add(new Object[]{id,row.ordinal(),row.mappingId(),row.component(),row.transactionName(),row.description(),row.amount(),row.debit()==null?null:row.debit().id(),row.credit()==null?null:row.credit().id()});
            int entryNo=0;
            for(var posting:row.postings()) {
                entries.add(new Object[]{id,row.ordinal(),++entryNo,++lineNumber,posting.side(),posting.account().id(),posting.amount()});
                if(!kind.equals("JOURNAL") && posting.account().cash())references.add(posting.account().id());
            }
        }
        db.batchUpdate("insert into gl_voucher_rows(journal_id,ordinal,mapping_id,component,transaction_name,description,amount,debit_account_id,credit_account_id) values (?,?,?,?,?,?,?,?,?)",capturedRows);
        db.batchUpdate("insert into gl_voucher_entries(journal_id,ordinal,entry_no,line_number,side,account_id,amount) values (?,?,?,?,?,?,?)",entries);
        if(!references.isEmpty())db.batchUpdate("insert into gl_voucher_money_references(journal_id,account_id,reference) values (?,?,?)",references.stream().map(account->new Object[]{id,account,form.getReference().trim()}).toList());
        if(submit)journals.transition(id,0,"submit","");
        audit.record(actor.id(),submit?"accounting.voucher.submit":"accounting.voucher.draft","gl_journals",id,number+"; "+preview.rows().size()+" transactions");
        return id;
    }
    private record Input(String side,Long accountId,BigDecimal amount,String field) {}
    private Preview resolve(String kind,VoucherForm f) {
        if(!Set.of("RECEIPT","PAYMENT","JOURNAL").contains(kind))throw new IllegalArgumentException("error.invalid");
        if(!kind.equals("JOURNAL") && f.getParty().isBlank())fail("party","voucher.error.party");
        boolean needsMoney=!kind.equals("JOURNAL") && f.getActivityId()==null && f.getRows().stream().anyMatch(r->r.getEntries().isEmpty());
        if(needsMoney && f.getMoneyAccountId()==null)fail("moneyAccountId","accounting.error.accounts");
        if(kind.equals("JOURNAL") && f.getMoneyAccountId()!=null)fail("moneyAccountId","error.invalid");
        String client="";
        if(!f.getClientKey().isBlank())try {client=clients.voucherClient(f.getClientKey()).name();}catch(IllegalArgumentException e){fail("clientKey","error.invalid");}
        if(f.getActivityId()!=null && db.queryForList("select id from gl_activities where id=? and active for share",Long.class,f.getActivityId()).isEmpty())fail("activityId","config.error.inactive");
        Map<Long,PostingMappingService.VoucherTemplate> selected;
        try {selected=mappings.voucherTemplates(f.getRows().stream().map(VoucherForm.Row::getMappingId).filter(Objects::nonNull).distinct().toList());}
        catch(IllegalArgumentException e){throw new FieldValidationException("gl.source.mappingChanged","rows");}
        var plans=new ArrayList<List<Input>>();var names=new ArrayList<String>();var ids=new TreeSet<Long>();int entryCount=0;
        for(int i=0;i<f.getRows().size();i++) {
            var row=f.getRows().get(i);BigDecimal rowAmount=amount(row,i);String field="rows["+i+"]",name="";var inputs=new ArrayList<Input>();
            if(!row.getEntries().isEmpty()) {
                if(f.getActivityId()!=null || row.getMappingId()!=null || !"TOTAL".equals(row.getComponent()))fail(field+".entries","voucher.error.mapping");
                if(row.getEntries().size()<2)fail(field+".entries","voucher.error.entries");
                for(int e=0;e<row.getEntries().size();e++){var input=row.getEntries().get(e);inputs.add(new Input(input.getSide(),input.getAccountId(),input.getAmount(),field+".entries["+e+"]"));}
            } else {
                Long debit=row.getDebitAccountId(),credit=row.getCreditAccountId();
                if(f.getActivityId()!=null) {
                    var mapping=selected.get(row.getMappingId());
                    if(mapping==null || !mapping.kind().equals(kind) || !Objects.equals(mapping.activityId(),f.getActivityId()))fail(field+".mappingId","voucher.error.mapping");
                    var rule=mapping.rules().stream().filter(r->r.component().equals(row.getComponent())).findFirst().orElse(null);
                    if(rule==null)fail(field+".mappingId","gl.source.mappingChanged");
                    debit=rule.debitAccountId();credit=rule.creditAccountId();name=mapping.name();
                    if(!Set.of("NONE","INTERNAL").contains(mapping.flow()) && !f.getCashFlow().equals(mapping.flow()))fail("cashFlow","accounting.error.mappingFlow");
                } else if(row.getMappingId()!=null)fail(field+".mappingId","voucher.error.mapping");
                if(kind.equals("RECEIPT")){if(f.getActivityId()==null)debit=f.getMoneyAccountId();else if(f.getMoneyAccountId()!=null && !Objects.equals(debit,f.getMoneyAccountId()))fail("moneyAccountId","accounting.error.mappingAccounts");}
                if(kind.equals("PAYMENT")){if(f.getActivityId()==null)credit=f.getMoneyAccountId();else if(f.getMoneyAccountId()!=null && !Objects.equals(credit,f.getMoneyAccountId()))fail("moneyAccountId","accounting.error.mappingAccounts");}
                String visible=f.getActivityId()!=null?"mappingId":kind.equals("RECEIPT")?"creditAccountId":kind.equals("PAYMENT")?"debitAccountId":debit==null?"debitAccountId":"creditAccountId";
                if(debit==null || credit==null || debit.equals(credit))fail(field+"."+visible,"accounting.error.mappingAccounts");
                inputs.add(new Input("DEBIT",debit,rowAmount,field+"."+(f.getActivityId()!=null?"mappingId":kind.equals("RECEIPT")?"moneyAccountId":"debitAccountId")));
                inputs.add(new Input("CREDIT",credit,rowAmount,field+"."+(f.getActivityId()!=null?"mappingId":kind.equals("PAYMENT")?"moneyAccountId":"creditAccountId")));
            }
            entryCount+=inputs.size();if(entryCount>500)fail(field+".entries","voucher.error.entries");
            inputs.forEach(input->ids.add(input.accountId()));plans.add(inputs);names.add(name);
        }
        var accounts=db.query("select id,code||' '||name,cash_account from gl_accounts where id=any(?) and active and depth=4 order by id for share",(r,n)->new Account(r.getLong(1),r.getString(2),r.getBoolean(3)),(Object)ids.toArray(Long[]::new));
        var byId=new HashMap<Long,Account>();accounts.forEach(a->byId.put(a.id(),a));
        if(needsMoney && (!byId.containsKey(f.getMoneyAccountId()) || !byId.get(f.getMoneyAccountId()).cash()))fail("moneyAccountId","accounting.error.accounts");
        var result=new ArrayList<Row>();BigDecimal total=BigDecimal.ZERO,cashDelta=BigDecimal.ZERO;boolean hasCash=false;
        for(int i=0;i<plans.size();i++) {
            var row=f.getRows().get(i);BigDecimal rowAmount=amount(row,i);var entries=new ArrayList<Entry>();var debits=new HashSet<Long>();var credits=new HashSet<Long>();BigDecimal debitTotal=BigDecimal.ZERO,creditTotal=BigDecimal.ZERO;
            for(var input:plans.get(i)) {
                boolean debit=input.side().equals("DEBIT");var account=byId.get(input.accountId());
                String path=input.field()+(row.getEntries().isEmpty()?"":".accountId");if(path.endsWith(".moneyAccountId"))path="moneyAccountId";
                if(account==null || !(debit?debits:credits).add(account.id()))fail(path,"accounting.error.accounts");
                if(!kind.equals("JOURNAL") && account.cash()!=(kind.equals("RECEIPT")?debit:!debit))fail(path,"accounting.error.mappingDirection");
                var amount=input.amount().setScale(2,java.math.RoundingMode.UNNECESSARY);
                if(debit)debitTotal=debitTotal.add(amount);else creditTotal=creditTotal.add(amount);
                if(account.cash()){hasCash=true;cashDelta=cashDelta.add(debit?amount:amount.negate());}
                entries.add(new Entry(input.side(),account,amount));
            }
            if(!Collections.disjoint(debits,credits))fail("rows["+i+"].entries","accounting.error.mappingAccounts");
            if(debitTotal.compareTo(rowAmount)!=0 || creditTotal.compareTo(rowAmount)!=0)fail("rows["+i+"].entries","accounting.error.balance");
            String description=row.getDescription().trim();
            if(description.isBlank())description=names.get(i);
            if(description.isBlank())fail("rows["+i+"].description","form.required");
            boolean split=!row.getEntries().isEmpty();
            result.add(new Row(i+1,row.getMappingId(),names.get(i),row.getComponent(),description,rowAmount,split?null:entries.get(0).account(),split?null:entries.get(1).account(),split?List.copyOf(entries):List.of()));
            total=total.add(rowAmount);
        }
        if(cashDelta.signum()!=0?!Set.of("OPERATING","INVESTING","FINANCING").contains(f.getCashFlow()):!f.getCashFlow().equals(hasCash?"INTERNAL":"NONE"))fail("cashFlow","accounting.error.mappingFlow");
        if(total.compareTo(new BigDecimal("999999999999.99"))>0)fail("rows","voucher.error.total");
        return new Preview(List.copyOf(result),total,client);
    }
    private static BigDecimal amount(VoucherForm.Row row,int index) {
        if(row.getEntries().isEmpty()) {
            if(row.getAmount()==null)fail("rows["+index+"].amount","form.required");
            return row.getAmount().setScale(2,java.math.RoundingMode.UNNECESSARY);
        }
        BigDecimal value=row.getEntries().stream().filter(e->"DEBIT".equals(e.getSide())).map(VoucherForm.Entry::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(value.signum()<=0)fail("rows["+index+"].entries","accounting.error.balance");
        if(value.compareTo(new BigDecimal("999999999999.99"))>0)fail("rows["+index+"].entries","voucher.error.total");
        return value.setScale(2,java.math.RoundingMode.UNNECESSARY);
    }
    private static void fail(String field,String code){throw new FieldValidationException(code,field);}
    private static String fingerprint(String kind,VoucherForm f,boolean submit) {
        try {
            var bytes=new java.io.ByteArrayOutputStream();
            try(var out=new java.io.DataOutputStream(bytes)) {
                for(Object value:List.of(kind,f.getDate(),f.getParty(),f.getReference(),f.getDescription(),f.getEvidenceReference(),f.getClientKey(),String.valueOf(f.getActivityId()),String.valueOf(f.getMoneyAccountId()),f.getCashFlow(),submit))out.writeUTF(String.valueOf(value));
                int index=0;
                for(var row:f.getRows()){out.writeUTF(String.valueOf(row.getMappingId()));out.writeUTF(row.getComponent());out.writeUTF(row.getDescription());out.writeUTF(amount(row,index++).toPlainString());out.writeUTF(String.valueOf(row.getDebitAccountId()));out.writeUTF(String.valueOf(row.getCreditAccountId()));if(!row.getEntries().isEmpty()){out.writeUTF("SPLIT_V1");out.writeInt(row.getEntries().size());for(var entry:row.getEntries()){out.writeUTF(entry.getSide());out.writeLong(entry.getAccountId());out.writeUTF(entry.getAmount().setScale(2).toPlainString());}}}
            }
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
