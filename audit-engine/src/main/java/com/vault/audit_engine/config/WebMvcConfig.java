package com.vault.audit_engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Automatically forward anyone visiting localhost:8080/ straight to the landing index page
        registry.addViewController("/").setViewName("forward:/landing/index.html");
    }
}
