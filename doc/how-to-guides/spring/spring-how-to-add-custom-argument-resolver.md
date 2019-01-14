# Spring - How to add a custom ArgumentResolver
The core [ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s provided in the application
may not cover all the use cases for argument resolution that is desired and the framework has been built to allow for the consumers to provide their own
resolver easily. See [core-how-to-implement-custom-argument-resolver.md](../core/core-how-to-implement-a-custom-argument-resolver.md) for more details about how
to build an [ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)

## Prerequisites
- This relies on no custom [ArgumentResolverService](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
being provided therefore the default will be used. See
[QueueListenerConfiguration](../../../java-dynamic-sqs-listener-spring/java-dynamic-sqs-listener-spring-starter/src/main/java/com/jashmore/sqs/spring/config/QueueListenerConfiguration.java)
for more information about how the [ArgumentResolvers](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
are collected.

## Steps
1. Build your [ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) by following
the guide provided by [core-how-to-implement-custom-argument-resolver.md](../core/core-how-to-implement-a-custom-argument-resolver.md).
1. Add this bean in the Spring Context by annotating it with a `@Component` (or other equivalent annotation) or by providing it as a bean in a `@Configuration`
class.
     ```java
     @Configuration
     public class MyConfiguration {
        @Bean
        public ArgumentResolver userGroupArgumentResolver() {
            return new UserGroupArgumentResolver(); 
        }   
     }
     ```

At this point the default [ArgumentResolverService](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
should contain this custom argument resolver and it should be applied during execution of the framework. This is because all 
[ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) beans are including
in the underlying service.
