package com.foresight.fcms.shared.controller;

import com.foresight.fcms.shared.validation.FieldValidationException;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.BindingResult;

import java.sql.SQLException;
import java.util.Map;

/** Maps known failures to bound controls; database text is never shown to users. */
public final class FormErrors {
    private FormErrors() {}

    public static void reject(BindingResult errors, IllegalArgumentException failure, String fallback) {
        if (failure instanceof FieldValidationException fieldFailure) {
            var properties = org.springframework.beans.PropertyAccessorFactory.forBeanPropertyAccess(errors.getTarget());
            boolean unbound = false;
            for (String field : fieldFailure.fields()) {
                if (properties.isReadableProperty(field)) errors.rejectValue(field, failure.getMessage());
                else unbound = true;
            }
            // An internally generated journal can report fields absent from a voucher form.
            if (unbound) errors.reject(failure.getMessage());
        } else errors.reject(fallback);
    }

    public static void conflict(BindingResult errors, DataAccessException failure, Map<String,String> constraintFields) {
        if (failure.getMostSpecificCause() instanceof SQLException sql
                && ("23505".equals(sql.getSQLState()) || "23503".equals(sql.getSQLState()))) {
            for (var constraint : constraintFields.entrySet()) {
                if (sql.getMessage() != null && sql.getMessage().contains("\"" + constraint.getKey() + "\"")
                        && org.springframework.beans.PropertyAccessorFactory.forBeanPropertyAccess(errors.getTarget()).isReadableProperty(constraint.getValue())) {
                    errors.rejectValue(constraint.getValue(), "23505".equals(sql.getSQLState()) ? "error.duplicate" : "error.invalid");
                    return;
                }
            }
        }
        errors.reject("error.conflict");
    }
}
