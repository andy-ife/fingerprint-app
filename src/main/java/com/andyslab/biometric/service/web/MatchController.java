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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.andyslab.biometric.service.api.BiometricMatchingEngine;
import com.andyslab.biometric.service.model.BiometricMatch;
import com.andyslab.biometric.service.model.BiometricSubject;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides web services for biometrics matching
 */
@RestController
@CrossOrigin
@RequestMapping("/match")
public class MatchController {

    final BiometricMatchingEngine engine;

    MatchController(BiometricMatchingEngine engine) {
        this.engine = engine;
    }

    /**
     * @return matches for the given subject. This is essentially a search for a
     *         template, with resulting possible matches
     */
    @PostMapping
    @ResponseBody
    public List<BiometricMatch> match(@RequestBody BiometricSubject subject) {
        List<BiometricMatch> matches = new ArrayList<>();
        if (subject != null) {
            matches = engine.identify(subject, null);
        }
        return matches;
    }
}
