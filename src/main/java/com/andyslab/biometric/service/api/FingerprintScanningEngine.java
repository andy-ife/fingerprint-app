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

import SecuGen.FDxSDKPro.jni.JSGFPLib;
import SecuGen.FDxSDKPro.jni.SGDeviceInfoParam;
import SecuGen.FDxSDKPro.jni.SGDeviceList;
import SecuGen.FDxSDKPro.jni.SGFDxErrorCode;
import SecuGen.FDxSDKPro.jni.SGFingerInfo;
import SecuGen.FDxSDKPro.jni.SGFingerPosition;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import com.andyslab.biometric.service.exception.BadScanException;
import com.andyslab.biometric.service.exception.BiometricServiceException;
import com.andyslab.biometric.service.exception.DeviceNotFoundException;
import com.andyslab.biometric.service.exception.DeviceTimeoutException;
import com.andyslab.biometric.service.exception.DuplicateSubjectException;
import com.andyslab.biometric.service.exception.ServiceNotEnabledException;
import com.andyslab.biometric.service.model.BiometricConfig;
import com.andyslab.biometric.service.model.BiometricMatch;
import com.andyslab.biometric.service.model.BiometricScanSession;
import com.andyslab.biometric.service.model.BiometricScanner;
import com.andyslab.biometric.service.model.BiometricSubject;
import com.andyslab.biometric.service.model.Fingerprint;
import com.andyslab.biometric.service.model.ScanType;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Component that enables interaction with the devices for scanning and
 * extracting biometric templates
 */
@Component
public class FingerprintScanningEngine {
    protected final Log log = LogFactory.getLog(this.getClass());

    final BiometricConfig config;
    final ScanSessionManager sessionManager;
    final BiometricMatchingEngine matchingEngine;

    static private JSGFPLib client = null;
    static private SGDeviceInfoParam deviceInfo = null;

    FingerprintScanningEngine(
            BiometricConfig config,
            ScanSessionManager manager,
            BiometricMatchingEngine matchingEngine) {
        this.config = config;
        this.sessionManager = manager;
        this.matchingEngine = matchingEngine;
    }

    @PostConstruct
    public void init() {
        if (config.isFingerprintScanningEnabled()) {
            log.debug("Started fingerprint scanning engine initialization...");
            initializeClient();
            initializeDevices();
            log.debug("Completed fingerprint scanning engine initialization...");
        }
    }

    @PreDestroy
    public void destroy() {
        if (config.isFingerprintScanningEnabled()) {
            log.debug("Started fingerprint scanning engine destroy...");
            dispose();
            log.debug("Ended fingerprint scanning engine destroy...");
        }
    }

    /**
     * Retrieves all connected Fingerprint Scanners
     */
    public synchronized List<BiometricScanner> getFingerprintScanners() {

        if (!config.isFingerprintScanningEnabled()) {
            throw new ServiceNotEnabledException("Fingerprint Scanning");
        }

        if (deviceInfo == null) {
            throw new DeviceNotFoundException();
        }

        if (client != null) {
            long error = -1;
            // Count devices
            int[] ndevs = new int[1];
            ndevs[0] = 0;
            SGDeviceList[] devList = new SGDeviceList[1];
            devList[0] = new SGDeviceList();

            error = client.EnumerateDevice(ndevs, devList);
            if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                log.debug("EnumerateDevice() Success [" + error + "]");
                log.debug("# of devices: [" + devList[0].devID + " " + devList[0].devName + " "
                        + devList[0].devType + "]");
            } else {
                log.error("Error counting devices");
            }

            List<BiometricScanner> ret = new ArrayList<>();
            for (SGDeviceList device : devList) {
                BiometricScanner scanner = new BiometricScanner();
                scanner.setId(String.valueOf(device.devID));
                scanner.setDisplayName(Helper.getDeviceDisplayName(device.devName));
                scanner.setImageHeight(deviceInfo.imageHeight);
                scanner.setImageWidth(deviceInfo.imageWidth);
                ret.add(scanner);
            }
            return ret;
        } else {
            throw new DeviceNotFoundException();
        }
    }

    /**
     * Scans a fingerprint.
     */
    public Fingerprint scanFingerprint() {
        return scanFingerprint(null, null, ScanType.REGISTRATION);
    }

    /**
     * Scans a fingerprint using the given device, associating with the finger(s) of
     * the given type<br>
     * <br>
     * <b>type</b> is the position of the finger e.g left middle finger, right index
     * finger<br>
     * A scan session is composed of 2-3 (configurable) scans of the same finger for
     * maximum
     * accuracy. <b>sessionId</b> is the id of such a session<br>
     * <b>scanType</b> represents the purpose of the scan e.g registration, search,
     * match<br>
     */
    public synchronized Fingerprint scanFingerprint(String type, String sessionId, ScanType scanType) {
        Fingerprint fp = new Fingerprint();

        if (!config.isFingerprintScanningEnabled()) {
            throw new ServiceNotEnabledException("Fingerprint Scanning");
        }

        // if there's no device attached, try to find it, but if still no device fail
        if (client == null || deviceInfo == null) {
            initializeClient();
            initializeDevices();
            if (client == null || deviceInfo == null) {
                throw new DeviceNotFoundException();
            }
        }

        // Check for existing sessions
        int viewNumber = 0;
        int maxSize = scanType == ScanType.REGISTRATION ? config.getScansRegistrationCount()
                : config.getScansSearchCount();
        BiometricScanSession session = sessionManager.getSession(sessionId, scanType);
        if (session != null && session.getFingerprints() != null && session.getFingerprints().size() < maxSize) {
            viewNumber = session.getFingerprints().size() + 1;
        }

        log.debug("Scanning fingerprint...");

        try {
            // Initialize vars
            byte[] buffer = new byte[deviceInfo.imageWidth * deviceInfo.imageHeight];
            long targetQuality = config.getScanningThreshold();
            int[] actualQuality = new int[1];
            int[] maxTemplateSize = new int[1];
            int timeout = config.getScanTimeoutMs();
            int impressionType = config.getImpressionType();
            log.debug("Capturing fingerprint...");

            // Do the scan
            long res = client.GetImageEx(buffer, timeout, 0, targetQuality);

            if (res == SGFDxErrorCode.SGFDX_ERROR_NONE) {

                // Get information about finger
                SGFingerInfo fingerInfo = new SGFingerInfo();
                if (type == null) {
                    fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_UK; // Unknown finger
                } else {
                    fingerInfo.FingerNumber = Helper.getFingerPosition(Integer.valueOf(type));
                }
                fingerInfo.ViewNumber = viewNumber;
                fingerInfo.ImpressionType = Helper.getImpressionType(impressionType);
                client.GetImageQuality(deviceInfo.imageWidth, deviceInfo.imageHeight, buffer,
                        actualQuality);
                fingerInfo.ImageQuality = actualQuality[0];

                // If quality is too low, scan again
                if (fingerInfo.ImageQuality < targetQuality) {
                    throw new BadScanException("Poor scan detected. Please adjust your finger's position");
                }
                log.debug("Fingerprint captured successfully...");

                // Create template from captured image
                long err = client.GetTemplateSize(buffer, maxTemplateSize);
                err = client.GetMaxTemplateSize(maxTemplateSize);
                if (err != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    throw new BadScanException("Error Getting Max Template Size");
                }
                byte[] minBuffer = new byte[maxTemplateSize[0]];
                log.debug("Extracting template...");
                err = client.CreateTemplate(fingerInfo, buffer, minBuffer);
                if (err != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    throw new BadScanException("Error Creating SG400 Fingerprint Template");
                }

                // verify the print
                List<Fingerprint> fingerprints = session.getFingerprints();
                Fingerprint prevFp1 = (fingerprints.size() > 0) ? fingerprints.get(0) : null;
                Fingerprint prevFp2 = (fingerprints.size() > 1) ? fingerprints.get(1) : null;
                Fingerprint cur = new Fingerprint(config.getTemplateFormat(),
                        Base64.getEncoder().encodeToString(minBuffer));
                boolean matched = false;

                if (prevFp1 != null && prevFp2 != null) {
                    // match 1 and cur && 2 and cur
                    boolean match1 = Helper.verifyFingerprints(client, prevFp1, cur);
                    boolean match2 = Helper.verifyFingerprints(client, prevFp2, cur);
                    matched = match1 && match2;
                } else if (prevFp1 != null && prevFp2 == null) {
                    // match 1 and cur
                    boolean match1 = Helper.verifyFingerprints(client, prevFp1, cur);
                    matched = match1;
                } else {
                    matched = true;
                }

                if (!matched) {
                    throw new BadScanException(
                            "This fingerprint does not match the others. Please adjust your finger's position");
                }

                // Create Fingerprint model
                String b64Template = Base64.getEncoder().encodeToString(minBuffer);
                String b64Image = Base64.getEncoder().encodeToString(buffer);
                fp.setTemplate(b64Template);
                fp.setImage(b64Image);
                fp.setFormat(config.getTemplateFormat());
                fp.setType(type);
                fp.setQuality(actualQuality[0]);

                // if we're registering, check that the print doesn't exist
                if (scanType == ScanType.REGISTRATION && viewNumber >= config.getScansRegistrationCount()) {
                    List<Fingerprint> options = session.getFingerprints();
                    options.add(fp);
                    Fingerprint best = Helper.getBestFingerprint(options);
                    List<BiometricMatch> existingMatches = matchingEngine
                            .identify(new BiometricSubject(new ArrayList<Fingerprint>(Arrays.asList(best))), scanType);
                    if (!existingMatches.isEmpty())
                        throw new DuplicateSubjectException("");
                }

                // update or destroy the session
                if (scanType == ScanType.REGISTRATION && viewNumber >= config.getScansRegistrationCount()
                        || scanType == ScanType.SEARCH && viewNumber >= config.getScansSearchCount()) {
                    sessionManager.destroySession(sessionId);
                } else {
                    session.addFingerprint(fp);
                    sessionManager.updateSession(session);
                }
            }

        } catch (DeviceTimeoutException e) {
            throw e;
        } catch (BadScanException e) {
            throw e;
        } catch (Exception e) {
            // FATAL errors
            sessionManager.destroySession(sessionId);
            client = null;
            deviceInfo = null;
            throw new BiometricServiceException("Error capturing fingerprint:", e);
        }

        return fp;
    }

    // ***** LIFECYCLE METHODS *****
    /**
     * @return Biometric client, configured with appropriate properties from
     *         configuration
     */
    private void initializeClient() {
        client = new JSGFPLib();
        long error = client.Open();
        error = client.Init(config.getDeviceName());
        if (client != null && error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            // Set template format
            client.SetTemplateFormat(Helper.getSecuGenTemplateFormat(config.getTemplateFormat()));

            // Count Devices
            log.debug("JSGFPLib Initialization Success");
            int[] ndevs = { 0 };
            SGDeviceList[] devList = { new SGDeviceList() };

            error = client.EnumerateDevice(ndevs, devList);
            if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                log.debug("EnumerateDevice() Success [" + error + "]");
                log.debug("# of devices: [" + devList[0].devID + " " + devList[0].devName + " "
                        + devList[0].devType + "]");
            } else {
                log.error("Error counting devices");
            }
        } else {
            client = null;
            log.error("JSGFPLib Initialization Error");
        }

    }

    private void initializeDevices() {
        deviceInfo = new SGDeviceInfoParam();
        long error = -1;

        if (client != null) {
            List<BiometricScanner> scanners = getFingerprintScanners();
            if (scanners.size() == 0) {
                deviceInfo = null;
                log.error("No fingerprint scanners found");
                return;
            }
            client.OpenDevice(Long.parseLong(scanners.get(0).getId()));
            client.SetTemplateFormat(Helper.getSecuGenTemplateFormat(config.getTemplateFormat()));
            error = client.GetDeviceInfo(deviceInfo);
        }

        if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            deviceInfo.imageWidth = deviceInfo.imageWidth;
            deviceInfo.imageHeight = deviceInfo.imageHeight;
            log.debug("Device Initialization Success");
        } else {
            deviceInfo = null;
            log.error("Device Initialization Error");

        }
    }

    /**
     * Dispose client and device
     */
    private void dispose() {
        try {
            if (client != null) {
                client.CloseDevice();
                client.Close();
            }
            client = null;
            deviceInfo = null;
        } catch (Exception e) {
            log.error("Error disposing of biometric client/device: " + e.toString());
        }
    }
}