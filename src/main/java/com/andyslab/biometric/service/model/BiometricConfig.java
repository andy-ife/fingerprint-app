/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.andyslab.biometric.service.model;

import java.io.Serializable;

/**
 * Encapsulates configuration of the Neurotechnology client
 */
public class BiometricConfig implements Serializable {

    public enum MatchingSpeed {
        LOW, MEDIUM, HIGH
    }

    public enum TemplateSize {
        COMPACT, SMALL, MEDIUM, LARGE
    }

    // ****** PROPERTIES *****

    private boolean matchingServiceEnabled = false;
    private boolean fingerprintScanningEnabled = true;
    private String licenseFilePath;
    private String sqliteDatabasePath;
    private Integer matchingThreshold;
    private MatchingSpeed matchingSpeed;
    private TemplateSize templateSize;
    private Integer deviceName;
    private Integer ajpPort;

    // ***** PROPERTY ACCESS *****

    public String getLicenseFilePath() {
        return licenseFilePath;
    }

    public boolean isMatchingServiceEnabled() {
        return matchingServiceEnabled;
    }

    public void setMatchingServiceEnabled(boolean matchingServiceEnabled) {
        this.matchingServiceEnabled = matchingServiceEnabled;
    }

    public boolean isFingerprintScanningEnabled() {
        return fingerprintScanningEnabled;
    }

    public void setFingerprintScanningEnabled(boolean fingerprintScanningEnabled) {
        this.fingerprintScanningEnabled = fingerprintScanningEnabled;
    }

    public void setLicenseFilePath(String licenseFilePath) {
        this.licenseFilePath = licenseFilePath;
    }

    public String getSqliteDatabasePath() {
        return sqliteDatabasePath;
    }

    public void setSqliteDatabasePath(String sqliteDatabasePath) {
        this.sqliteDatabasePath = sqliteDatabasePath;
    }

    public Integer getMatchingThreshold() {
        return matchingThreshold;
    }

    public void setMatchingThreshold(Integer matchingThreshold) {
        this.matchingThreshold = matchingThreshold;
    }

    public MatchingSpeed getMatchingSpeed() {
        return matchingSpeed;
    }

    public void setMatchingSpeed(MatchingSpeed matchingSpeed) {
        this.matchingSpeed = matchingSpeed;
    }

    public TemplateSize getTemplateSize() {
        return templateSize;
    }

    public void setTemplateSize(TemplateSize templateSize) {
        this.templateSize = templateSize;
    }

    public Integer getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(Integer deviceName) {
        this.deviceName = deviceName;
    }

    public Integer getAjpPort() {
        return ajpPort;
    }

    public void setAjpPort(Integer ajpPort) {
        this.ajpPort = ajpPort;
    }
}
