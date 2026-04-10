/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.pih.biometric.service.model;

import java.io.Serializable;

import SecuGen.FDxSDKPro.jni.SGDeviceInfoParam;

/**
 * Simple bean to encapsulate a Device capable of extracting a Biometric
 * template
 */
public class BiometricScanner implements Serializable {

    private String id;
    private String displayName;
    private String firmwareVersion;
    private int brightness;
    private int imageDpi;
    private int imageHeight;
    private int imageWidth;

    public BiometricScanner() {
    }

    public BiometricScanner(SGDeviceInfoParam device) {
        this.id = String.valueOf(device.deviceID);
        this.displayName = "Device #" + String.valueOf(device.deviceSN());
        this.firmwareVersion = String.valueOf(device.FWVersion);
        this.brightness = device.brightness;
        this.imageDpi = device.imageDPI;
        this.imageHeight = device.imageHeight;
        this.imageWidth = device.imageWidth;
    }

    @Override
    public String toString() {
        if (id != null) {
            return id;
        }
        if (displayName != null) {
            return displayName;
        }
        return super.toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String make) {
        this.firmwareVersion = make;
    }

    public int getBrightness() {
        return brightness;
    }

    public void setBrightness(int brightness) {
        this.brightness = brightness;
    }

    public int getImageDpi() {
        return imageDpi;
    }

    public void setImageDpi(int imageDpi) {
        this.imageDpi = imageDpi;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }
}
