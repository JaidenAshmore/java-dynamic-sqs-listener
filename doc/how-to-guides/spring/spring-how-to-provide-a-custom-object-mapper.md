# Spring - How to provide own ObjectMapper for serialisation/deserialisation
Currently a Jackson `ObjectMapper` is being used for the serialisation and deserialisation of messages and their attributes. If none is provided
by the application, the Spring Starter will provide their own to use. This shows steps on providing your own so the default is not used.

### Steps

1. In a `@Configuration` class define a bean of type `ObjectMapper`
    ```java
    @Configuration
    public class MyConfiguration {
       @Bean
       public ObjectMapper objectMapper() {
           return new ObjectMapper();
       }     
    }
    ```


## Providing an ObjectMapper without including it in the Spring Context
One problem with doing this method is that this ObjectMapper will be shared with this library as well as any others that need this, e.g. the HTTP layer
may be using this ObjectMapper for their own serialisation. Therefore, if you want to provide an `ObjectMapper` that is only used by this library you would
need to override the individual core [ArgumentResolvers](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
that are using this `ObjectMapper`, which is the
[PayloadArgumentResolver](../../../java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/payload/PayloadArgumentResolver.java) and
[MessageAttributeArgumentResolver](../../../java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/attribute/MessageAttributeArgumentResolver.java). 

### Steps

1. In a `@Configuration` class define your own [ArgumentResolverService](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
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
           return new MessageAttributeArgumentResolver(objectMapper);
       }
    }
    ```