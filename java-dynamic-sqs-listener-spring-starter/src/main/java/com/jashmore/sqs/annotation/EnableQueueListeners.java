package com.jashmore.sqs.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.jashmore.sqs.config.QueueListenerConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation used to enable this framework in the application.
 *
 * <p>This can be applied to your {@link org.springframework.boot.SpringApplication} or {@link Configuration @Configuration} class to
 * automatically configure the framework.
 *
 * <p>An example of the usage of this annotation is shown below:
 * <pre class="code">
 * &#064;EnableQueueListeners
 * &#064;SpringBootApplication
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class);
 *     }
 * }
 * </pre>
 *
 * <p>Note that the minimum requirement for this framework is that a {@link AmazonSQSAsync} bean has been supplied into the spring context
 * for injection.
 */
@Retention(value = RUNTIME)
@Target(ElementType.TYPE)
@Import({QueueListenerConfiguration.class})
@Configuration
public @interface EnableQueueListeners {
}
