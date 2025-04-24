package com.ai.interviewbuddy.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import org.springframework.context.annotation.Configuration;

@Push
@Configuration
public class AppShellConfig implements AppShellConfigurator {
    // This is now the global push config
}