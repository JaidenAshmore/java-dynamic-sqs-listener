# Spring Cloud Schema Registry Extension
This extension allows the SQS consumer to be able to parse messages that have been serialized using a schema
like [Avro](https://avro.apache.org/docs/1.9.2/gettingstartedjava.html) and these definitions have been stored in the
[Spring Cloud Schema Registry](https://cloud.spring.io/spring-cloud-static/spring-cloud-schema-registry/1.0.0.RC1/reference/html/spring-cloud-schema-registry.html).

## Why would you want this?
You may want to more easily control how the schema of your messages change during the lifecycle of the application using a tool like the
Spring Cloud Schema Registry. This will allow you to have your producers with different versions of your message schema, but the SQS
consumer can still be able to use this.

## Examples
To see this in action take a look at the [Spring Cloud Schema Registry Example](../../examples/spring-cloud-schema-registry-example).
