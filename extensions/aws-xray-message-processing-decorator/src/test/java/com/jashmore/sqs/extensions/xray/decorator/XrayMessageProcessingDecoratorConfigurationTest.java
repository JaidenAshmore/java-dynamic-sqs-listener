package com.jashmore.sqs.extensions.xray.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class XrayMessageProcessingDecoratorConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(XrayMessageProcessingDecoratorConfiguration.class));

    @Test
    void shouldProvideDecoratorBean() {
        contextRunner.run((context) -> assertThat(context).hasSingleBean(BasicXrayMessageProcessingDecorator.class));
    }
}