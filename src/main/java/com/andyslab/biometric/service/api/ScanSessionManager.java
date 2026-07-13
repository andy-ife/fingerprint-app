package com.andyslab.biometric.service.api;

import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Component;

import com.andyslab.biometric.service.model.BiometricConfig;
import com.andyslab.biometric.service.model.BiometricScanSession;
import com.andyslab.biometric.service.model.ScanType;

@Component
public class ScanSessionManager {

    public static Log log = LogFactory.getLog(ScanSessionManager.class);

    final CacheManager cacheManager;
    final BiometricConfig config;

    ScanSessionManager(CacheManager cacheManager, BiometricConfig config) {
        this.cacheManager = cacheManager;
        this.config = config;
    }

    public BiometricScanSession getSession(String uuid, ScanType scanType) {
        Cache cache = cacheManager.getCache("scanSessionCache");
        if (cache != null && uuid != null && !uuid.equalsIgnoreCase("null")) {
            BiometricScanSession session = cache.get(uuid, BiometricScanSession.class);
            if (session != null) {
                return session;
            }
        }

        BiometricScanSession newSession = new BiometricScanSession(UUID.randomUUID().toString());

        if (cache != null) {
            cache.put(newSession.getUuid(), newSession);
        }

        newSession.setMaxCount(
                scanType == ScanType.REGISTRATION ? config.getScansRegistrationCount() : config.getScansSearchCount());
        return newSession;
    }

    @CachePut(value = "scanSessionCache", key = "#session.uuid")
    public BiometricScanSession updateSession(BiometricScanSession session) {
        return session;
    }

    @CacheEvict(value = "scanSessionCache", key = "#uuid")
    public void destroySession(String uuid) {
    }
}
