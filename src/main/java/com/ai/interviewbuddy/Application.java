package com.ai.interviewbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;

/**
 * The entry point of the Spring Boot application.
 *
 * Use the @PWA annotation make the application installable on phones, tabrewblets
 * and some desktop browsers.
 *
 */
@SpringBootApplication
// @PWA(name = "InterviewBuddy", shortName = "IB")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
