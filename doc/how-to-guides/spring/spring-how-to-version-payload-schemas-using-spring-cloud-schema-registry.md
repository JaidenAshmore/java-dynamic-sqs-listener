# Spring - How to version message payload Schemas using Spring Cloud Schema Registry
As your application grows over time the format of the data that needs to be sent in the SQS messages may change as well. To allow for
these changes, the [Spring Cloud Schema Registry](https://cloud.spring.io/spring-cloud-static/spring-cloud-schema-registry/1.0.0.RC1/reference/html/spring-cloud-schema-registry.html)
can be used to track the version of your schemas, allowing the SQS consumer to be able to interpret multiple versions of your payload.

## Full reference
For a full working solution of this feature, take a look at the [Spring Cloud Schema Registry Example](../../../examples/spring-cloud-schema-registry-example).

## Steps to consume messages
1. Include the `Spring Cloud Schema Registry Extension` dependency
    ```xml
        <dependency>
            <groupId>com.jashmore</groupId>
            <artifactId>avro-spring-cloud-schema-registry-extension</artifactId>
            <version>${project.version}</version>
        </dependency>  
    ```
1. Define your schemas and map this in your spring `application.yml`
    ```yml
   spring:
     cloud:
       schema-registry-client:
         endpoint: http://localhost:8990
       schema:
         avro:
           schema-imports:
             - classpath:avro/author.avsc
           schema-locations:
             - classpath:avro/book.avsc
    ```
   In this example above we have a book schema which is dependent on the author schema. We have also harded coded the Schema Registry
   to be at [http://localhost:8990](http://localhost:8990).
1. Create your schemas and place them in your `resources` directory. For example this is an example schema for the Book.
    ```json
    {
      "namespace" : "com.jashmore.sqs.extensions.registry.model",
      "type" : "record",
      "name" : "Book",
      "fields" : [
        { "name":"id","type":"string" },
        { "name":"name","type":"string" },
        { "name":"author","type":"Author" }
      ]
    }
    ```
1. Enable the extension by annotating the Spring Application
    ```java
    @EnableSchemaRegistrySqsExtension
    @SpringBootApplication
    class Application {
        // normal code
    }
    ```
1. Define your queue listener using the `@SpringCloudSchemaRegistryPayload` to represent the payload that needs to be deserialized from
the message payload.
    ```java
    @QueueListener(value = "queueName")
    public void listen(@SpringCloudSchemaRegistryPayload Book payload) {
        log.info("Payload: {}", payload);
    }
    ```

## Steps to produce messages using Avro
You can wrap your `SqsAsyncClient` with the
[AvroSchemaRegistrySqsAsyncClient](../../../util/proxy-method-interceptor/src/main/java/com/jashmore/sqs/registry/AvroSchemaRegistrySqsAsyncClient.java)
to be able to more easily send a message that will be serialized using the Avro Schema.  This Avro SQS Client was built for testing purposes and therefore it is
recommended to developer your own logic for sending these messages.

For a full example of building this client, take a look at the
[Producer Example](../../../examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer).
