# Spring - How to add a custom ArgumentResolver
The core [ArgumentResolvers](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) provided in the application
may not cover all the use cases for argument resolution that is desired and the framework has been built to allow for the consumers to provide their own
resolver easily.

## Prerequisites
1. This relies on no [ArgumentResolverService](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
being provided by the consumer of this framework and therefore the default will be used. See
[QueueListenerAutoConfiguration](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-core/src/main/java/com/jashmore/sqs/spring/config/QueueListenerAutoConfiguration.java)
for more information about how the [ArgumentResolvers](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
are collected.

## Steps
1. Create a new implementation of the [ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
interface.
    ```java
    public class MyArgumentResolver implements ArgumentResolver {
       @Override
       public boolean canResolveParameter(Parameter parameter) {
           // implement this 
       }
    
       @Override
       public Object resolveArgumentForParameter(QueueProperties queueProperties, Parameter parameter, Message message) throws ArgumentResolutionException {
           // implement this
       }
    }
    ```
1. Add this bean in the Spring Context by annotating it with a `@Component` (or other equivalent annotation) or by providing it as a bean in a `@Configuration`
class.
     ```java
     @Configuration
     public class MyConfiguration {
        @Bean
        public ArgumentResolver customArgumentResolver() {
            return new MyArgumentResolver(); 
        }   
     }
     ```

At this point the default [ArgumentResolverService](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
should contain this custom argument resolver and it should be applied during execution of the framework.
