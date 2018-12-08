package it.com.jashmore.sqs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.amazonaws.services.sqs.model.Message;
import it.com.jashmore.example.Application;
import com.jashmore.sqs.QueueProperties;
import com.jashmore.sqs.argument.ArgumentResolutionException;
import com.jashmore.sqs.argument.ArgumentResolverService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Parameter;

@SpringBootTest(classes = { Application.class, ArgumentResolverServiceOverrideIntegrationTest.TestConfig.class }, webEnvironment = RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ArgumentResolverServiceOverrideIntegrationTest {

    @Autowired
    private ArgumentResolverService argumentResolverService;

    @Configuration
    public static class TestConfig {
        @Bean
        public ArgumentResolverService argumentResolverService() {
            return new MyArgumentResolverService();
        }
    }

    @Test
    public void argumentResolverProvidedInConfigurationIsUsedInsteadOfDefault() {
        // act
        assertThat(argumentResolverService).isInstanceOf(MyArgumentResolverService.class);
    }

    private static class MyArgumentResolverService implements ArgumentResolverService {
        @Override
        public Object resolveArgument(final QueueProperties queueProperties, final Parameter parameter, final Message message) throws ArgumentResolutionException {
            return null;
        }
    }
}
