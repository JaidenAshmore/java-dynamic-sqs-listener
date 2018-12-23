package it.com.jashmore.sqs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.amazonaws.services.sqs.model.Message;
import it.com.jashmore.example.Application;
import com.jashmore.sqs.argument.ArgumentResolverService;
import com.jashmore.sqs.argument.payload.Payload;
import com.jashmore.sqs.argument.payload.mapper.PayloadMapper;
import com.jashmore.sqs.argument.payload.mapper.PayloadMappingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@SpringBootTest(classes = {Application.class, PayloadMapperOverrideIntegrationTest.TestConfig.class}, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PayloadMapperOverrideIntegrationTest {

    @Autowired
    private ArgumentResolverService argumentResolverService;

    @Configuration
    public static class TestConfig {
        @Bean
        public PayloadMapper payloadMapper() {
            return new MyPayloadMapper();
        }
    }

    @Test
    public void payloadMapperShouldBeAbleToBeOverriddenForMessageBodyMapping() throws NoSuchMethodException {
        // arrange
        final Method method = PayloadMapperOverrideIntegrationTest.class.getMethod("method", String.class);
        final Parameter payloadParameter = method.getParameters()[0];

        // act
        final Object argumentValue = argumentResolverService.resolveArgument(null, payloadParameter, new Message().withBody("test"));

        // assert
        assertThat(argumentValue).isEqualTo("test-mapped");
    }

    public void method(@Payload String payload) {

    }

    private static class MyPayloadMapper implements PayloadMapper {
        @Override
        public Object map   (final Message message, final Class<?> clazz) throws PayloadMappingException {
            return message.getBody() + "-mapped";
        }
    }
}
