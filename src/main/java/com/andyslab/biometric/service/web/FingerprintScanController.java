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

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.andyslab.biometric.service.api.FingerprintScanningEngine;
import com.andyslab.biometric.service.model.BiometricScanner;
import com.andyslab.biometric.service.model.Fingerprint;

import java.util.List;

/**
 * Provides web services for biometrics scanning
 */
@RestController
@CrossOrigin
@RequestMapping("/fingerprint")
public class FingerprintScanController {

    final FingerprintScanningEngine engine;

    FingerprintScanController(FingerprintScanningEngine engine) {
        this.engine = engine;
    }

    /**
     * @return Connected Fingerprint scanners list
     */
    @RequestMapping(method = RequestMethod.GET, value = "/devices")
    @ResponseBody
    public List<BiometricScanner> getScanners() {
        return engine.getFingerprintScanners();
    }

    /**
     * @return Fingerprint that is the result of a scan
     * 
     * @param type represents SGFingerPosition which is a
     *             string from "0" to "10"
     */
    @RequestMapping(method = RequestMethod.GET, value = "/scan")
    @ResponseBody
    public Fingerprint scan(@RequestParam(required = false) String type) {
        return engine.scanFingerprint(type);
    }
}
