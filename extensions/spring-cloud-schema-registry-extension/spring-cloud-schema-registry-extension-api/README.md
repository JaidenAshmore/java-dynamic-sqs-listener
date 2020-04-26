# Spring Cloud Schema Registry Extension
This extension parses the payload of a message that has been serialized by a tool such as an Apache Avro. This is the basic API and does not contain
the implementation details on how to obtain the schemas or how to serialize the payload with these schemas. See the implementations for specific
details, such as the [avro-spring-cloud-schema-registry-extension](../avro-spring-cloud-schema-registry-extension).