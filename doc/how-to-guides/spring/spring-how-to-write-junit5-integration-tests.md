# Spring - How to write Integration Tests
This guide provides details on how to write a simple integration test for a spring boot application. Note that writing integration tests through the SQS queue
can be flaky and therefore you should prefer to call your message listener directly in your integration test when testing normal business logic.

For this guide the [Java Dynamic SQS Listener - Spring Integration Test Example](../../../examples/spring-integration-test-example)
module will be used, which is a very simple application that has a single queue listener that calls out to a service when a message is retrieved. The
tests written includes test on:
 - A message was received and able to be successfully processed
 - A message was received, was not able to be processed and through the re-drive policy succeeded the next time
 - A message was received, was not able to be processed after the number of times defined by the re-drive policy where it ended up in the Dead Letter Queue

#### Tools
The tools that will be used to help this are provided by the `local-sqs-test-utils-junit5`:
1. [LocalSqsExtension](../../../util/local-sqs-test-utils-junit5/src/main/java/com/jashmore/sqs/test/LocalSqsExtension.java): extension that will be able
to set up the Local SQS Queue before the tests start, purge between each test and shutdown the server after all the tests have run.

### Examples
The main example that should be used as a reference is the
[SqsListenerExampleIntegrationTest](../../../examples/spring-integration-test-example/src/test/java/it/com/jashmore/sqs/examples/integrationtests/SqsListenerExampleIntegrationTest.java)
which will test all of those scenarios described above using the methods described below. Otherwise any of the other integration tests in the spring starter
module would be good examples.

## Steps
1. Include the local-sqs-test-utils maven dependency in the test scope
    ```xml
    <dependency>
        <groupId>com.jashmore</groupId>
        <artifactId>local-sqs-test-utils-junit5</artifactId>
        <version>${java.dynamic.sqs.listener.version}</version>
        <scope>test</scope> 
    </dependency>
    ```
1. Add the [LocalSqsRule](../../../util/local-sqs-test-utils/src/main/java/com/jashmore/sqs/test/LocalSqsRule.java) as a `ClassRule` to your integration test.
    ```java
    @RegisterExtension
    static final LocalSqsExtension LOCAL_SQS = new LocalSqsExtension(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME)
                    .maxReceiveCount(QUEUE_MAX_RECEIVE_COUNT)
                    .visibilityTimeout(VISIBILITY_TIMEOUT_IN_SECONDS)
                    .build()
    ));
    ```
1. Add the `LocalSqsAsyncClient` to the context of the spring application by defining a `@Configuration` in your application.
    ```java
    @Configuration
    public static class TestConfiguration {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS.getLocalAmazonSqsAsync();
        }
    }
    ```
1. Make sure this Configuration is being included, e.g. by adding it as a class in the `@SpringBootTest` annotation.
    ```java
    @SpringBootTest(classes = {Application.class, IntegrationTest.TestConfiguration.class })
    ```
1. Write a test that uses the [LocalSqsAsyncClient](../../../util/local-amazon-sqs/src/main/java/com/jashmore/sqs/util/LocalSqsAsyncClient.java) to send
messages on the queue and assert that they are consumed.
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
            localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "my message");
         
             // assert
             // assertions here that the message was processed correctly
        }
 
    }
    ```
