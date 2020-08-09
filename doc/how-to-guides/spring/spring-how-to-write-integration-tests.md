# Spring - How to write Integration Tests

This guide provides details on how to write a simple integration test for a spring boot application. Note that writing integration tests through the SQS queue
can be flaky and therefore you should prefer to call your message listener directly in your integration test when testing normal business logic.

For this guide the [Java Dynamic SQS Listener - Spring Integration Test Example](../../../examples/spring-integration-test-example)
module will be used, which is a very simple application that has a single message listener that calls out to a service. The
tests written include:

-   The listener receives a message and was able to be successfully processed
-   The listener receives a message, was not able to be processed and through the re-drive policy succeeded the next time
-   The listener receives a message, was not able to be processed after the number of times defined by the re-drive policy where it ended up in the
    Dead Letter Queue

## Tools

The [elasticmq-sqs-client](../../../util/elasticmq-sqs-client) is a module that will start up an ElasticMQ server and automatically shut it down the end
of the bean's lifecycle.

## Examples

The main example that should be used as a reference is the
[SqsListenerExampleIntegrationTest](../../../examples/spring-integration-test-example/src/test/java/it/com/jashmore/sqs/examples/integrationtests/SqsListenerExampleIntegrationTest.java)
which will test all of those scenarios described above using the methods described below. Otherwise, any of the other integration tests in the spring starter
module would be good examples.

## Steps

1.  Include the `elasticmq-sqs-client` maven dependency in the test scope

    ```xml
    <dependency>
        <groupId>com.jashmore</groupId>
        <artifactId>elasticmq-sqs-client</artifactId>
        <version>${java.dynamic.sqs.listener.version}</version>
        <scope>test</scope>
    </dependency>
    ```

1.  Create a configuration class in your test providing an `ElasticMqSqsAsyncClient` bean. Here you can provide some
    configuration to set up some initial queues in the SQS Server.

    ```java
    @Configuration
    public static class TestConfig {

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(
                ImmutableList.of(
                    SqsQueuesConfig
                        .QueueConfig.builder()
                        .queueName(QUEUE_NAME)
                        .maxReceiveCount(QUEUE_MAX_RECEIVE_COUNT)
                        .visibilityTimeout(VISIBILITY_TIMEOUT_IN_SECONDS)
                        .build()
                )
            );
        }
    }
    ```

1.  Include this Configuration class in the `@SpringBootTest` annotation.

    ```java
    @SpringBootTest(classes = {Application.class, IntegrationTest.TestConfig.class })
    ```

1.  Autowire the `SqsAsyncClient` for this test, in this case we can use the `LocalSqsAsyncClient` interface.

    ```java
    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;
    ```

1.  Write the test using the client, for example sending a message to the queue messages on the queue and assert that they are consumed.

    ```java
    public class MyServiceTest {
        // Configuration defined above...

        @Autowired
        private LocalSqsAsyncClient localSqsAsyncClient;

        @Test
        public void myTest() {
            // arrange
            // your setup code

            // act
            localSqsAsyncClient.sendMessage(QUEUE_NAME, "my message");
            // assert
            // assertions here that the message was processed correctly
        }
    }
    ```

1.  If the Integration Test has multiple tests it is best to purge the queues between tests, and you can do this with a JUnit4 `After` or JUnit5 `AfterEach`

    ```java
    @AfterEach
    void tearDown() throws InterruptedException, ExecutionException, TimeoutException {
        localSqsAsyncClient.purgeAllQueues().get(5, TimeUnit.SECONDS);
    }
    ```
