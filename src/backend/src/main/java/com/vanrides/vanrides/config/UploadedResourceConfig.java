package com.vanrides.vanrides.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class UploadedResourceConfig implements WebMvcConfigurer {

    private final String uploadLocation;

    public UploadedResourceConfig(@Value("${vanrides.uploads.dir:uploads}") String uploadRoot) {
        this.uploadLocation = Path.of(uploadRoot).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation.endsWith("/") ? uploadLocation : uploadLocation + "/");
    }
}
