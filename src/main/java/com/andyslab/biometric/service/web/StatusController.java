/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.andyslab.biometric.service.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.andyslab.biometric.service.api.BiometricMatchingEngine;
import com.andyslab.biometric.service.model.BiometricConfig;
import com.andyslab.biometric.service.model.BiometricStatus;

/**
 * Provides web services for system status
 */
@RestController
@CrossOrigin
public class StatusController {

    @Autowired
    BiometricConfig config;

    @Autowired
    BiometricMatchingEngine engine;

    /**
     * @return the status of the system.
     */
    @RequestMapping("/status")
    @ResponseBody
    public BiometricStatus status() {
        BiometricStatus status = new BiometricStatus();
        try {
            Integer numEnrolled = engine.getNumberEnrolled();
            status.setNumberEnrolled(numEnrolled);
            status.setEnabled(true);
        } catch (Exception e) {
            status.setEnabled(false);
            status.setStatusMessage(e.getMessage());
        }
        return status;
    }
}
