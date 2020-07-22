# Spring Sleuth Example

An example of setting up Brave Tracing with this library.

## Steps

1. Start up a Zipkin server

    ```shell script
    docker run -d -p 9411:9411 openzipkin/zipkin
    ```

1. Start the application

    ```shell script
    gradle bootRun
    ```

1. Look in the logs for the server to see messages processed with Trace IDs
1. Navigate to <http://localhost:9411/zipkin> and type one of the trace IDs into the search bar to see the full trace
