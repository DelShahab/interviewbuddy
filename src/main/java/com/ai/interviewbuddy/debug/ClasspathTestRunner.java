package com.ai.interviewbuddy.debug;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class ClasspathTestRunner {

    @PostConstruct
    public void verifyCredentialFile() {
        String path = "credentials/interview-credentials.json";
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);

        if (in != null) {
            System.out.println("✅ Found credentials on classpath: " + path);
        } else {
            System.err.println("❌ Credentials NOT found in classpath: " + path);
        }
    }
}
