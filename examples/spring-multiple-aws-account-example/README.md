# Spring Connect to Multiple AWS Account SQS queues

There can be use cases to connect to SQS queues across multiple AWS accounts and this shows how to configure the
[spring starter](../../spring/spring-starter) module to do this. Note that this uses multiple ElasticMQ servers but it
would be equivalent for configuring multiple `SqsAsyncClients` with different credentials.

## Usage

```bash
gradle bootRun
```
