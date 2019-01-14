# Core - How to implement a custom Argument Resolver
When the framework executes the methods for a SQS message, the
[ArgumentResolverService](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) is used to map the
message to each of the arguments of the method.  The main implementation, the
[DelegatingArgumentResolverService](../../../java-dynamic-sqs-listener-core/src/main/java/com/jashmore/sqs/argument/DelegatingArgumentResolverService.java)
uses [ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)s under the hood to resolve
each type of argument. Users of the framework may get to a point where the default argument types are insufficient and therefore want to provide their own.

## Example Use Case
The payload in a SQS message is deeply nested and it is desirable to only provide the field that is needed instead of passing the entire
message body into the method. For example, using the sample SQS Message below, only the user's group is necessary and therefore an
[ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java) is wanting to be included
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
    },
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
1. Create a new implementation of the [ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java)
interface that will be able to resolve these arguments with those annotations.
    ```java
    public class UserGroupArgumentResolver implements ArgumentResolver {
       private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
       @Override
       public boolean canResolveParameter(Parameter parameter) {   
           // make sure only String parameters with the @UserGroup annotations are resolved using this
           return parameter.getAnnotation(UserGroup.class) != null
               && parameter.getType() == String.class;
       }
    
       @Override
       public Object resolveArgumentForParameter(QueueProperties queueProperties, Parameter parameter, Message message) throws ArgumentResolutionException {
           // not the nicest solution, you should probably create a POJO instead of this TypeReference and Maps
           try {
               return Optional.ofNullable(objectMapper.readValue(message.body(), new TypeReference<Map<String, Map<String, Map<String, String>>>>(){}))
                   .map(message -> message.get("payload"))
                   .map(message -> message.get("user"))
                   .map(message -> message.get("group"));
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
1. Build your [ArgumentResolverService](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolverService.java) with
this [ArgumentResolver](../../../java-dynamic-sqs-listener-api/src/main/java/com/jashmore/sqs/argument/ArgumentResolver.java).
    ```java
    new DelegatingArgumentResolverService(ImmutableSet.of(
         new UserGroupArgumentResolver()
   ));
    ```

## Integrating with Spring
The Spring Starter provides an easier way to integrate argument resolvers, see
[spring-how-to-add-custom-argument-resolver.md](../spring/spring-how-to-add-custom-argument-resolver.md).
