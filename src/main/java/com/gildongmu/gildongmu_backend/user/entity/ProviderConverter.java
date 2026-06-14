package com.gildongmu.gildongmu_backend.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProviderConverter implements AttributeConverter<Provider, String> {

    @Override
    public String convertToDatabaseColumn(Provider provider) {
        return provider == null ? null : provider.getCode();
    }

    @Override
    public Provider convertToEntityAttribute(String code) {
        return code == null ? null : Provider.from(code);
    }
}
