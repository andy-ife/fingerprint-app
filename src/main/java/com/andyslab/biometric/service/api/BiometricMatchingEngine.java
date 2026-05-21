/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.andyslab.biometric.service.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.andyslab.biometric.service.data.service.BiometricSubjectService;
import com.andyslab.biometric.service.exception.BiometricServiceException;
import com.andyslab.biometric.service.exception.ServiceNotEnabledException;
import com.andyslab.biometric.service.model.BiometricConfig;
import com.andyslab.biometric.service.model.BiometricMatch;
import com.andyslab.biometric.service.model.BiometricSubject;
import com.andyslab.biometric.service.model.BiometricTemplateFormat;
import com.andyslab.biometric.service.model.Fingerprint;
import com.secugen.secusearch.api.SSCandidate;
import com.secugen.secusearch.api.SSEngineParam;
import com.secugen.secusearch.api.SSException;
import com.secugen.secusearch.api.SSIdTemplatePair;
import com.secugen.secusearch.api.SecuSearch;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Component that enables interaction with the biometric matching service,
 * including enrollment, matching, and retrieval of templates
 */
@Component
public class BiometricMatchingEngine {

    protected final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    BiometricConfig config;

    @Autowired
    BiometricSubjectService backupDbService;

    /**
     * On startup, we ensure licenses are appropriately added and the server is
     * available
     */
    @PostConstruct
    public void startup() {
        initializeEngine();
    }

    @PreDestroy
    public void dispose() {
        terminateEngine();
    }

    /**
     * Saves a biometrics subject
     */
    public BiometricSubject enroll(BiometricSubject biometricSubject) {
        log.debug("Enrolling subject: " + biometricSubject.getSubjectId());

        if (biometricSubject.getSubjectId() == null) {
            biometricSubject.setSubjectId(UUID.randomUUID().toString()); // Setting subject id as a random uuid
        }

        if (biometricSubject.getFingerprints().isEmpty()) {
            throw new BiometricServiceException(
                    "Unable to enroll biometrics since subject does not contain any fingerprints");
        }

        try {
            BiometricSubject savedSubject = backupDbService.saveSubject(biometricSubject);
            boolean success = SecuSearch.getInstance()
                    .registerFPBatch(savedSubject.getFingerprints().stream().map(this::mapToSSIdTemplatePair)
                            .toArray(SSIdTemplatePair[]::new));

            // Check the result and handle errors if they occur
            if (success == true) {
                SecuSearch.getInstance().saveFPDB(config.getSqliteDatabasePath());
            } else {
                backupDbService.removeSubject(savedSubject);
                throw new BiometricServiceException("Unable to save the template");
            }
            log.debug("Template saved successfully for " + biometricSubject.getSubjectId());
        } catch (Exception e) {
            System.err.println("Error enrolling subject: " + e.toString());

        }

        return biometricSubject;
    }

    /**
     * Updates a biometrics subject
     * TODO: Needs testing
     */
    public BiometricSubject update(BiometricSubject biometricSubject) {
        log.debug("Updating subject: " + biometricSubject.getSubjectId());

        if (biometricSubject.getSubjectId() == null) {
            throw new BiometricServiceException("Unable to update template as subjectId is missing");
        }

        if (biometricSubject.getFingerprints().isEmpty()) {
            throw new BiometricServiceException("Unable to update template since no fingerprints are included");
        }

        try {
            BiometricSubject updatedSubject = backupDbService.updateSubject(biometricSubject);
            biometricSubject = updatedSubject;
            SecuSearch.getInstance().removeFPBatch(buildFingerprintIdList(updatedSubject.getFingerprints()));
            boolean success = SecuSearch.getInstance()
                    .registerFPBatch(updatedSubject.getFingerprints().stream().map(this::mapToSSIdTemplatePair)
                            .toArray(SSIdTemplatePair[]::new));
            if (success == true) {
                SecuSearch.getInstance().saveFPDB(config.getSqliteDatabasePath());
            } else {
                // the two dbs might be out of sync now? Sync strategy needed
                throw new BiometricServiceException("Unable to update the subject");
            }

            log.debug("Template saved successfully for " + biometricSubject.getSubjectId());
        } catch (Exception e) {
            System.err.println("Error updating subject: " + e.toString());
        }

        return biometricSubject;
    }

    /**
     * @return a List of BiometricsMatch that match the given biometricSubject,
     *         along with information on the match quality
     * 
     *         assume one finger per subject
     */
    public List<BiometricMatch> identify(BiometricSubject biometricSubject) {
        List<BiometricMatch> ret = new ArrayList<BiometricMatch>();

        log.debug("Identifying Matches for source template...");

        try {
            SSCandidate[] candidates = SecuSearch.getInstance()
                    .searchFP(Base64.getDecoder().decode(biometricSubject.getFingerprints().get(0).getTemplate()));

            if (candidates.length > 0) {
                log.debug("Found " + candidates.length + " possible matches");
                for (SSCandidate candidate : candidates) {
                    ret.add(new BiometricMatch(
                            backupDbService.getSubjectByFingerprintId(candidate.getId()).getSubjectId(),
                            candidate.getMatchScore()));
                }
            } else if (candidates.length == 0) {
                log.debug("No match found");
            } else {
                throw new BiometricServiceException("Identification failed");
            }
        } catch (Exception e) {
            System.err.println("Error matching subject: " + e.toString());
        }

        return ret;
    }

    /**
     * @return a count of all biometrics enrolled in the system
     *         assume one finger per subject
     */
    public Integer getNumberEnrolled() {
        try {
            int secuSearchDbNumber = SecuSearch.getInstance().getFPCount();
            int backupDbNumber = Integer.valueOf(backupDbService.getSubjectCount());
            if (secuSearchDbNumber != backupDbNumber) {
                throw new BiometricServiceException("Fingerprint databases out of sync");
            }
            return backupDbNumber;
        } catch (Exception e) {
            System.err.println("Error updating subject: " + e.toString());
        }
        return -1;
    }

    /**
     * @return the biometric template for the given subjectId with the default
     *         SecuGen format
     */
    public BiometricSubject getSubject(String subjectId) {
        return getSubject(subjectId, BiometricTemplateFormat.SG400);
    }

    /**
     * @return the biometric template for the given subjectId with the specified
     *         format.
     *         If format is null, it defaults to the SecuGen proprietary
     *         format
     */
    public BiometricSubject getSubject(String subjectId, BiometricTemplateFormat format) {
        log.debug("Retrieving subject: " + subjectId);
        BiometricSubject biometricSubject = null;

        try {
            biometricSubject = backupDbService.findSubjectBySubjectId(subjectId);
            if (biometricSubject != null) {
                log.debug("Found subject " + subjectId + ", extracting overall template in format: " + format);

            } else {
                log.debug("No saved subjects found for this id");
            }
            return biometricSubject;
        } catch (Exception e) {
            System.err.println("Error finding subject: " + e.toString());

        }

        return null;
    }

    /**
     * Deletes the subject associated with the given subjectId
     */
    public void deleteSubject(String subjectId) {
        log.debug("Deleting template for subject " + subjectId);

        try {
            BiometricSubject subject = backupDbService.deleteBySubjectId(subjectId);
            if (subject != null) {
                SecuSearch.getInstance().removeFPBatch(buildFingerprintIdList(subject.getFingerprints()));
                SecuSearch.getInstance().saveFPDB(config.getSqliteDatabasePath());
            }
            log.debug("No saved biometrics found for subject: " + subjectId);
        } catch (Exception e) {
            System.err.println("Error deleting subject: " + e.toString());

        }
    }

    // ***** CONVENIENCE METHODS *****

    private void terminateEngine() {
        try {
            SecuSearch.getInstance().terminateEngine();
        } catch (Exception e) {
            System.err.println("Error terminating engine: " + e.toString());
        }
    }

    private void initializeEngine() {
        if (!config.isMatchingServiceEnabled()) {
            throw new ServiceNotEnabledException("Biometric Enrollment, Identification, and Matching");
        }
        try {
            SecuSearch.getInstance().initializeEngine(new SSEngineParam(0, 10, config.getLicenseFilePath(), false));
            String db = config.getSqliteDatabasePath();

            try {
                boolean success = SecuSearch.getInstance().loadFPDB(db);
                if (new File(db).exists() && !success) {
                    // TODO: Somehow populate SecuSearch instance with backup db data
                    throw new BiometricServiceException("Error initializing fingerprint database");
                }
            } catch (SSException e) {
                try {
                    System.err.println("Error loading main database: " + e.getErrorCode() + e.toString());
                    // TODO: Somehow populate SecuSearch instance with backup db data
                } catch (Exception er) {
                    System.err.println("Error loading backup database: " + er.toString());
                }
            } catch (Exception e) {
                System.err.println("Error loading databases: " + e.toString());
            }
        } catch (Exception e) {
            System.out.println("Error creating biometric client: " + e.toString());
        }
    }

    private SSIdTemplatePair mapToSSIdTemplatePair(Fingerprint fingerprint) {
        byte[] templateBytes = new byte[400];
        templateBytes = Base64.getDecoder().decode(fingerprint.getTemplate());
        System.out.println("========== Byte array size: " + templateBytes.length);
        return new SSIdTemplatePair(fingerprint.getId(), templateBytes);
    }

    private int[] buildFingerprintIdList(List<Fingerprint> fingerprints) {
        int[] ids = new int[fingerprints.size()];

        for (int i = 0; i < fingerprints.size(); i++) {
            ids[i] = fingerprints.get(i).getId();
        }
        return ids;
    }

}