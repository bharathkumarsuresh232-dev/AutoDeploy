package com.sterling.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main Spring Boot Application Class for Sterling Auto Deploy Service
 * 
 * This application provides REST APIs for automated deployment management
 * with support for continuous integration and deployment automation.
 * 
 * @author Sterling Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.sterling.app"})
public class SterlingAutoDeployApplication {

    public static void main(String[] args) {
        SpringApplication.run(SterlingAutoDeployApplication.class, args);
    }
}