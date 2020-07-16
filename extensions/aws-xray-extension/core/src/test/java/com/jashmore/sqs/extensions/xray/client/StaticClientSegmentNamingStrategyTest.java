package com.jashmore.sqs.extensions.xray.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaticClientSegmentNamingStrategyTest {

    @Test
    void willReturnSameNameForEachCall() {
        final StaticClientSegmentNamingStrategy strategy = new StaticClientSegmentNamingStrategy("static-name");
        assertThat(strategy.getSegmentName()).isEqualTo("static-name");
        assertThat(strategy.getSegmentName()).isEqualTo("static-name");
    }
}