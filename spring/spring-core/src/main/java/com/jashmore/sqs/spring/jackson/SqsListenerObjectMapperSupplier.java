package com.jashmore.sqs.spring.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jashmore.sqs.spring.config.QueueListenerConfiguration;

import java.util.function.Supplier;

/**
 * Specific {@link ObjectMapper} supplier for the use in this Spring Library to provide easier customisation of the {@link ObjectMapper} used in
 * de-serialisation.
 *
 * <p>This wrapper is needed as there is complications with how a custom {@link ObjectMapper} can be supplied without impacting the default Spring Web
 * {@link ObjectMapper}. For example, if the {@link QueueListenerConfiguration} defines an {@link ObjectMapper} if no
 * bean of that type exists and this configuration occurs before the Spring version is instantiated, the result will be that Spring uses this SQS listener's
 * {@link ObjectMapper} instead of theirs and the consumer will lose the ability to customise this {@link ObjectMapper} using application properties, etc.
 */
@FunctionalInterface
public interface SqsListenerObjectMapperSupplier extends Supplier<ObjectMapper> {

}
