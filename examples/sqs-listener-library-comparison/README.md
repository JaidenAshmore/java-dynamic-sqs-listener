# SQS Listener Library Comparison
This module has the Spring Cloud, JMS and Java Dynamic SQS Listener libraries all integrated into a Spring Boot application.  This can be used
to compare the performance of each of the types of libraries with different types of messages being processed. For example, a message that has a lot of
IO or to test what happens if the time to get new messages from the queue is large.

## Results (02/June/2019)
The results that I ran on my local machine (which is not the greatest environment but gives a rough estimate) are the following:

### tl;dr
*Ordered from fastest to slowest*

| Method | Concurrency | Time 1000 messages (ms) |
| JMS | 30 | 4889 |
| Java Dynamic SQS Listener (PrefetchingQueueListener) | 30 | 10550 |
| Java Dynamic SQS Listener (PrefetchingQueueListener) | 10 | 10560ms |
| JMS | 10 | 10587 |
| Spring Cloud | 10 | 20284 |
| Java Dynamic SQS Listener (QueueListener) | 30 | 20385 |
| Java Dynamic SQS Listener (QueueListener) | 10 | 20399 |

### Configuration
- Message latency = 100ms
- Message IO time = 100ms
- Commit ID = `a1bdde0d3c96d71997c3001cecc3ddba0d98c709`

### Raw Results
#### JMS (concurrency = 10)
```
23:44:54.720 [DefaultMessageListenerContainer-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 1454ms
23:44:55.738 [DefaultMessageListenerContainer-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 2472ms
23:44:56.754 [DefaultMessageListenerContainer-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 3488ms
23:44:57.765 [DefaultMessageListenerContainer-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 4499ms
23:44:58.779 [DefaultMessageListenerContainer-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 5513ms
23:44:59.788 [DefaultMessageListenerContainer-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 6522ms
23:45:00.798 [DefaultMessageListenerContainer-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 7532ms
23:45:01.812 [DefaultMessageListenerContainer-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 8546ms
23:45:02.834 [DefaultMessageListenerContainer-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 9568ms
23:45:03.853 [DefaultMessageListenerContainer-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 10587ms
```

#### JMS (concurrency = 30)
```
23:45:56.165 [DefaultMessageListenerContainer-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 1388ms
23:45:56.779 [DefaultMessageListenerContainer-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 2002ms
23:45:57.239 [DefaultMessageListenerContainer-1] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 2462ms
23:45:57.637 [DefaultMessageListenerContainer-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 2860ms
23:45:57.972 [DefaultMessageListenerContainer-28] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 3195ms
23:45:58.317 [DefaultMessageListenerContainer-15] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 3540ms
23:45:58.652 [DefaultMessageListenerContainer-22] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 3875ms
23:45:58.978 [DefaultMessageListenerContainer-28] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 4201ms
23:45:59.331 [DefaultMessageListenerContainer-12] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 4554ms
23:45:59.666 [DefaultMessageListenerContainer-21] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 4889ms
```

#### Spring Cloud (concurrency = 10, this is the max)
```
23:48:11.143 [simpleMessageListenerContainer-11] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 1860ms
23:48:13.201 [simpleMessageListenerContainer-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 3918ms
23:48:15.265 [simpleMessageListenerContainer-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 5982ms
23:48:17.316 [simpleMessageListenerContainer-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 8033ms
23:48:19.363 [simpleMessageListenerContainer-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 10080ms
23:48:21.408 [simpleMessageListenerContainer-3] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 12125ms
23:48:23.452 [simpleMessageListenerContainer-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 14169ms
23:48:25.491 [simpleMessageListenerContainer-10] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 16208ms
23:48:27.530 [simpleMessageListenerContainer-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 18247ms
23:48:29.567 [simpleMessageListenerContainer-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 20284ms
```

#### Jashmore Prefetching (concurrency = 10)
```
23:39:25.302 [message-listeners-prefetching-concurrency10-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 989ms
23:39:26.378 [message-listeners-prefetching-concurrency10-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 2065ms
23:39:27.446 [message-listeners-prefetching-concurrency10-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 3133ms
23:39:28.517 [message-listeners-prefetching-concurrency10-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 4204ms
23:39:29.592 [message-listeners-prefetching-concurrency10-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 5279ms
23:39:30.652 [message-listeners-prefetching-concurrency10-0] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 6339ms
23:39:31.712 [message-listeners-prefetching-concurrency10-7] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 7399ms
23:39:32.769 [message-listeners-prefetching-concurrency10-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 8456ms
23:39:33.821 [message-listeners-prefetching-concurrency10-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 9508ms
23:39:34.873 [message-listeners-prefetching-concurrency10-7] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 10560ms
```

####  Jashmore Prefetching (concurrency = 30)
```
23:38:28.696 [message-listeners-prefetching-concurrency30-12] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 972ms
23:38:29.772 [message-listeners-prefetching-concurrency30-16] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 2048ms
23:38:30.847 [message-listeners-prefetching-concurrency30-15] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 3123ms
23:38:31.926 [message-listeners-prefetching-concurrency30-13] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 4202ms
23:38:32.990 [message-listeners-prefetching-concurrency30-17] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 5266ms
23:38:34.056 [message-listeners-prefetching-concurrency30-17] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 6332ms
23:38:35.119 [message-listeners-prefetching-concurrency30-16] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 7395ms
23:38:36.174 [message-listeners-prefetching-concurrency30-12] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 8450ms
23:38:37.225 [message-listeners-prefetching-concurrency30-16] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 9501ms
23:38:38.274 [message-listeners-prefetching-concurrency30-14] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 10550ms
```

#### Jashmore QueueListener (concurrency = 10)
```
23:40:36.464 [message-listeners-queue-listener-method-concurrency10-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 1889ms
23:40:38.536 [message-listeners-queue-listener-method-concurrency10-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 3961ms
23:40:40.597 [message-listeners-queue-listener-method-concurrency10-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 6022ms
23:40:42.658 [message-listeners-queue-listener-method-concurrency10-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 8083ms
23:40:44.725 [message-listeners-queue-listener-method-concurrency10-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 10150ms
23:40:46.778 [message-listeners-queue-listener-method-concurrency10-9] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 12203ms
23:40:48.830 [message-listeners-queue-listener-method-concurrency10-1] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 14255ms
23:40:50.879 [message-listeners-queue-listener-method-concurrency10-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 16304ms
23:40:52.928 [message-listeners-queue-listener-method-concurrency10-1] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 18353ms
23:40:54.974 [message-listeners-queue-listener-method-concurrency10-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 20399ms
```

#### Jashmore QueueListener (concurrency = 30)
```
23:41:39.507 [message-listeners-queue-listener-method-concurrency30-7] INFO  c.j.sqs.examples.MessageListeners - Time for processing 100 messages is 1875ms
23:41:41.578 [message-listeners-queue-listener-method-concurrency30-4] INFO  c.j.sqs.examples.MessageListeners - Time for processing 200 messages is 3946ms
23:41:43.639 [message-listeners-queue-listener-method-concurrency30-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 300 messages is 6007ms
23:41:45.705 [message-listeners-queue-listener-method-concurrency30-7] INFO  c.j.sqs.examples.MessageListeners - Time for processing 400 messages is 8073ms
23:41:47.768 [message-listeners-queue-listener-method-concurrency30-7] INFO  c.j.sqs.examples.MessageListeners - Time for processing 500 messages is 10136ms
23:41:49.823 [message-listeners-queue-listener-method-concurrency30-6] INFO  c.j.sqs.examples.MessageListeners - Time for processing 600 messages is 12191ms
23:41:51.874 [message-listeners-queue-listener-method-concurrency30-1] INFO  c.j.sqs.examples.MessageListeners - Time for processing 700 messages is 14242ms
23:41:53.923 [message-listeners-queue-listener-method-concurrency30-5] INFO  c.j.sqs.examples.MessageListeners - Time for processing 800 messages is 16291ms
23:41:55.971 [message-listeners-queue-listener-method-concurrency30-8] INFO  c.j.sqs.examples.MessageListeners - Time for processing 900 messages is 18339ms
23:41:58.017 [message-listeners-queue-listener-method-concurrency30-2] INFO  c.j.sqs.examples.MessageListeners - Time for processing 1000 messages is 20385ms
```
