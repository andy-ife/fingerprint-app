package com.andyslab.biometric.service.api;

import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.andyslab.biometric.service.model.BiometricConfig;
import com.andyslab.biometric.service.model.BiometricScanSession;
import com.andyslab.biometric.service.model.Fingerprint;

@Component
public class ScanSessionManager {

    public static Log log = LogFactory.getLog(ScanSessionManager.class);

    final CacheManager cacheManager;
    final BiometricConfig config;

    ScanSessionManager(CacheManager cacheManager, BiometricConfig config) {
        this.cacheManager = cacheManager;
        this.config = config;
    }

    @Cacheable("scanSessionCache")
    public BiometricScanSession getSession(String uuid) {
        return new BiometricScanSession(uuid, new ArrayList<Fingerprint>());
    }

    @CachePut(value = "scanSessionCache", key = "#session.uuid")
    public BiometricScanSession updateSession(BiometricScanSession session) {
        return session;
    }

    @CacheEvict(value = "scanSessionCache", key = "#uuid")
    public void destroySession(String uuid) {
    }
}
