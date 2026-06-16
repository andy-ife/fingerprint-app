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
import java.util.Base64;
import java.util.List;

/**
 * Component that enables interaction with the devices for scanning and
 * extracting biometric templates
 */
@Component
public class FingerprintScanningEngine {

    static private final long TIMEOUT_IN_MS = 15000;
    static private final int[] MAX_TEMPLATE_SIZE = { 400 };

    protected final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    BiometricConfig config;

    static private JSGFPLib client = null;

    private SGDeviceInfoParam deviceInfo = null;
    private short secuGenTemplateFormat = SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400;

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

        if (client != null) {
            long error = -1;
            // Count devices
            int[] ndevs = new int[1];
            ndevs[0] = 0;
            SGDeviceList[] devList = new SGDeviceList[1];
            devList[0] = new SGDeviceList();

            error = client.EnumerateDevice(ndevs, devList);
            if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                System.out.println("EnumerateDevice() Success [" + error + "]");
                System.out
                        .println("# of devices: [" + devList[0].devID + " " + devList[0].devName + " "
                                + devList[0].devType + "]");
            } else {
                System.out.println("Error counting devices");
            }

            List<BiometricScanner> ret = new ArrayList<>();
            for (SGDeviceList device : devList) {
                BiometricScanner scanner = new BiometricScanner();
                scanner.setId(String.valueOf(device.devID));
                scanner.setDisplayName(String.valueOf(getDeviceName(device.devName)));
                scanner.setImageHeight(deviceInfo.imageHeight);
                scanner.setImageWidth(deviceInfo.imageWidth);
                ret.add(scanner);
            }
            return ret;
        } else {
            List<BiometricScanner> scanners = new ArrayList<BiometricScanner>();
            BiometricScanner scanner = new BiometricScanner();
            scanner.setDisplayName("Scanny");
            scanner.setId("shfskjhsfsf");
            scanners.add(scanner);
            return scanners;
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
            log.debug("Capturing fingerprint...");

            long res = client.GetImageEx(buffer, TIMEOUT_IN_MS, 0, targetQuality);

            if (res == SGFDxErrorCode.SGFDX_ERROR_NONE) {

                // Get information about finger
                SGFingerInfo fingerInfo = new SGFingerInfo();
                if (type == null) {
                    fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_UK;
                } else {
                    fingerInfo.FingerNumber = getFingerPosition(Integer.valueOf(type));
                }
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

    // ***** CONVENIENCE METHODS *****
    /**
     * @return Biometric client, configured with appropriate properties from
     *         configuration
     */
    private void initializeClient() {
        client = new JSGFPLib();
        long error = client.Open();
        error = client.Init(SGFDxDeviceName.SG_DEV_FDU05); // hamster u20
        if (client != null && error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            // Set template format
            secuGenTemplateFormat = getSecuGenTemplateFormat(config.getTemplateFormat());
            client.SetTemplateFormat(secuGenTemplateFormat);

            // Count Devices
            System.out.println("JSGFPLib Initialization Success");
            int[] ndevs = new int[1];
            ndevs[0] = 0;
            SGDeviceList[] devList = new SGDeviceList[1];
            devList[0] = new SGDeviceList();

            error = client.EnumerateDevice(ndevs, devList);
            if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                System.out.println("EnumerateDevice() Success [" + error + "]");
                System.out
                        .println("# of devices: [" + devList[0].devID + " " + devList[0].devName + " "
                                + devList[0].devType + "]");
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
        long error = -1;

        if (client != null) {
            List<BiometricScanner> scanners = getFingerprintScanners();
            if (scanners.size() == 0) {
                deviceInfo = null;
                System.err.println("No fingerprint scanners found");
                return;
            }
            client.OpenDevice(Long.parseLong(scanners.get(0).getId()));
            client.SetTemplateFormat(secuGenTemplateFormat);
            error = client.GetDeviceInfo(deviceInfo);
        }

        if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            deviceInfo.imageWidth = deviceInfo.imageWidth;
            deviceInfo.imageHeight = deviceInfo.imageHeight;
            System.out.println("Device Initialization Success");
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

    public static String getDeviceName(long value) {
        String driverWithText;

        if (value == SGFDxDeviceName.SG_DEV_UNKNOWN) {
            driverWithText = "Default";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU02) {
            driverWithText = "FDU02 USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU03) {
            driverWithText = "FDU03 / SDU03 USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU04) {
            driverWithText = "FDU04 / SDU04 USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU05) {
            driverWithText = "U20 USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU06) {
            driverWithText = "UPx USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU06AP) {
            driverWithText = "UPx-AP USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU07) {
            driverWithText = "U10 USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU08) {
            driverWithText = "U20-A USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU08A) {
            driverWithText = "U20-AP USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU09A) {
            driverWithText = "U30 USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU10A) {
            driverWithText = "U-Air USB driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDUSDA) {
            driverWithText = "U20-ASF-BT (Bluetooth SPP) driver";
        } else if (value == SGFDxDeviceName.SG_DEV_FDUSDA_BLE) {
            driverWithText = "U20-ASF-BT (Bluetooth BLE) driver";
        } else if (value == SGFDxDeviceName.SG_DEV_AUTO) {
            driverWithText = "Auto-detected";
        } else {
            driverWithText = "Unknown Device";
        }

        // Strip the word "driver" and trim spaces
        return driverWithText.replaceAll("(?i)\\bdriver\\b", "").trim();
    }

    private short getSecuGenTemplateFormat(BiometricTemplateFormat format) {
        short result = SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400;
        switch (format) {
            case SG400:
                result = SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400;
                break;
            case ANSI378:
                result = SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378;
                break;
            case ISO19794:
                result = SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794;
                break;
        }
        return result;
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
            System.out.println("Error disposing of biometric client/device");
        }
    }
}