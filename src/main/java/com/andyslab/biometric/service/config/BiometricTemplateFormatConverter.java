package com.andyslab.biometric.service.config;

import org.springframework.core.convert.converter.Converter;

import com.andyslab.biometric.service.model.BiometricTemplateFormat;

public class BiometricTemplateFormatConverter implements Converter<String, BiometricTemplateFormat> {
    @Override
    public BiometricTemplateFormat convert(String source) {
        return BiometricTemplateFormat.valueOf(source.toUpperCase());
    }
}
