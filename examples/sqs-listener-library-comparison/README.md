# SQS Listener Library Comparison

This module has the Spring Cloud, JMS and Java Dynamic SQS Listener libraries all integrated into a Spring Boot application.  This can be used
to compare the performance of each of the types of libraries with different types of messages being processed. For example, a message that has a lot of
IO or to test what happens if the time to get new messages from the queue is large.

## Getting Started

You can run the [Application](src/main/java/com/jashmore/sqs/examples/Application.java) which will place messages onto a queue and then the corresponding
listener will process them. Take a look at the [ExampleConstants](src/main/java/com/jashmore/sqs/examples/ExampleConstants.java) for the types of variables
that can be altered, such as the time to process the messages, etc.

## Results (02/June/2019)

The results that I ran on my local machine (which is not the greatest environment but gives a rough estimate) are the following:

### tl;dr

Ordered from fastest to slowest:

| Method | Concurrency | Time 1000 messages (ms) |
| JMS | 30 | 19997 |
| Java Dynamic SQS Listener (PrefetchingQueueListener) | 30 | 21087ms |
| Java Dynamic SQS Listener (QueueListener) | 30 | 41484ms |
| Java Dynamic SQS Listener (PrefetchingQueueListener) | 10 | 50049ms |
| JMS | 10 | 51354ms |
| Spring Cloud | 10 | 70558ms |
| Java Dynamic SQS Listener (QueueListener) | 10 | 70640ms |

### Configuration

- Message latency = 100ms
- Message IO time = 100ms
- Commit ID = `a1bdde0d3c96d71997c3001cecc3ddba0d98c709`

### Raw Results

#### JMS (concurrency = 10)

```text
21:17:17.677 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 6205ms
21:17:22.701 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 11229ms
21:17:27.722 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 16250ms
21:17:32.736 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 21264ms
21:17:37.751 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 26279ms
21:17:42.766 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 31294ms
21:17:47.780 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 36308ms
21:17:52.794 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 41322ms
21:17:57.808 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 46336ms
21:18:02.826 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 51354ms
```

#### JMS (concurrency = 30)

```text
21:15:48.707 [DefaultMessageListenerContainer-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 4731ms
21:15:50.601 [DefaultMessageListenerContainer-23] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 6625ms
21:15:52.277 [DefaultMessageListenerContainer-19] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 8301ms
21:15:53.945 [DefaultMessageListenerContainer-15] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 9969ms
21:15:55.620 [DefaultMessageListenerContainer-23] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 11644ms
21:15:57.296 [DefaultMessageListenerContainer-19] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 13320ms
21:15:58.959 [DefaultMessageListenerContainer-27] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 14983ms
21:16:00.636 [DefaultMessageListenerContainer-11] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 16660ms
21:16:02.310 [DefaultMessageListenerContainer-19] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 18334ms
21:16:03.973 [DefaultMessageListenerContainer-27] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 19997ms
```

#### JMS (concurrency = 100)

No idea why this suddenly slows down between 700 and 800 messages.

```textt
21:31:33.101 [DefaultMessageListenerContainer-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 4729ms
21:31:34.995 [DefaultMessageListenerContainer-11] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 6623ms
21:31:36.453 [DefaultMessageListenerContainer-18] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 8081ms
21:31:37.671 [DefaultMessageListenerContainer-19] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 9299ms
21:31:38.750 [DefaultMessageListenerContainer-17] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 10378ms
21:31:39.739 [DefaultMessageListenerContainer-41] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 11367ms
21:31:40.634 [DefaultMessageListenerContainer-26] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 12262ms
21:32:01.027 [DefaultMessageListenerContainer-48] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 32655ms
21:32:09.217 [DefaultMessageListenerContainer-51] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 40845ms
21:32:10.272 [DefaultMessageListenerContainer-32] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 41900ms

```

#### Spring Cloud (concurrency = 10, this is the max)

```text
21:18:52.214 [simpleMessageListenerContainer-11] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 6897ms
21:18:59.314 [simpleMessageListenerContainer-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 13997ms
21:19:06.415 [simpleMessageListenerContainer-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 21098ms
21:19:13.495 [simpleMessageListenerContainer-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 28178ms
21:19:20.572 [simpleMessageListenerContainer-11] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 35255ms
21:19:27.642 [simpleMessageListenerContainer-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 42325ms
21:19:34.708 [simpleMessageListenerContainer-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 49391ms
21:19:41.773 [simpleMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 56456ms
21:19:48.826 [simpleMessageListenerContainer-11] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 63509ms
21:19:55.875 [simpleMessageListenerContainer-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 70558ms
```

#### Jashmore Prefetching (concurrency = 10)

```text
21:21:18.084 [prefetching-concurrency10-message-processing-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 5011ms
21:21:23.088 [prefetching-concurrency10-message-processing-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 10015ms
21:21:28.092 [prefetching-concurrency10-message-processing-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 15019ms
21:21:33.097 [prefetching-concurrency10-message-processing-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 20024ms
21:21:38.102 [prefetching-concurrency10-message-processing-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 25029ms
21:21:43.105 [prefetching-concurrency10-message-processing-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 30032ms
21:21:48.108 [prefetching-concurrency10-message-processing-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 35035ms
21:21:53.113 [prefetching-concurrency10-message-processing-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 40040ms
21:21:58.118 [prefetching-concurrency10-message-processing-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 45045ms
21:22:03.122 [prefetching-concurrency10-message-processing-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 50049ms
```

#### Jashmore Prefetching (concurrency = 30)

```text
21:22:48.267 [prefetching-concurrency30-message-processing-7] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 2403ms
21:22:50.374 [prefetching-concurrency30-message-processing-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 4510ms
21:22:52.466 [prefetching-concurrency30-message-processing-22] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 6602ms
21:22:54.553 [prefetching-concurrency30-message-processing-1] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 8689ms
21:22:56.635 [prefetching-concurrency30-message-processing-16] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 10771ms
21:22:58.712 [prefetching-concurrency30-message-processing-22] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 12848ms
21:23:00.782 [prefetching-concurrency30-message-processing-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 14918ms
21:23:02.845 [prefetching-concurrency30-message-processing-16] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 16981ms
21:23:04.900 [prefetching-concurrency30-message-processing-27] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 19036ms
21:23:06.951 [prefetching-concurrency30-message-processing-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 21087ms
```

#### Jashmore Prefetching (concurrency = 100)

```text
21:33:07.007 [prefetching-concurrency30-message-processing-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 2403ms
21:33:09.114 [prefetching-concurrency30-message-processing-12] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 4510ms
21:33:11.206 [prefetching-concurrency30-message-processing-25] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 6602ms
21:33:13.290 [prefetching-concurrency30-message-processing-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 8686ms
21:33:15.371 [prefetching-concurrency30-message-processing-16] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 10767ms
21:33:17.454 [prefetching-concurrency30-message-processing-25] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 12850ms
21:33:19.526 [prefetching-concurrency30-message-processing-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 14922ms
21:33:21.587 [prefetching-concurrency30-message-processing-17] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 16983ms
21:33:23.641 [prefetching-concurrency30-message-processing-27] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 19037ms
21:33:25.693 [prefetching-concurrency30-message-processing-7] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 21089ms
```

#### Jashmore QueueListener (concurrency = 10)

```text
21:23:55.674 [queue-listener-method-concurrency10-message-processing-1] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 7086ms
21:24:02.761 [queue-listener-method-concurrency10-message-processing-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 14173ms
21:24:09.830 [queue-listener-method-concurrency10-message-processing-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 21242ms
21:24:16.897 [queue-listener-method-concurrency10-message-processing-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 28309ms
21:24:23.958 [queue-listener-method-concurrency10-message-processing-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 35370ms
21:24:31.018 [queue-listener-method-concurrency10-message-processing-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 42430ms
21:24:38.078 [queue-listener-method-concurrency10-message-processing-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 49490ms
21:24:45.131 [queue-listener-method-concurrency10-message-processing-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 56543ms
21:24:52.181 [queue-listener-method-concurrency10-message-processing-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 63593ms
21:24:59.228 [queue-listener-method-concurrency10-message-processing-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 70640ms
```

#### Jashmore QueueListener (concurrency = 30)

```text
21:25:37.364 [queue-listener-method-concurrency30-message-processing-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 4445ms
21:25:41.507 [queue-listener-method-concurrency30-message-processing-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 8588ms
21:25:45.642 [queue-listener-method-concurrency30-message-processing-14] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 12723ms
21:25:49.769 [queue-listener-method-concurrency30-message-processing-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 16850ms
21:25:53.884 [queue-listener-method-concurrency30-message-processing-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 20965ms
21:25:57.995 [queue-listener-method-concurrency30-message-processing-13] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 25076ms
21:26:02.102 [queue-listener-method-concurrency30-message-processing-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 29183ms
21:26:06.205 [queue-listener-method-concurrency30-message-processing-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 33286ms
21:26:10.312 [queue-listener-method-concurrency30-message-processing-14] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 37393ms
21:26:14.403 [queue-listener-method-concurrency30-message-processing-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 41484ms
```
