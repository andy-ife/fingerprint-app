package com.andyslab.biometric.service.model;

import java.util.List;
import java.util.UUID;

public class BiometricScanSession {
    private String uuid;
    private List<Fingerprint> fingerprints;

    public BiometricScanSession(String uuid, List<Fingerprint> fingerprints) {
        if (uuid == null) {
            this.uuid = UUID.randomUUID().toString();
        } else {
            this.uuid = uuid;
        }
        this.fingerprints = fingerprints;
    }

    public BiometricScanSession(List<Fingerprint> fingerprints) {
        this.uuid = UUID.randomUUID().toString();
        this.fingerprints = fingerprints;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public List<Fingerprint> getFingerprints() {
        return fingerprints;
    }

    public void setFingerprints(List<Fingerprint> fingerprints) {
        this.fingerprints = fingerprints;
    }

    public void addFingerprint(Fingerprint fingerprint) {
        this.fingerprints.add(fingerprint);
    }
}
