package com.van.cardnews.domain.template.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true) // autoApply = true를 주면 해당 Enum을 사용하는 모든 곳에 자동으로 적용됩니다.
public class TemplateContentTypeConverter implements AttributeConverter<TemplateContentType, String> {

    @Override
    public String convertToDatabaseColumn(TemplateContentType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue(); // Enum에 정의된 소문자 값을 DB에 저장
    }

    @Override
    public TemplateContentType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return TemplateContentType.from(dbData); // DB의 소문자 값을 다시 Enum으로 변환
    }
}
