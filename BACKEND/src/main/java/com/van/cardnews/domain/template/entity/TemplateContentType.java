package com.van.cardnews.domain.template.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TemplateContentType {

    RECRUITMENT("recruitment", "A"),
    EVENT("event", "B"),
    NEWS("news", "C"),
    QUOTE("quote", "D");

    private final String value;

    /**
     * 템플릿 code의 접두문자.
     *
     * code 형식: {접두문자}{일련번호} (예: A1, A2, B1 ...)
     * 일련번호는 같은 접두문자 내에서 1부터 증가합니다.
     */
    private final String codePrefix;

    TemplateContentType(String value, String codePrefix) {
        this.value = value;
        this.codePrefix = codePrefix;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getCodePrefix() {
        return codePrefix;
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
