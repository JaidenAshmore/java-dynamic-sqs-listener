# Spring Cloud Schema Registry Example

## Steps
Start each of these applications in new terminals/your IDE:
1. A Spring Cloud Schema Registry server
    ```bash
    wget -O /tmp/schema-registry-server.jar https://repo.spring.io/libs-snapshot-local/org/springframework/cloud/spring-cloud-schema-registry-server/1.0.0.BUILD-SNAPSHOT/spring-cloud-schema-registry-server-1.0.0.BUILD-SNAPSHOT.jar
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
   mvn spring-boot:run
   ```
1. The first SQS producer service
   ```bash
   cd java-dynamic-sqs-listener/examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer
   mvn spring-boot:run
   ```
1. The second SQS producer service
   ```bash
   cd java-dynamic-sqs-listener/examples/spring-cloud-schema-registry-example/spring-cloud-schema-registry-producer
   mvn spring-boot:run
   ```

You should now see the consumer receiving messages from both producers and even though the producers are sending
the payload in different schema versions the consumer is still able to process the message. 