/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.andyslab.biometric.service;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.andyslab.biometric.service.api.BiometricMatchingEngine;
import com.andyslab.biometric.service.model.BiometricSubject;
import com.andyslab.biometric.service.model.BiometricTemplateFormat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Tests for the matching engine
 */
public class BiometricMatchingEngineTest extends BaseBiometricTest {

    @Autowired
    BiometricMatchingEngine matchingEngine;

    @Test
    public void shouldGetTemplatesInVariousFormats() throws Exception {
        String subjectId = "101-01-1";
        BiometricSubject subject = loadSubjectFromResource(subjectId);
        matchingEngine.enroll(subject);

        // Get template in default format
        String defaultFormat = matchingEngine.getSubject(subjectId).getFingerprints().get(0).getTemplate();
        String secugenFormat = matchingEngine.getSubject(subjectId, BiometricTemplateFormat.SG400).getFingerprints()
                .get(0).getTemplate();

        // For now just verify that these extractions all work successfully, and produce
        // different template results
        assertThat(secugenFormat, is(defaultFormat));

        String isoFormat = matchingEngine.getSubject(subjectId, BiometricTemplateFormat.ANSI378).getFingerprints()
                .get(0).getTemplate();
        assertThat(isoFormat, not(defaultFormat));
    }
}
