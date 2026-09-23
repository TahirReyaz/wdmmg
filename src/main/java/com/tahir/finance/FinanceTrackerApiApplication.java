package com.tahir.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinanceTrackerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceTrackerApiApplication.class, args);
    }
}
