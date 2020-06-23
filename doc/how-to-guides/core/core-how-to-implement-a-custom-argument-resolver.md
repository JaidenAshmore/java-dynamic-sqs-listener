# Core - How to implement a custom Argument Resolver
When the framework executes the methods for a SQS message, the
[ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used to build
the arguments for the method execution from the message being processed.  The default core implementation, the
[DelegatingArgumentResolverService](../../../core/src/main/java/com/jashmore/sqs/argument/DelegatingArgumentResolverService.java),
uses [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s under the hood to resolve
each type of argument. Users of the framework may get to a point where the default argument types are insufficient and therefore want to provide their own.

To do this, when building your [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java)
with the [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s make sure to add your
custom resolver.
    ```java
    new DelegatingArgumentResolverService(ImmutableSet.of(
         new MessageIdArgumentResolver(),
         ...
         new MyCustomArgumentResolver()
   ));
    ```

## Example Use Case
The payload in a SQS message is deeply nested and it is desirable to only provide the field that is needed instead of passing the entire
message body into the method. For example, using the sample SQS Message below, only the user's group is necessary and therefore an
[ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) is wanting to be included
that will map any method argument annotated with `@UserGroup` to extract just that single field.
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
    // other fields here
}
```

## Steps
1. Create a new annotation that indicates that this user group should be extracted.
```java
@Retention(value = RUNTIME)
@Target(ElementType.PARAMETER)
public @interface UserGroup {
}
```
1. Create a new implementation of the [ArgumentResolver](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
interface that will be able to resolve these arguments with those annotations.
    ```java
    public class UserGroupArgumentResolver implements ArgumentResolver<String> {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
     
        @Override
        public boolean canResolveParameter(MethodParameter methodParameter) {
            // make sure only String parameters with the @UserGroup annotations are resolved using this
            return methodParameter.getParameter().getType().isAssignableFrom(String.class)
                && AnnotationUtils.findParameterAnnotation(methodParameter, UserGroup.class).isPresent();
        }
    
        @Override
        public Object resolveArgumentForParameter(QueueProperties queueProperties, MethodParameter methodParameter, Message message) throws ArgumentResolutionException {
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
1. Create a method that will use this argument, for example something like:
    ```java
        public void messageListener(@UserGroup final String userGroup, @MessageId final String messageId) {
            // Do something here
        }
    ```
1. Build your [ArgumentResolverService](../../../api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) with
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
