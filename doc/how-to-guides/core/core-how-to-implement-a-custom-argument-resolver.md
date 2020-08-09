# Core - How to implement a custom Argument Resolver

The framework uses an
[ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) to build
the arguments for the method listener execution. The default core implementation, the
[DelegatingArgumentResolverService](../../../core/src/main/java/com/jashmore/sqs/argument/DelegatingArgumentResolverService.java),
uses [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s under the hood to resolve
each type of argument.

You can define your own [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)and include it in your
[ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) to extend what type of paramters you can consume.

```java
new DelegatingArgumentResolverService(ImmutableSet.of(
     new MessageIdArgumentResolver(),
     ...
     new MyCustomArgumentResolver()
));
```

## Example Use Case

There is a deeply nested payload in the SQS message, and it is desirable to only provide the field that is needed instead of passing the entire
message body into the method. For example, using the sample SQS Message below, only the user's group is necessary and therefore a custom
[ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) can be included
to extract that field to any method parameter annotated with the `@UserGroup` annotation.
example:

```java
public void messageListener(@UserGroup final String userGroup) {
    // process mesage here
}
```

### Example SQS Message

```json
{
    "payload": {
        "user": {
            "group": "admin"
        }
    }
}
```

## Steps

1.  Create a new annotation that indicates that this user group should be extracted.

    ```java
    @Retention(value = RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface UserGroup {
    }
    ```

1.  Create a new implementation of the [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
    interface that will be able to resolve these arguments with those annotations.

    ```java
    public class UserGroupArgumentResolver implements ArgumentResolver<String> {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        @Override
        public boolean canResolveParameter(MethodParameter methodParameter) {
            // make sure only String parameters with the @UserGroup annotations are resolved using this
            return (
                methodParameter.getParameter().getType().isAssignableFrom(String.class) &&
                AnnotationUtils.findParameterAnnotation(methodParameter, UserGroup.class).isPresent()
            );
        }

        @Override
        public String resolveArgumentForParameter(QueueProperties queueProperties, MethodParameter methodParameter, Message message)
            throws ArgumentResolutionException {
            try {
                // You could build an actual POJO instead of using this JsonNode
                final JsonNode node = objectMapper.readTree(message.body());
                final JsonNode userGroupNode = node.at("/payload/user/group");
                if (userGroupNode.isMissingNode()) {
                    return null;
                } else {
                    return userGroupNode.textValue();
                }
            } catch (IOException e) {
                throw new ArgumentResolutionException("Error parsing payload", e);
            }
        }
    }
    ```

1.  Create a method that will use this argument, for example something like:

    ```java
        public void messageListener(@UserGroup final String userGroup) {
            // Do something here
        }
    ```

1.  Build your [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) with
    this [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java).

    ```java
    new DelegatingArgumentResolverService(ImmutableSet.of(
         // other ArgumentResolvers here
         new UserGroupArgumentResolver()

    ));
    ```

## Integrating with Spring

The Spring Starter provides an easier way to integrate argument resolvers, see
[spring-how-to-add-custom-argument-resolver.md](../spring/spring-how-to-add-custom-argument-resolver.md).
