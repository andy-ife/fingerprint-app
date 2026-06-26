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
import SecuGen.FDxSDKPro.jni.SGImpressionType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import com.andyslab.biometric.service.exception.BadScanException;
import com.andyslab.biometric.service.exception.BiometricServiceException;
import com.andyslab.biometric.service.exception.DeviceNotFoundException;
import com.andyslab.biometric.service.exception.DeviceTimeoutException;
import com.andyslab.biometric.service.exception.ServiceNotEnabledException;
import com.andyslab.biometric.service.model.BiometricConfig;
import com.andyslab.biometric.service.model.BiometricScanner;
import com.andyslab.biometric.service.model.Fingerprint;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
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

    static private JSGFPLib client = null;
    private SGDeviceInfoParam deviceInfo = null;

    FingerprintScanningEngine(BiometricConfig config) {
        this.config = config;
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
                scanner.setDisplayName(String.valueOf(Helper.getDeviceDislayName(device.devName)));
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
        return scanFingerprint(null);
    }

    /**
     * Scans a fingerprint using the given device, associating with the finger(s) of
     * the given type
     * Type is String.valueOf(SGFingerPosition)
     */
    public synchronized Fingerprint scanFingerprint(String type) {
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

        log.debug("Scanning fingerprint from device");

        try {
            byte[] buffer = new byte[deviceInfo.imageWidth * deviceInfo.imageHeight];
            long targetQuality = config.getScanningThreshold();
            int[] actualQuality = new int[1];
            int[] maxTemplateSize = new int[1];
            log.debug("Capturing fingerprint...");

            long res = client.GetImageEx(buffer, config.getScanTimeoutMs(), 0, targetQuality);

            /**
             * TODO: We should scan 2-3 times and compare the fingerprints for higher
             * accuracy
             * This can be done using JSGFPLib.MatchTemplate() (SG400 only)
             * Or JSGFPLIb.MatchTemplateEx() (SG400, ANSI378, ISO19794)
             */

            if (res == SGFDxErrorCode.SGFDX_ERROR_NONE) {

                // Get information about finger
                SGFingerInfo fingerInfo = new SGFingerInfo();
                if (type == null) {
                    fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_UK;
                } else {
                    fingerInfo.FingerNumber = Helper.getFingerPosition(Integer.valueOf(type));
                }
                fingerInfo.ViewNumber = 1;
                fingerInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
                client.GetImageQuality(deviceInfo.imageWidth, deviceInfo.imageHeight, buffer,
                        actualQuality);
                fingerInfo.ImageQuality = actualQuality[0];

                // If quality is too low, scan again
                if (fingerInfo.ImageQuality < targetQuality) {
                    throw new BadScanException("Scan quality is too low. Please try again");
                }
                log.debug("Fingerprint captured successfully...");

                // Create template from captured image
                long err = client.GetTemplateSize(buffer, maxTemplateSize);
                err = client.GetMaxTemplateSize(maxTemplateSize);
                if (err != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    throw new BiometricServiceException("Error Getting Max Template Size");
                }
                byte[] minBuffer = new byte[maxTemplateSize[0]];
                log.debug("Extracting template...");
                err = client.CreateTemplate(fingerInfo, buffer, minBuffer);
                if (err != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    throw new BiometricServiceException("Error Creating SG400 Fingerprint Template");
                }

                // Create Fingerprint model
                String b64Template = Base64.getEncoder().encodeToString(minBuffer);
                String b64Image = Base64.getEncoder().encodeToString(buffer);
                fp.setTemplate(b64Template);
                fp.setImage(b64Image);
                fp.setFormat(config.getTemplateFormat());
                fp.setType(type);
            }

        } catch (DeviceTimeoutException e) {
            throw e;
        } catch (BadScanException e) {
            throw e;
        } catch (Exception e) {
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
     * Ensures a list of possible disposable objects are disposed of
     */
    private void dispose() {
        try {
            if (client != null)
                client.Close();
            // if (deviceInfo != null)
            // client.CloseDevice();
            client = null;
            deviceInfo = null;
        } catch (Exception e) {
            log.error("Error disposing of biometric client/device");
        }
    }
}