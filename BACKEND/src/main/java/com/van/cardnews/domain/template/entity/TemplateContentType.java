package com.van.cardnews.domain.template.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TemplateContentType {

    RECRUITMENT("recruitment"),
    EVENT("event"),
    NEWS("news"),
    QUOTE("quote");

    private final String value;

    TemplateContentType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TemplateContentType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "지원하지 않는 콘텐츠 유형입니다: " + value
                        )
                );
    }
}
