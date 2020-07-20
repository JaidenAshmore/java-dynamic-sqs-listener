# Spring - Customising Argument Resolution

This library uses any [Jackson](https://github.com/FasterXML/jackson) `ObjectMapper` bean present in the application to de-serialise the message payloads.
If the application is a Spring Boot Web application it will use the default `ObjectMapper` provided by
the [JacksonAutoConfiguration](https://github.com/spring-projects/spring-boot/blob/master/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jackson/JacksonAutoConfiguration.java).
This guide provides steps to provide a custom `ObjectMapper`, as well as how to customise the types
of [ArgumentResolvers](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) being used.

## Providing your own `ObjectMapper`

To override the default `ObjectMapper` being used, you must supply your
own [SqsListenerObjectMapperSupplier](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/jackson/SqsListenerObjectMapperSupplier.java) that
will provide the `ObjectMapper`.

```java
@Configuration
public class MyConfiguration {
    @Bean
    public SqsListenerObjectMapperSupplier objectMapperSupplier() {
        return () -> new ObjectMapper();
    }
}
```

*The reason for this supplier being created, instead of defining your own `ObjectMapper` bean is to reduce complexity of interoperability
with the existing `ObjectMappers`, like the Spring Boot auto configured `ObjectMapper` used for HTTP serialisation and de-serialisation.*

## Providing your own [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)

If you want to customise the entire argument resolution process, you can provide your
own [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java). This can be useful if you only want
to support certain argument resolutions or want to have control on how you resolve arguments.

```java
@Configuration
public class MyConfiguration {
    @Bean
    public ArgumentResolverService argumentResolverService(final SqsListenerObjectMapperSupplier objectMapperSupplier) {
        final ObjectMapper objectMapper = objectMapperSupplier.get();
        final List<ArgumentResolver<?>> argumentResolvers = Arrays.asList(
            new PayloadArgumentResolver(new JacksonPayloadMapper(objectMapper)),
            new MessageAttributeArgumentResolver(objectMapper),
            new MessageArgumentResolver()
        );
        return new DelegatingArgumentResolverService(argumentResolvers);
    }
}
```

## Overwriting core [ArgumentResolvers](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) that use Jackson's `ObjectMapper`

If you want to override the [ArgumentResolvers](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) that use an `ObjectMapper`,
you can supply those resolvers as beans to auto configure them into
the default [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java).

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

To use a different de-serialisation tool, like [gson](https://github.com/google/gson), you can implement a
custom [PayloadMapper](../../../core/src/main/java/com/jashmore/sqs/argument/payload/mapper/PayloadMapper.java) and build
the [PayloadArgumentResolver](../../../core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java) like in the above example.
