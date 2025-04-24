package com.ai.interviewbuddy.config;

import org.apache.tomcat.websocket.server.WsServerContainer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.websocket.server.ServerContainer;

@Configuration
public class WebSocketBufferConfig {

    @Bean
    public ServletWebServerFactory servletContainer() {
        return new TomcatServletWebServerFactory() {
            @Override
            protected void customizeConnector(org.apache.catalina.connector.Connector connector) {
                super.customizeConnector(connector);
                connector.setProperty("org.apache.tomcat.websocket.binaryBufferSize", "65536");
                connector.setProperty("org.apache.tomcat.websocket.textBufferSize", "65536");
            }
        };
    }
}
