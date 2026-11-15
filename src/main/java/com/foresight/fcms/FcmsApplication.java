package com.foresight.fcms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        com.foresight.fcms.upload.UploadScanProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class FcmsApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(FcmsApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(FcmsApplication.class);
    }
}
