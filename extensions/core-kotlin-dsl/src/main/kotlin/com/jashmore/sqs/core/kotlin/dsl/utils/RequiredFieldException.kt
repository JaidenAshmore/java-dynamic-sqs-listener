package com.jashmore.sqs.core.kotlin.dsl.utils

/**
 * Exception thrown if a field is required for the specific component construction.
 *
 * This is required as the compile time checks for required fields in Kotlin DSL isn't quite there yet.
 */
class RequiredFieldException(fieldName: String, componentName: String) : RuntimeException("$fieldName is required for $componentName")
