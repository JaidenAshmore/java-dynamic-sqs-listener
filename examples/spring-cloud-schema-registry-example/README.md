# Spring Cloud Schema Registry Extension Example

This example shows how you can consume messages which have been defined using an [Avro](https://avro.apache.org/docs/1.9.2/gettingstartedjava.html)
Schema and
the [Spring Cloud Schema Registry](https://cloud.spring.io/spring-cloud-static/spring-cloud-schema-registry/1.0.0.RC1/reference/html/spring-cloud-schema-registry.html).

To find the corresponding code look in the [Spring Cloud Schema Registry Extension](../../extensions/spring-cloud-schema-registry-extension) module.

## Steps

Start each of these applications in new terminals/your IDE:

1. A Spring Cloud Schema Registry server

    ```bash
    wget -O /tmp/schema-registry-server.jar https://repo.spring.io/libs-release-ossrh-cache/org/springframework/cloud/spring-cloud-schema-registry-server/1.0.3.RELEASE/spring-cloud-schema-registry-server-1.0.3.RELEASE.jar
    cd /tmp
    java -jar schema-registry-server.jar
    ```

1. A local SQS server using ElasticMQ

    ```bash
    docker run -p 9324:9324 softwaremill/elasticmq
    ```

1. The SQS consumer service

    ```bash
    cd java-dynamic-sqs-listener/examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-consumer
    gradle bootRun
    ```

1. The first SQS producer service

    ```bash
    cd java-dynamic-sqs-listener/examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer
    gradle bootRun
    ```

1. The second SQS producer service

    ```bash
    cd java-dynamic-sqs-listener/examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer-2
    gradle bootRun
    ```

You should now see the consumer receiving messages from both producers and even though the producers are sending
the payload in different schema versions the consumer is still able to process the message.
