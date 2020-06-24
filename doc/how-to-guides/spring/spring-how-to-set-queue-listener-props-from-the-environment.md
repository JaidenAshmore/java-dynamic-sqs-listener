# Spring - How to set Queue Listener props from the Environment

The Queue Listener annotations have been written to allow for them to be set via the Spring Environment, e.g. properties from an `application.yml` file.

## Steps

1. Create some properties in the `application.yml` file, in this case there are two profiles with different concurrency rates.

    ```yaml

    ---
    spring.profiles: staging

    queues:
        my-queue:
           concurrency: 5

    ---
    spring.profiles: production
    queues:
        my-queue:
           concurrency: 5
    ```

1. Use the `{propertyName}String` fields in the annotations to pull data from the environment:

    ```java
    @QueueListener(value = "http://localhost:9432/q/myqueue", concurrencyLevelString="${queues.my-queue.concurrency}")
    public void myMethod(@Payload String payload) {

    }
    ```

***NOTE**: the `{propertyName}String` fields will override any of the other properties if they are set. E.g. if both `concurrencyLevel` and
`concurrencyLevelString` are set the `concurrencyLevelString` one will be prioritised*
