package com.jashmore.sqs.micronaut.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Supplier;

/**
 * Specific {@link ObjectMapper} supplier for the use in this Spring Library to provide easier customisation of the {@link ObjectMapper} used in
 * de-serialisation.
 *
 * <p>This wrapper is needed as there is complications with how a custom {@link ObjectMapper} can be supplied without impacting the default Spring Web
 * {@link ObjectMapper}. We don't want this library's {@link ObjectMapper} to override the default Spring Boot one unintentionally.
 */
@FunctionalInterface
public interface SqsListenerObjectMapperSupplier extends Supplier<ObjectMapper> {}
