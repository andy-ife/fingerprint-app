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
import SecuGen.FDxSDKPro.jni.SGDeviceInfo;
import SecuGen.FDxSDKPro.jni.SGDeviceInfoParam;
import SecuGen.FDxSDKPro.jni.SGFDxDeviceName;
import SecuGen.FDxSDKPro.jni.SGFDxErrorCode;
import SecuGen.FDxSDKPro.jni.SGFDxTemplateFormat;
import SecuGen.FDxSDKPro.jni.SGFingerInfo;
import SecuGen.FDxSDKPro.jni.SGFingerPosition;
import SecuGen.FDxSDKPro.jni.SGImpressionType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.andyslab.biometric.service.exception.BadScanException;
import com.andyslab.biometric.service.exception.BiometricServiceException;
import com.andyslab.biometric.service.exception.DeviceNotFoundException;
import com.andyslab.biometric.service.exception.DeviceTimeoutException;
import com.andyslab.biometric.service.exception.ServiceNotEnabledException;
import com.andyslab.biometric.service.model.BiometricConfig;
import com.andyslab.biometric.service.model.BiometricScanner;
import com.andyslab.biometric.service.model.BiometricTemplateFormat;
import com.andyslab.biometric.service.model.Fingerprint;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * Component that enables interaction with the devices for scanning and
 * extracting biometric templates
 */
@Component
public class FingerprintScanningEngine {

    static private final long TIMEOUT_IN_MS = 5000;
    static private final int[] MAX_TEMPLATE_SIZE = { 400 };

    protected final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    BiometricConfig config;

    static private JSGFPLib client = null;

    private SGDeviceInfoParam deviceInfo = null;

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
            initializeDevices();
        }

        log.debug("Retrieving fingerprint scanners...");

        // Count devices
        int[] ndevs = new int[1];
        ndevs[0] = 0;
        long error = client.CountDevices(ndevs, 2000);
        if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            System.out.println("CountDevices() Success [" + error + "]");
            System.out.println("# of devices: [" + ndevs[0] + "]");
        } else {
            System.out.println("Error counting devices");
        }

        // Get devices
        SGDeviceInfo[] devList = new SGDeviceInfo[ndevs[0]];
        for (int i = 0; i < ndevs[0]; ++i)
            devList[i] = new SGDeviceInfo();
        error = client.FindDevices(devList, 1000);

        List<BiometricScanner> ret = new ArrayList<>();
        for (SGDeviceInfo device : devList) {
            BiometricScanner scanner = new BiometricScanner();
            scanner.setId(String.valueOf(device.ID));
            scanner.setDisplayName(String.valueOf(device.Name));
            ret.add(scanner);
        }
        return ret;
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
            long targetQuality = config.getMatchingThreshold();
            int[] actualQuality = new int[1];
            log.debug("Capturing fingerprint...");

            if (client.GetImageEx(buffer, TIMEOUT_IN_MS, 0, targetQuality) == SGFDxErrorCode.SGFDX_ERROR_NONE) {

                // Get information about finger
                SGFingerInfo fingerInfo = new SGFingerInfo();
                fingerInfo.FingerNumber = getFingerPosition(Integer.valueOf(type));
                fingerInfo.ViewNumber = 1;
                fingerInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
                client.GetImageQuality(deviceInfo.imageWidth, deviceInfo.imageHeight, buffer,
                        actualQuality);
                fingerInfo.ImageQuality = actualQuality[0];

                // If quality is too low, scan again
                if (fingerInfo.ImageQuality < 60) {
                    throw new BadScanException("Scan quality is too low. Please try again");
                }
                log.debug("Fingerprint captured successfully...");

                // Create template from captured image
                long err = client.GetMaxTemplateSize(MAX_TEMPLATE_SIZE);
                if (err != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    throw new BiometricServiceException("Error Getting Max Template Size");
                }
                byte[] minBuffer = new byte[MAX_TEMPLATE_SIZE[0]];
                log.debug("Extracting template...");
                err = client.CreateTemplate(fingerInfo, buffer, minBuffer);
                if (err != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    throw new BiometricServiceException("Error Creating SG400 Fingerprint Template");
                }

                // Create Fingerprint model
                fp.setTemplate(minBuffer.toString());
                fp.setImage(buffer.toString());
                fp.setFormat(BiometricTemplateFormat.SG400);
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

    // ***** CONVENIENCE METHODS *****
    /**
     * @return Biometric client, configured with appropriate properties from
     *         configuration
     */
    private void initializeClient() {
        client = new JSGFPLib();
        long error = client.Open();
        error = client.Init(SGFDxDeviceName.SG_DEV_AUTO);
        if (client != null && error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            // Set template format
            client.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);

            // Count Devices
            System.out.println("JSGFPLib Initialization Success");
            int[] ndevs = new int[1];
            ndevs[0] = 0;
            error = client.CountDevices(ndevs, 1000);
            if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                System.out.println("CountDevices() Success [" + error + "]");
                System.out.println("# of devices: [" + ndevs[0] + "]");
            } else {
                System.out.println("Error counting devices");
            }
        } else {
            client = null;
            System.err.println("JSGFPLib Initialization Error");
        }

    }

    private void initializeDevices() {
        deviceInfo = new SGDeviceInfoParam();
        client.OpenDevice(0);
        client.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);

        long error2 = client.GetDeviceInfo(deviceInfo);

        if (error2 == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            deviceInfo.imageWidth = deviceInfo.imageWidth;
            deviceInfo.imageHeight = deviceInfo.imageHeight;
        } else {
            deviceInfo = null;
            System.err.println("Device Initialization Error");

        }
    }

    /**
     * Maps the integer type to one of the SGFingerPosition constants.
     */
    private int getFingerPosition(int type) {
        switch (type) {
            case SGFingerPosition.SG_FINGPOS_RT:
                return SGFingerPosition.SG_FINGPOS_RT;
            case SGFingerPosition.SG_FINGPOS_RI:
                return SGFingerPosition.SG_FINGPOS_RI;
            case SGFingerPosition.SG_FINGPOS_RM:
                return SGFingerPosition.SG_FINGPOS_RM;
            case SGFingerPosition.SG_FINGPOS_RR:
                return SGFingerPosition.SG_FINGPOS_RR;
            case SGFingerPosition.SG_FINGPOS_RL:
                return SGFingerPosition.SG_FINGPOS_RL;
            case SGFingerPosition.SG_FINGPOS_LT:
                return SGFingerPosition.SG_FINGPOS_LT;
            case SGFingerPosition.SG_FINGPOS_LI:
                return SGFingerPosition.SG_FINGPOS_LI;
            case SGFingerPosition.SG_FINGPOS_LM:
                return SGFingerPosition.SG_FINGPOS_LM;
            case SGFingerPosition.SG_FINGPOS_LR:
                return SGFingerPosition.SG_FINGPOS_LR;
            case SGFingerPosition.SG_FINGPOS_LL:
                return SGFingerPosition.SG_FINGPOS_LL;
            default:
                return SGFingerPosition.SG_FINGPOS_UK;
        }
    }

    /**
     * Ensures a list of possible disposable objects are disposed of
     */
    private void dispose() {
        client = null;
        deviceInfo = null;
        client.Close();
        client.CloseDevice();
    }
}