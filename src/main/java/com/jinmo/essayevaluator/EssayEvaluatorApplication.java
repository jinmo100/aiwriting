package com.jinmo.essayevaluator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EssayEvaluatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EssayEvaluatorApplication.class, args);
    }

}
