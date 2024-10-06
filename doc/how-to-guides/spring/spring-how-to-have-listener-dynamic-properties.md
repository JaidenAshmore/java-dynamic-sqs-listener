# Spring - How to have Dynamic Properties in the listener

Java annotations and Spring configuration properties can't provide dynamic properties, and therefore to provide dynamic runtime configuration in Spring apps,
the annotation parser can be overridden. Some annotation parsers
are [QueueListenerParser](../../../annotations/src/main/java/com/jashmore/sqs/annotations/core/basic/QueueListenerParser.java),
[PrefetchingQueueListenerParser](../../../annotations/src/main/java/com/jashmore/sqs/annotations/core/prefetch/PrefetchingQueueListenerParser.java),
and [FifoQueueListenerParser](../../../annotations/src/main/java/com/jashmore/sqs/annotations/core/fifo/FifoQueueListenerParser.java).

## Steps

1.  Extend the corresponding parser class.

    ```java
    public class CustomPrefetchingQueueListenerParser extends PrefetchingQueueListenerParser {

        private static final Random random = new Random();

        public CustomPrefetchingQueueListenerParser(Environment environment) {
            super(environment);
        }

        @Override
        protected Supplier<Integer> concurrencySupplier(PrefetchingQueueListener annotation) {
            return () -> random.nextInt(20);
        }
    }

    ```

1.  Include this parser as a bean

    ```java
    class MyConfiguration {

        public PrefetchingQueueListenerParser customParser(final Environment environment) {
            return new CustomPrefetchingQueueListenerParser(environment);
        }
    }

    ```

1.  Now all message listeners with the [PrefetchingQueueListener](../../../annotations/src/main/java/com/jashmore/sqs/annotations/core/prefetch/PrefetchingQueueListener.java)
    annotation will use a random concurrency rate.
