package com.foresight.fcms.accounting.controller;

import com.foresight.fcms.accounting.dto.VoucherForm;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.client.service.ClientDirectoryService;
import com.foresight.fcms.shared.controller.*;
import com.foresight.fcms.shared.dto.RegisterQuery;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.*;
import java.util.*;

@Controller
@RequestMapping("/accounting/vouchers/{route:receipts|payments|journals}")
@PreAuthorize("hasAuthority('accounting.read')")
public class VoucherController {
    private final VoucherService vouchers;
    private final JournalService journals;
    private final ActivityTransactionService activities;
    private final AccountingService accounts;
    private final PostingMappingService mappings;
    private final ClientDirectoryService clients;
    private final JournalDocumentService documents;
    private final AccountingDocumentPdfService pdf;
    private final SourceBindingService sources;
    private final org.springframework.context.MessageSource messages;
    public VoucherController(VoucherService vouchers,JournalService journals,ActivityTransactionService activities,
            AccountingService accounts,PostingMappingService mappings,ClientDirectoryService clients,
            JournalDocumentService documents,AccountingDocumentPdfService pdf,SourceBindingService sources,org.springframework.context.MessageSource messages) {
        this.vouchers=vouchers;this.journals=journals;this.activities=activities;this.accounts=accounts;
        this.mappings=mappings;this.clients=clients;this.documents=documents;this.pdf=pdf;this.sources=sources;this.messages=messages;
    }
    @InitBinder("voucherForm")
    public void bind(WebDataBinder b) {
        b.setAutoGrowCollectionLimit(50);
        b.setAllowedFields("requestKey","date","party","reference","description","evidenceReference","activityId","moneyAccountId","cashFlow","clientKey",
                "rows[*].mappingId","rows[*].component","rows[*].description","rows[*].amount","rows[*].debitAccountId","rows[*].creditAccountId","rows[*].entries[*].side","rows[*].entries[*].accountId","rows[*].entries[*].amount");
    }
    @ModelAttribute
    public void context(@PathVariable String route,Model m) {
        m.addAttribute("voucherKind",VoucherService.kind(route));m.addAttribute("voucherRoute",route);
        m.addAttribute("baseUrl","/accounting/vouchers/"+route);m.addAttribute("resourceKey","vouchers-"+route);
        m.addAttribute("registerStatuses",List.of("DRAFT","SUBMITTED","POSTED","REJECTED","CANCELLED"));
    }
    @GetMapping
    public String register(@PathVariable String route,@ModelAttribute("query") RegisterQuery q,Model m) {
        m.addAttribute("records",journals.list(q,VoucherService.kind(route)));return "accounting/vouchers/register";
    }
    @GetMapping("/new") @PreAuthorize("hasAuthority('accounting.write')")
    public String create(@PathVariable String route,Model m) {
        var form=new VoucherForm();if(!route.equals("journals"))form.setCashFlow("OPERATING");
        m.addAttribute("voucherForm",form);options(route,form,m);return "accounting/vouchers/form";
    }
    @PostMapping @PreAuthorize("hasAuthority('accounting.write')")
    public String create(@PathVariable String route,@jakarta.validation.Valid @ModelAttribute("voucherForm") VoucherForm form,
            BindingResult errors,@RequestParam(defaultValue="submit") String action,Model m,RedirectAttributes redirect) {
        if(!Set.of("preview","draft","submit").contains(action))throw new IllegalArgumentException("error.invalid");
        if(form.getRows().stream().mapToInt(row->row.getEntries().isEmpty()?2:row.getEntries().size()).sum()>500)errors.reject("voucher.error.entries");
        if(!errors.hasErrors())try {
            if(action.equals("preview"))m.addAttribute("preview",vouchers.preview(VoucherService.kind(route),form));
            else {
                long id=vouchers.create(VoucherService.kind(route),form,action.equals("submit"));
                return ResourceFeedback.success(redirect,"/accounting/vouchers/"+route,action.equals("submit")?"feedback.voucherSubmitted":"feedback.voucherDraftCreated","/accounting/vouchers/"+route+"/"+id);
            }
        }catch(IllegalArgumentException failure){FormErrors.reject(errors,failure,"error.conflict".equals(failure.getMessage())?"error.conflict":"error.invalid");}
         catch(org.springframework.dao.DataAccessException failure){FormErrors.conflict(errors,failure,Map.of("gl_voucher_money_reference","reference"));}
        options(route,form,m);return "accounting/vouchers/form";
    }
    private void options(String route,VoucherForm f,Model m) {
        // Native manual forms start with an explicit debit and credit, also on error recovery.
        if(f.getActivityId()==null)for(var row:f.getRows())if(row.getEntries().isEmpty()) {
            for(String side:List.of("DEBIT","CREDIT")) {
                var entry=new VoucherForm.Entry();entry.setSide(side);entry.setAmount(row.getAmount());
                entry.setAccountId(side.equals("DEBIT")?(route.equals("receipts")?f.getMoneyAccountId():row.getDebitAccountId()):(route.equals("payments")?f.getMoneyAccountId():row.getCreditAccountId()));
                row.getEntries().add(entry);
            }
        }
        var activityChoices=new LinkedHashMap<Long,ActivityTransactionService.Choice>();
        activities.activityChoices("",0).items().forEach(a->activityChoices.put(a.id(),a));
        if(f.getActivityId()!=null)try {var a=activities.activity(f.getActivityId());activityChoices.put(a.id(),new ActivityTransactionService.Choice(a.id(),a.name()));}catch(IllegalArgumentException ignored) { /* Retain field error. */ }
        m.addAttribute("activities",activityChoices.values());
        var ids=new HashSet<Long>();if(f.getMoneyAccountId()!=null)ids.add(f.getMoneyAccountId());
        for(var row:f.getRows()){if(row.getDebitAccountId()!=null)ids.add(row.getDebitAccountId());if(row.getCreditAccountId()!=null)ids.add(row.getCreditAccountId());for(var entry:row.getEntries())if(entry.getAccountId()!=null)ids.add(entry.getAccountId());}
        var choices=new LinkedHashMap<Long,AccountingService.Choice>();accounts.choices("",0,true).items().forEach(a->choices.put(a.id(),a));
        var selectedIds=ids.stream().sorted().limit(501).toList();
        for(int start=0;start<selectedIds.size();start+=50)
            accounts.selected(selectedIds.subList(start,Math.min(start+50,selectedIds.size()))).forEach(a->choices.put(a.id(),a));
        m.addAttribute("accounts",choices.values());
        var selectedMappings=new LinkedHashMap<String,VoucherService.MappingChoice>();
        if(f.getActivityId()!=null)vouchers.choices(VoucherService.kind(route),f.getActivityId(),"",0).items().forEach(a->selectedMappings.put(a.id()+":"+a.component(),a));
        try {
            var mappingIds=f.getRows().stream().map(VoucherForm.Row::getMappingId).filter(Objects::nonNull).distinct().toList();
            for(var mapping:mappings.voucherTemplates(mappingIds).values())for(var rule:mapping.rules())selectedMappings.put(mapping.id()+":"+rule.component(),new VoucherService.MappingChoice(mapping.id(),mapping.name(),rule.component(),rule.debitAccountId(),rule.creditAccountId(),mapping.flow()));
        }catch(IllegalArgumentException ignored) { /* A retired selection remains a field error. */ }
        m.addAttribute("mappingChoices",selectedMappings.values());
        if(!f.getClientKey().isBlank())try{m.addAttribute("selectedClient",clients.voucherClient(f.getClientKey()));}catch(IllegalArgumentException ignored) { /* Retain field error. */ }
    }
    private long check(String route,long id) {
        if(!journals.find(id).kind().equals(VoucherService.kind(route)))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);return id;
    }
    @GetMapping("/{id}")
    public String details(@PathVariable String route,@PathVariable long id,Model m) {
        var doc=documents.document(check(route,id),org.springframework.context.i18n.LocaleContextHolder.getLocale());
        m.addAttribute("actionBase","/accounting/vouchers/"+route+"/"+id);
        m.addAttribute("journal",doc.journal());m.addAttribute("voucherDocument",doc);m.addAttribute("voucherHeader",vouchers.header(id).orElse(null));m.addAttribute("voucherRows",vouchers.rows(id));
        m.addAttribute("sourceBinding",sources.forJournal(id).orElse(null));m.addAttribute("sourceReviewAllowed",sources.canReview(id));m.addAttribute("requestKey",UUID.randomUUID());
        return "accounting/vouchers/details";
    }
    @PostMapping("/{id}/actions/{action}")
    public String action(@PathVariable String route,@PathVariable long id,@PathVariable String action,@RequestParam long version,@RequestParam(defaultValue="") String reason,Model m,RedirectAttributes redirect) {
        check(route,id);
        try{journals.transition(id,version,action,reason);return ResourceFeedback.success(redirect,"/accounting/vouchers/"+route+"/"+id,"feedback.saved",null);}
        catch(IllegalArgumentException|org.springframework.dao.DataAccessException failure){m.addAttribute("actionError",true);return details(route,id,m);}
    }
    @PostMapping("/{id}/reverse") @PreAuthorize("hasAuthority('accounting.reverse')")
    public String reverse(@PathVariable String route,@PathVariable long id,@RequestParam UUID requestKey,@RequestParam java.time.LocalDate date,@RequestParam String reason,Model m,RedirectAttributes redirect) {
        check(route,id);
        try{long saved=journals.reverse(id,requestKey,date,reason);return ResourceFeedback.success(redirect,"/accounting/journals/"+saved,"feedback.saved",null);}
        catch(IllegalArgumentException|org.springframework.dao.DataAccessException failure){m.addAttribute("actionError",true);return details(route,id,m);}
    }
    @GetMapping({"/{id}/pdf","/{id}/print"}) @PreAuthorize("hasAuthority('accounting.report')")
    public ResponseEntity<byte[]> pdf(@PathVariable String route,@PathVariable long id,Locale locale) {
        var document=documents.document(check(route,id),locale);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=fcms-"+route+"-"+id+".pdf").body(pdf.voucher(document,locale));
    }
    public record MappingOption(String id,String name,long debit,long credit,String flow) {}
    @PostMapping("/number") @ResponseBody @PreAuthorize("hasAuthority('accounting.write')")
    public VoucherService.NumberReservation number(@PathVariable String route,@RequestParam UUID requestKey,@RequestParam java.time.LocalDate date) {
        return vouchers.reserve(VoucherService.kind(route),requestKey,date);
    }
    @GetMapping("/choices") @ResponseBody @PreAuthorize("hasAuthority('accounting.write')")
    public com.foresight.fcms.shared.dto.ChoicePage<MappingOption> choices(@PathVariable String route,@RequestParam Long activityId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {
        var result=vouchers.choices(VoucherService.kind(route),activityId,search,page);
        var locale=org.springframework.context.i18n.LocaleContextHolder.getLocale();
        return new com.foresight.fcms.shared.dto.ChoicePage<>(result.items().stream().map(a->new MappingOption(a.id()+":"+a.component(),a.name()+" / "+messages.getMessage("voucher.component."+a.component(),null,locale),a.debitAccountId(),a.creditAccountId(),a.cashFlow())).toList(),result.page(),result.hasNext());
    }
    @GetMapping("/accounts") @ResponseBody @PreAuthorize("hasAuthority('accounting.write')")
    public com.foresight.fcms.shared.dto.ChoicePage<VoucherService.Account> accounts(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String purpose){return vouchers.accounts(search,page,purpose);}
    @GetMapping("/clients") @ResponseBody @PreAuthorize("hasAuthority('accounting.write')")
    public com.foresight.fcms.shared.dto.ChoicePage<ClientDirectoryService.VoucherClient> clients(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return clients.voucherClients(search,page);}
}
