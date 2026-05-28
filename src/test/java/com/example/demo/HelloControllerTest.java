package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that boot the full application on a random port
 * and verify the endpoints respond correctly. These run during the
 * 'Test' stage of the Jenkins pipeline; a failure here fails the build.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() {
        String body = restTemplate.getForObject(
                "http://localhost:" + port + "/health", String.class);
        assertThat(body).contains("UP");
    }

    @Test
    void rootEndpointReturnsGreeting() {
        String body = restTemplate.getForObject(
                "http://localhost:" + port + "/?name=Jenkins", String.class);
        assertThat(body).contains("Hello, Jenkins!");
    }
}
