package com.jashmore.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@SpringBootTest(
    classes = {
        SpringObjectMapperIntegrationTest.Application.class,
        SpringObjectMapperIntegrationTest.TestConfig.class,
        SpringObjectMapperIntegrationTest.Controller.class,
        SpringObjectMapperIntegrationTest.MessageListener.class,
        QueueListenerConfiguration.class
    },
    webEnvironment = RANDOM_PORT
)
@TestPropertySource(
    properties = {
        // We customise the Jackson ObjectMapper to not fail on unknown properties and we want to make sure that this is maintained when integrating
        // this library
        "spring.jackson.deserialization.fail-on-unknown-properties=false"
    }
)
class SpringObjectMapperIntegrationTest {

    private static final String QUEUE_NAME = "SpringObjectMapperIntegrationTest";

    private static final CountDownLatch MESSAGE_RECEIVED = new CountDownLatch(1);
    private static final AtomicReference<User> MESSAGE_LISTENER_USER = new AtomicReference<>(null);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @SpringBootApplication
    public static class Application {

        public static void main(String[] args) {
            SpringApplication.run(Application.class);
        }
    }

    @RestController
    public static class Controller {

        @PostMapping("/user")
        public String createEntity(@RequestBody User user) {
            return user.username;
        }
    }

    @Service
    public static class MessageListener {

        @QueueListener(QUEUE_NAME)
        public void listener(@Payload final User user) {
            MESSAGE_LISTENER_USER.set(user);
            MESSAGE_RECEIVED.countDown();
        }
    }

    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }
    }

    @Test
    void springObjectMapperShouldNotBeOverwritten() {
        // arrange
        final Map<String, String> payload = new HashMap<>();
        payload.put("username", "user");
        payload.put("email", "example@company.com");

        // act
        final ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/user", payload, String.class);

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void springObjectMapperShouldBeUsedByDefaultInMessageListener() throws JsonProcessingException, InterruptedException {
        // arrange
        final Map<String, String> payload = new HashMap<>();
        payload.put("username", "user");
        payload.put("email", "example@company.com");

        // act
        localSqsAsyncClient.sendMessage(QUEUE_NAME, new ObjectMapper().writeValueAsString(payload));

        // assert
        // If we don't process the message then parsing the User message payload would be a failure and indicates that the default Spring ObjectMapper
        // was not used
        assertThat(MESSAGE_RECEIVED.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(MESSAGE_LISTENER_USER.get()).isEqualTo(new User("user"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {

        private String username;
    }
}
