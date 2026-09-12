package com.company.platform.scheduler;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DolphinSchedulerRuntimeConfiguratorTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void createsBigdataPlatformProjectWhenItDoesNotExist() throws Exception {
        AtomicBoolean created = new AtomicBoolean(false);
        AtomicInteger createCalls = new AtomicInteger();
        server = startServer(created, createCalls, false);

        PlatformProperties properties = properties(server.getAddress().getPort());
        DolphinSchedulerRuntimeConfigurator configurator = new DolphinSchedulerRuntimeConfigurator(
                properties, new PlatformStore(), new PasswordCipher(), new ObjectMapper());

        configurator.refresh(true);

        assertEquals("22919517565792", properties.getScheduler().getDolphinscheduler().getProjectCode());
        assertEquals(1, createCalls.get());
    }

    @Test
    void reusesExistingProjectWithoutCreatingAnotherOne() throws Exception {
        AtomicBoolean created = new AtomicBoolean(true);
        AtomicInteger createCalls = new AtomicInteger();
        server = startServer(created, createCalls, true);

        PlatformProperties properties = properties(server.getAddress().getPort());
        DolphinSchedulerRuntimeConfigurator configurator = new DolphinSchedulerRuntimeConfigurator(
                properties, new PlatformStore(), new PasswordCipher(), new ObjectMapper());

        configurator.refresh(true);
        configurator.refresh(true);

        assertEquals("22919517565792", properties.getScheduler().getDolphinscheduler().getProjectCode());
        assertEquals(0, createCalls.get());
    }

    private PlatformProperties properties(int port) {
        PlatformProperties properties = new PlatformProperties();
        var ds = properties.getScheduler().getDolphinscheduler();
        ds.setRealEnabled(true);
        ds.setBaseUrl("http://127.0.0.1:" + port);
        ds.setUsername("admin");
        ds.setPassword("test-password");
        ds.setProjectCode("bigdata-platform");
        return properties;
    }

    private HttpServer startServer(AtomicBoolean created, AtomicInteger createCalls, boolean initiallyExists)
            throws IOException {
        created.set(initiallyExists);
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/dolphinscheduler/login", exchange ->
                respond(exchange, 200, "{\"code\":0,\"data\":{\"sessionId\":\"session-1\"}}"));
        httpServer.createContext("/dolphinscheduler/projects", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (!body.contains("projectName=bigdata-platform")) {
                    respond(exchange, 400, "{\"code\":10001}");
                    return;
                }
                createCalls.incrementAndGet();
                created.set(true);
                respond(exchange, 201, "{\"code\":0,\"data\":null}");
                return;
            }
            String list = created.get()
                    ? "[{\"name\":\"bigdata-platform\",\"code\":22919517565792}]"
                    : "[]";
            respond(exchange, 200, "{\"code\":0,\"data\":{\"totalList\":" + list + "}}");
        });
        httpServer.start();
        return httpServer;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
