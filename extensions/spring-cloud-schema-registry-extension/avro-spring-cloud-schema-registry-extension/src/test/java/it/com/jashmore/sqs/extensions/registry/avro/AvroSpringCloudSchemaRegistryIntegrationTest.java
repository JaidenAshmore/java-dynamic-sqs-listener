package it.com.jashmore.sqs.extensions.registry.avro;

import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.elasticmq.ElasticMqSqsAsyncClient;
import com.jashmore.sqs.extensions.registry.InMemorySchemaRegistryClient;
import com.jashmore.sqs.extensions.registry.SpringCloudSchemaRegistryPayload;
import com.jashmore.sqs.extensions.registry.avro.AvroSpringCloudSchemaProperties;
import com.jashmore.sqs.extensions.registry.avro.EnableSchemaRegistrySqsExtension;
import com.jashmore.sqs.extensions.registry.model.Author;
import com.jashmore.sqs.extensions.registry.model.Book;
import com.jashmore.sqs.registry.AvroSchemaRegistrySqsAsyncClient;
import com.jashmore.sqs.spring.container.basic.QueueListener;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.schema.registry.avro.AvroSchemaServiceManager;
import org.springframework.cloud.schema.registry.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(classes = AvroSpringCloudSchemaRegistryIntegrationTest.Application.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class AvroSpringCloudSchemaRegistryIntegrationTest {
    private static final String QUEUE_NAME = "test";

    @Autowired
    private AvroSchemaServiceManager avroSchemaServiceManager;

    @Autowired
    private AvroSpringCloudSchemaProperties avroSpringCloudSchemaProperties;

    @Autowired
    private SchemaRegistryClient schemaRegistryClient;

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    static final AtomicReference<Book> RECEIVED_BOOK = new AtomicReference<>();
    static final CountDownLatch MESSAGE_RECEIVED_LATCH = new CountDownLatch(1);

    @SpringBootApplication
    @EnableSchemaRegistrySqsExtension
    public static class Application {

        public static void main(String[] args) {
            SpringApplication.run(Application.class);
        }

        @Bean
        @Primary
        public InMemorySchemaRegistryClient inMemorySchemaRegistryClient() {
            return new InMemorySchemaRegistryClient();
        }

        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return new ElasticMqSqsAsyncClient(QUEUE_NAME);
        }

        @SuppressWarnings("unused")
        @QueueListener(value = "test")
        public void myMethod(@SpringCloudSchemaRegistryPayload Book book) {
            RECEIVED_BOOK.set(book);
            MESSAGE_RECEIVED_LATCH.countDown();
        }
    }

    @Test
    void name() throws ExecutionException, InterruptedException {
        // arrange
        final AvroSchemaRegistrySqsAsyncClient avroClient = new AvroSchemaRegistrySqsAsyncClient(
            localSqsAsyncClient,
            schemaRegistryClient,
            avroSchemaServiceManager,
            avroSpringCloudSchemaProperties.getSchemaImports(),
            avroSpringCloudSchemaProperties.getSchemaLocations()
        );
        final String queueUrl = avroClient.getQueueUrl(builder -> builder.queueName(QUEUE_NAME)).get().queueUrl();
        final Book book = new Book("id", "name", new Author("firstname", "lastname"));

        // act
        avroClient.sendAvroMessage("prefix", "contentType", book, builder -> builder.queueUrl(queueUrl));
        MESSAGE_RECEIVED_LATCH.await(5, TimeUnit.SECONDS);

        // assert
        assertThat(RECEIVED_BOOK.get()).isEqualTo(book);
    }
}
