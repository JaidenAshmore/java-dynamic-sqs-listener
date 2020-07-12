# Spring - How to add a custom ArgumentResolver

The core [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s provided in the application
may not cover all the use cases for argument resolution, and the framework has been built to allow for the consumers to provide their own
resolver easily. See [core-how-to-implement-custom-argument-resolver.md](../core/core-how-to-implement-a-custom-argument-resolver.md) for more details about how
to build an [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java).

## Prerequisites

- This relies on the default [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
being used and therefore it cannot be overridden with a custom implementation. See
the [QueueListenerConfiguration](../../../spring/spring-core/src/main/java/com/jashmore/sqs/spring/config/QueueListenerConfiguration.java)
for more information about how the [ArgumentResolvers](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
are collected.

## Steps

1. Build your [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) by following
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

At this point the default [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
should contain this custom argument resolver, and it should be applied during execution of the framework. This is because all
[ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) beans are including
in the underlying service.
