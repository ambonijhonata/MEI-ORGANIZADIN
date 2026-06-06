package com.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class MeiOrganizadinApplication extends SpringBootServletInitializer {

    private final Class<?> applicationSource;

    public MeiOrganizadinApplication() {
        super();
        this.applicationSource = MeiOrganizadinApplication.class;
    }

    public static void main(final String[] args) {
        SpringApplication.run(MeiOrganizadinApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(final SpringApplicationBuilder application) {
        return application.sources(this.applicationSource);
    }
}
