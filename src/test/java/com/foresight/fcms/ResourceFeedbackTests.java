package com.foresight.fcms;

import com.foresight.fcms.auth.dto.UserForm;
import com.foresight.fcms.shared.controller.FormErrors;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.shared.validation.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ResourceFeedbackTests {
    @Test void successIsServerOwnedAndBoundToAUniqueDestinationGet() {
        var first = new RedirectAttributesModelMap();
        var second = new RedirectAttributesModelMap();
        String redirect = ResourceFeedback.success(first,"/register?kind=RECEIPT","feedback.voucherDraftCreated","/resource/42");
        assertTrue(redirect.matches("redirect:/register\\?kind=RECEIPT&_notice=[0-9a-f-]{36}"));
        assertNotEquals(redirect,ResourceFeedback.success(second,"/register?kind=RECEIPT","feedback.voucherDraftCreated","/resource/42"));
        var notice = (ResourceFeedback.Notice) first.getFlashAttributes().get("resourceNotice");
        assertEquals("success",notice.type());assertEquals("feedback.voucherDraftCreated",notice.messageKey());
        assertEquals("/resource/42",notice.resourceUrl());assertTrue(first.isEmpty());
    }
    @Test void domainFailuresKeepExactFieldsAndUnknownFailuresUseOnlySafeFallback() {
        var errors = new BeanPropertyBindingResult(new UserForm(),"form");
        FormErrors.reject(errors,new FieldValidationException("form.required","firstName","lastName"),"error.invalid");
        assertEquals("form.required",errors.getFieldError("firstName").getCode());
        assertEquals("form.required",errors.getFieldError("lastName").getCode());
        FormErrors.reject(errors,new IllegalArgumentException("Sensitive internal details"),"error.invalid");
        assertEquals("error.invalid",errors.getGlobalError().getCode());
        assertNull(errors.getGlobalError().getDefaultMessage());
    }
    @Test void onlyAllowlistedUniqueConstraintsBecomeFieldErrors() {
        var errors = new BeanPropertyBindingResult(new UserForm(),"form");
        var failure = new DataIntegrityViolationException("Sensitive SQL",new SQLException("duplicate key violates unique constraint \"users_email_lower_uq\"","23505"));
        FormErrors.conflict(errors,failure,Map.of("users_email_lower_uq","email"));
        assertEquals("error.duplicate",errors.getFieldError("email").getCode());assertFalse(errors.hasGlobalErrors());
        var unknown = new BeanPropertyBindingResult(new UserForm(),"form");
        FormErrors.conflict(unknown,failure,Map.of("another_constraint","email"));
        assertEquals("error.conflict",unknown.getGlobalError().getCode());assertFalse(unknown.hasFieldErrors());
    }
    @Test void anInternalCommandFieldMissingFromThePublicFormRemainsRecoverable() {
        var errors = new BeanPropertyBindingResult(new UserForm(),"form");
        FormErrors.reject(errors,new FieldValidationException("accounting.error.accounts","lines[0].accountId"),"error.invalid");
        assertEquals("accounting.error.accounts",errors.getGlobalError().getCode());
        assertFalse(errors.hasFieldErrors());
    }
    @Test void missingSqlStateUsesTheSafeFormLevelFallback() {
        var errors = new BeanPropertyBindingResult(new UserForm(),"form");
        FormErrors.conflict(errors,new DataIntegrityViolationException("internal",new SQLException("internal")),Map.of("known","email"));
        assertEquals("error.conflict",errors.getGlobalError().getCode());
    }
}
