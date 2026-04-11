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

/**
 * Represents a single fingerprint scan for a person
 */
public class Fingerprint extends BiometricSample {

    private String image;

    public Fingerprint() {
    }

    public Fingerprint(String type, BiometricTemplateFormat format, String template) {
        super.setType(type);
        super.setFormat(format);
        super.setTemplate(template);
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
