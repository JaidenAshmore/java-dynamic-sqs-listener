package com.jashmore.sqs.argument.attribute;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MessageAttributeDataTypes {
    STRING("String"),
    NUMBER("Number"),
    BINARY("Binary");

    private final String value;
}
