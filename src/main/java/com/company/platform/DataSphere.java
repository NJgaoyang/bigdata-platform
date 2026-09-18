package com.company.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DataSphere {
    public static void main(String[] args) {
        SpringApplication.run(DataSphere.class, args);
    }
}
