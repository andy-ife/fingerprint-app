package com.andyslab.biometric.service.config;

import org.springframework.core.convert.converter.Converter;

import com.andyslab.biometric.service.model.ScanType;

public class ScanTypeConverter implements Converter<String, ScanType> {
    @Override
    public ScanType convert(String source) {
        return ScanType.valueOf(source.toUpperCase());
    }
}
