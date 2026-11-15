package com.foresight.fcms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.i18n.*;

import java.util.Locale;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {
    @Bean
    public LocaleResolver localeResolver() {
        var r = new SessionLocaleResolver();
        r.setDefaultLocale(Locale.ENGLISH);
        return r;
    }

    @Override
    public void addFormatters(org.springframework.format.FormatterRegistry r) {
        var f = new org.springframework.format.datetime.standard.DateTimeFormatterRegistrar();
        f.setUseIsoFormat(true);
        f.registerFormatters(r);
    }

    @Override
    public void addInterceptors(InterceptorRegistry r) {
        var i = new LocaleChangeInterceptor();
        i.setParamName("lang");
        r.addInterceptor(i);
    }
}
