# Spring - How to add Custom MessageProcessingDecorators

If you have a custom [MessageProcessingDecorator](../../../api/src/main/java/com/jashmore/sqs/decorator/MessageProcessingDecorator.java)
that you want to apply to all the default message listeners you can just add it as a spring bean to auto configure it.

```java
@Configuration
public class MyConfiguration {

    @Bean
    public MessageProcessingDecorator myDecorator() {
        return new MyMessageProcessingDecorator();
    }
}

```
