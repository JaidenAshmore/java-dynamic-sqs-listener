# Spring - How to provide own ObjectMapper for serialisation/de-serialisation

The Spring Boot starter uses Jackson by default to de-serialise of message payloads/attributes. If that is undesirable, a payload mapper can be used instead.

## Using a custom `ObjectMapper`

In a `@Configuration` class define a bean of type `ObjectMapper`

```java
@Configuration
public class MyConfiguration {
   @Bean
   public ObjectMapper objectMapper() {
       return new ObjectMapper();
   }
}
```

## Instantiating a custom `JacksonPayloadMapper`

One problem with doing this method is that this ObjectMapper will be shared with this library as well as any others that need this, e.g. the HTTP layer
may be using this ObjectMapper for their own serialisation. Therefore, if you want to provide an `ObjectMapper` that is only used by this library you would
need to override the individual core [ArgumentResolvers](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
that are using this `ObjectMapper`, which is the
[PayloadArgumentResolver](../../../core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java) and
[MessageAttributeArgumentResolver](../../../core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttributeArgumentResolver.java).

In a `@Configuration` class define your own [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)

```java
@Configuration
public class MyConfiguration {
   private static final ObjectMapper MY_OBJECT_MAPPER_FOR_SQS = new ObjectMapper();

   @Bean
   public PayloadArgumentResolver payloadArgumentResolver() {
       return new PayloadArgumentResolver(new JacksonPayloadMapper(MY_OBJECT_MAPPER_FOR_SQS));
   }

   @Bean
   public MessageAttributeArgumentResolver messageAttributeArgumentResolver() {
       return new MessageAttributeArgumentResolver(MY_OBJECT_MAPPER_FOR_SQS);
   }
}
```

## De-serialising without using Jackson

To use a different de-serialisation tool, like GSON, you can implement a
custom [PayloadMapper](../../../core/src/main/java/com/jashmore/sqs/argument/payload/mapper/PayloadMapper.java) and build
the [PayloadArgumentResolver](../../../core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java) like in the above example.
