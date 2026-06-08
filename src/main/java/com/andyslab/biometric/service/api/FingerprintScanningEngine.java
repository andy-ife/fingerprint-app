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
                // Fingerprint dummyFp = new Fingerprint("7", BiometricTemplateFormat.SG400,
                // "fDmKTpTJPXKuiqqV9jQZL3Os/VaGzlJ4h+RLhvhbI9w9cvAOnB8euNwkvgGtT7FlsKTwYZxvY5izxvKB04achfTAT6z2k/tf/+RwUIzW8vdX+6rlpCxMucUmxsyxCfqApCtZe8iHMRXISHPlKgyKnHk/ebhsjIrwxb5kPYijiY4djTwP+zdbRJCiMCX4HA1CfjcfscxGzbi2sgujN7NdHXz/tstZ/DMt/eFXHvOh1b57dnHDFQLkPjfe3h77mgPCGy3J668NX2AqZP9H8Gz5fBstyeuvDV9gKmT/R/Bs+XwbLcnrrw1fYCpk/0fwbPl8Gy3J668NX2AqZP9H8Gz5fBstyeuvDV9gKmT/R/Bs+XwbLcnrrw1fYCpk/0fwbPl8Gy3J668NX2AqZP9H8Gz5fBstyeuvDV9gKmT/R/Bs+XwbLcnrrw1fYCpk/0fwbPl8Gy3J668NX2AqZP9H8Gz5fBstyeuvDV9gKmT/R/Bs+XwbLcnrrw1fYCpk/0fwbPl8Gy3J668NX2AqZP9H8Gz5fA==");
                // dummyFp.setImage(
                // "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxMTEhUTExIWFhUXGCAaGRgYGB4eIRwcIB8eICIgHyAgHyghISAlHR0dITEhJyovLi4uHiAzODMsNygtLi0BCgoKDg0NDg0NDysZFRkrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrK//AABEIARIAuAMBIgACEQEDEQH/xAAbAAACAwEBAQAAAAAAAAAAAAAABAMFBgIBB//EAEAQAAIBAgUCBAQEAwUIAgMAAAECAwQRAAUSITETQSJRYXEGFDKBI0KRoRUzUiRDYnKxNFNjgsHR4fEWkgdz8P/EABUBAQEAAAAAAAAAAAAAAAAAAAAB/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEQMRAD8A+44MGDAGDBgwBgwYL4AwrU5hFHq1youldbamAst7ajftcEXwn8QSVAT+zvDGTe8kxNlPbbvfcc7c74+ezZpWa3Wozegj27KHvzYWIG3O+9vLAXWc/FyySxfJ5nRov5ll/Mb/ANXbba3OHoc9rhB1FghrHaQhRSyrpVR/UzkXN9rDGRFRFpGvNcsfi6NTLYn/ADB7/e2Lr4XabUSmZUWghtEMKDSTbYkkgixsTsb4DQZx8XR0cMMtVFLGZBuqgPoawuGINv3xSUHxblFpljrHQzEszN1RZm7qWFlI9PLEdFJm/wCN1/lZ1VCY4xpPUbtYDe3vilmrqh7/ADHw8rMQVung2I3/ACt+uAtoajMI3U0VZDXwFbfiyRhrj1UC/vh6l/8AyKiO0VbTy00imx8JkXjzS/7Xx87TLKBSWqKevo35XT+Iq77ENoDfY4+h5TmtTLTL8tVUtc43ZHQxkrfYfUbEW31Dc+WA12WZpDUIHhlV1IuCp/1HI++HMfNcybLIJkFRDJRVDjUzRFlUMRc3dbK9iebWxcVVZXRIktGUr4NCizNaRj3YMo0m/tt5YDZYMUNF8V07zfLO3SqQoLRPcWJF7BiAG+2L7AGDBgwBgwYMAYMGDAGDBgwBgwYMAYQzfNIYF1TTrEDwWIHftfn9MP4weZ1WipkFLlslVKW8c0oIRd91VpNtI8l28r4BDNc6yzU6mOeueWUMISGcAre2gGwC7nbC8lDVAkUeSU0KE6iZwrFr+gYafa+Pc3qq4o0tRmUUKIyh4qRS7rvaxYeIXO1+L4SqaGmlTrR5fX1dyRrmlI32/qbVb1tbAWFTT5lGb9DKYwRvcWuO/J4xMlL1V6kuV0UwUWBppULXPNrhQP8A7XGKN8vZ0QSZBIQoCoUlJOi5PN/Uny3xa0/w7l7SmBsrqo9geppYqCRxqBIt6+YwFfT/AA7C1Ska0GYU2om8qS3C89zqGn73xDVZjSxFo0zbMImW48akjne2wPPfDeRim0yRU2Z1lNo1FVmBCKBydxpt6EjDUFTXzERhqDM4VHdk1EjuQSbN68e2A9yeszFgDTZhS1y/7qQBWsO/Aa/rvhpKingfrV2X/KTKdp4RdDfuWW2/owPbFFm1FS3JrcompFG3VgsVB9dO1vXDvw7PFZY6LNAynY01Yt9Xeyg2I+1xgH6uqnSEyt0c1pF3uQvUQethZtvQHFR8P5XTzlv4XmE1PN9Zp2Oy2PBHkCfXHeZ01Mk4d4anK5r260Y/BJ918Ok+fFubYtq6uk06K6lSqgK2NXTgNpB7kC7Lb0wHS5Y9cjx5rSmJ4BqFVEQuoC/B3I23I49sW2V18lPCH6prKfUAjxjU6R22L2+u2wuBf0xRZHPPDC70VSuZUymzQuT1EW3Cludvyke3lib4IhpjUtJl9ToiI1T0jqbgm9it7ad9r7jAb+lqUkUOjBlbcEYlxnIs41LKlJEBPEbtTyWjJu31bbWbxWbgnvi0yrNo59YQ+KNtEi90ccg/9+DgH8GDBgDBgwYAwYMGAMGDHhwGYz34tMcjQU1PJUzi11UWVb/1Mf1sL/bGc+Iqioa65jXw0kXeGnJZ2Hudx9hjV/ERZYy7VApYV/mOoBdt9rMdluPQnft3wFdT04kUUeWSVU0viMlWshW3mC//AIwBT1dGAP4WtWpt+IYYLtJ5Xkk2G/ocXlTl/wAwhkr4RCyxBIjPU3Dnc3kRdIvc7m3+mKueKSM6K/N1g0i5p6YBSBbYCw8uBviE0NKUJiyutrLOAHqHZfqF9rkbeoXARU9XMkJjGfUkSpYKka3AA2AB2IH2OLehkrBZmzylNxwUDDfy8Qx7lVPVxKOlk9JEdRujSeJh5jba3mb4impKx3YyZJRNcXvqH7m25+wwE/z2bKxs9FWRWswVlW/uD3/UYrqmljkfXVZXPTOoFp6RtQG/JC/9AcGX5BT1UiwTZTPSORvJCxCbcb8HjuMaOiymlypWHzsiPONKNOwZQwBsQLAbX7nALPk9fBZkzRGiawVKqPsd7E+f2wvmyK6EZjlg6aC4qKUhgP8AFtZx++G1yeertBWS0dbCrXJW6SrtsQUawO/kLg4Wo4YobxU89TRWcBlqFZ4ioPCs918V+zceWAlyNtcZNBVJV0+mxpqhvEDbgMQSPZh9ximyCGKCeRIWfL6p7f2eoIeKQXv4TYHzA3v6EYuvi6ClWNHqYWg1ObVNN+TfwlmFjYixsQRheOGSaG0yx5nS7aZY7LMp9dxcgG9wQffAQ5hSF5wsYFBmA3VkBMNQoF+QALE32IuLd8aEVECVKLKqQ5g8BtIAdBJ2IDG2qzWNjhOHJHIWjMiy0brcCR2WoiHNgb3IB8xceeKzN1an00mYp16Nm0x1XDwj8usgbEEL4ri/rgLit6TSQLWaoKsKCKiK6ox7qH4IPOhh7cYSzLM2Se7KKWUOD1wC0M8N99bAeE2vzwe+InSSmgeKtf5vLnUBZwLugP8AXY/SB+Yce2IEeoy5ENNqr8tZTt4XdL3vYgC6+h2G/GA+g5bmMU6dSGRZEuRqU3FxhvGZyF45nSpopUELAiaLSPqt4dhurg8+Y+xxphgDBgwYAwYMGA8OKylz6nkaUJICIf5j/kXm41fSSLG9jtizIxgamsalVCqJDTaG00OhTLK5J2sCQAeTzYXwEtT8WLLMP4fStVsy2aU6liAUkgaiNOxJO374rc5qSSBX5jY6WPytFquediy3J8t7C+Jvm1npQcxiFBArgxrHIys/IsUUXtvf18sR1fwwOmjZbVR0kEw8RZSrtc8gtZht22wCNBTmKMtTZckIBDGozJgDftYkX2HsMFTn5C/2jOGMrGwjoVVwAfUDc+t78YZrfgRwwHy71hXiWpqiFv8A5Bc+mKerzaSAdL5ymgdSRppaRpClu2vbAK5jFG0q6XzWqktp4dCBcbXIv9uOMNRfDsZF2oKxebtPVJGW9gxBOGcvzeIPHK+aZhI4Iuq07BSLjYroOx4ve/tiGuWWunuZeqIwSBNQS3UE8WFwf1wFtQZ00FFJDFR1caIGPUWWORkvvceIm32tziq+E80qZ3UJXGXUfDHV07MpPkJBcA274q6baR4jS0hDnSVKSU7N5aWYaRfyvi0gMUBCrLXZayNurhpoifS21vXYYCwzXK0eQtPl5DctNQSaiCNrMose1+MSDNpZYWip5Y8wi0Wkp6j8Of2tYFtu9r+uERFM0pm0RVQF7T5e4jkU8nUt/FfYlTt64SdWqKhAZIZ3A+mpjamn8/C4Gkm22oH7YDTZLJGyNBSu9LLq3pqsEq3hN0RWN9P+X3wllcsEMqiaNsrqAD4kFoJRx/kPpfjzxY02YVk0kjIjxsgBalqVXQVO34cwGxI877nyOEMoEy9VYFaRFF5svqrlkHA6T2IINthxgHPi8QiRZaqmYxmwWupmIZB21lbEDc77i2GMxzBoqYtKy19DJdWdF1Oqm/iYrdWC+e1tsV/wzKpDx0ctySepQVm2je5CWB2/UccYYrKeKkqFFPN8s8o1GF1LU8jWN01bBW2tfb2PGAhocvqKSFZMskFZRubtC9iQvfQTbfa2k9+3ODLZ3INRlLDQCTUUUlwdXJCKfoO54FicWlVBO6pLE3yNVYosLMrRSfm2A2JPZhYj1xQVDCeWJo5FoM1G0iOpCzelxsym1xa55GAareo4/ieVSO2pg1RTAjxWFiCvIewtbk9sbvJM0SpiWVNrjxKfqRu6sOQQexx87qM5EbM1AiQ1ayH5mlcAGZu5j38VjcgjtvbtjS/CAhnmevgcr1Ywk0G3hlBvdt/qA2/XAa7BgwYAwYMBwCtRWoqlmdVUXBYkWB8jvz6Yoano0ssMkzo7spjEzreZ2J2CBVsBub/bFFnKvJVkuQ0sYLJc/wBnpQOJJCbBpfzWPG3YXxLW0VPTQStMZpZZSqh1k1S1BNjpiAuyoSbWW218BdVTRrLcoJGiXQ9TOVAQG5sNgC17XCgdt9sYGuCzuZUX5m7C9XWDRBHpHEcY032Hrf1xNnNcZXENRG9VNs0dFGbRwi23Vflmtzc7YmiVZ2RZkFfNEbGKOy01OvkzW0sALeZ2OxtgK4V9bUzq0M01UYnDMxAiprD2sSLjnbvh+vzeqW7z5pRQ3/JBGJD9uTf1vhOvqYp30mWestYCkolZYFte242IHmOcRHSqkOmWZfbhGjWab7jex453OA8iqVEgbrZtUq24McOgEn1I49sWVNDKqkBM5F+CZIR4T28Xt74hDVE8I6cmaVLE8qvQjt6E2FvvhBcmlQapaCO//HrwP1Gsb4Cealke5K5wd9t4W3/TBHnRjsgzKqgJtZK6nBF/81rW9hj2OgVgClPTq9rf2fMgDf2J/wCuJkppIUGtMwjN7m+mqj48t9j9jgH8mD6pJJYqWodoynUonVZtLcnTcA9txYj1xHHGb9JKsOSLClzKPxW9H2P3H74ipvh2OqYuIo3IBYzUr9GQG2waE/SbgC4tiJZJJA0Czw1DIbdDMowso2GyPe539cA/HVNEwTXJSSMVURT/AItI5/wvyLnceIH0xPLV6FmiqxWQRuwZagEOsbD+iRbtoJ3Aftcd8U9FN0T0NRpiR/slcNcDHv03Oyjm3/XFtQSyJIqo3yTve0D/AIlNLb/dN9Kk24FjvxgIYqR6ltU6xhguunzODYHTx1N/SxvYemG6Vz02hkpV+YdWfoFgYagkrqeNjqIOm9lFuT74Xi1RyPDToI5jvPQyn8GUEWJhYi2/O3PcYq6Qp05GjMhpYzeWlZrTUjj+8iJ8VlN9h2vzgLQ5ovy8r6ZZaRWEc1M/82lIHKMDcqNu9+4O2JM5jhnij+bfXA29PXpYNHc3CybbHa2rg97Hcw5fWPrCrIgqnXVBU2GisjFzokHGvt5/rbHeWdOUyiNOnDa1dQyCwQm5aVL727+G17edsBxmuTfMPFS1jGGtT/Z6pQNMwHAPfVwSNjfcXxp8p+GY4pkmUmKffraTtOALElNRA8RDX5F/XFFPTwRxLDPUvLRzMppajXcwOOB1OQL8EnzGLbK6Q/MwCrJaoiVujUJcLNGRurdtQ5IPPI74DYjBgwYAxBWRM6MquULCwYDcX7j1xOcZqesqJNUsakAEpAGNlNwdUso2OkWsB7ne4sCOa0Ugg6BhSQa7RR6iVW12Ms7m1+dRXz23xl4KqHpTvJLMQVCNXiweRgwGinSx8JFxcW8/XErGOaIgvK8LSk7XWWtm76d7LCBtbyHO26j1XUmuvS66pYynaCjW99KC1nktcX8x9sBA0MfTIkZqamK6lgVgamqv/vNywvtsdsc1LF4Pl5I2jjXxR0dPbXpHDVD32Bt744TMVHUmieREc6Jq6oF3fyFOo4uL779vLCc9cEQQxoqwyNq6bt+NULckNO35U2uVFttvXAcDMWeJkFTFTRLa1PShjJKew1WGrfvcgb47pKcQsoZlpy1iIY0E9Q9+7lvCl/U/bBVZk00uuNU1lekJIkso23SBSSdXYyE7C/GOxk5iLRmRSGXVKiMCE8jPNYHc38Ki+1sFX6ZbLKljRZhKnbXWqAfcBrefnhJsmaJl6WXUKMD9U1Srkn9f+mE8mqi0JihWoqyl9kYwQR3O/FmPfk/pj2onCIZHiyunGwCFeq9/sTz64ImOWTBneWlyp2O5XqhTp9ADa3re+GMvyyCNFkqaan0NqIWCOSTTbYAujEDex4O2F5M9pHpul/ZesCSXNG+gr2Cgb39SMVVCihVcJSSK3aGpkgbf/CGsD9sFXNG0XVWWRZ0YDT81TTPKkY38LBl1bcEFTbyxZTxfNK3jps1jUagCenOvGw0ix+9sZ9qSmp7kvW0M7G4diJYj7soGoG/J33xbVpjEcU8itFLYf2+js0Z7fiLsRsASLffBE1OI2pzDSgT6DeShqv5iDuI2O4Pl54QyV42WVKMNJEF1S5fUatYseYW3F79h6Ycz2YPTrPVRrNH4dNfSELIlzyyEc9jv9sI10QJSaSoMiE6aeuj2aN+QlQo2I7X/AO+Asp6yN0WZupPRbakN2no5fO/1aRv5/cWxzVhuvT3YGoe5pq5LaJ9riOYAbah4SfbCcK1fUaVY+nmEY1SLfwVkXFwvBaw4HN8LUhQxNMqMKKRl6yK51Uc4b+Yl/wAuo3t5YC5pJY1DzinZV6v9rpju0EnaeLyXztyLeWL1o5TEDBVQSVbgmGYoLTRCx0N2LXPP3tyMZ+dqr5iJgUeqjQskgNkrYBa6m3Drfj1xBWU0MnRj6hipql3eFwNLUlR+ZL7CxYnwm2A01BlMgUEU6mOoA+ZomKjpsdi6XNhvuV78jfFrkVE1ErxyVGuFpAtPquSgI+hjbi/BJ9MZ7Nc8dJJqiCJvmaYiOohP97ACbSLb9QRxcg3xX0daIj1k1y5ZW/zNTFmgkPJvyovb2sMB9TQbb4MI5cSgWAmRykanqt+fkbn+ra59xj3AMT+W4HdgRtbfFIueQznovskx0w77zKB4mAG4j7auD+l7Wvm0DUTZUBZ/De66W2Hrex+3rjLRTs1ZE7COJo1CSmxb6zeOGM9mt4nt5jAS/ElMXX5eIwxz6WXXbeGmLWLDspKgDtffyx84+IVPTRo4THRK4WmhI3nk/rbuQdzc83sOcfQKwR1Mc8TSBYAx+anJA6jJYsiDsq7KT24HnjPZpN1CrBhFUTJdCfppKRdyw/pZlA9d9sBSQvK00gnKfMKnJsIKNPOw2DgcAcH1wZjlyFbAOkLGwbQWqK1hvqW4voJ3Ha2ISYWW69U0kbAQxlTqq5zc6m8xfm/AsO+LulepRhZg9e4/FZrdOjgP5edKnT2G+AqJIIYkRZl6tSLCGihLARg7nqEblj+be+IKfLpJ5emsaVMgG8MPggi/zlbA+17nfc4fjWBkaOJ2p8vS7SVDbS1DjlUJ3N/LDNC0S0zShWpaDX4VViJ6trWAvfjft/3wCYrVkDUrPNVvcKtPSDpwrbkXA3A8ziNqCWnQaqihohvslpJbjkE2Zr/cDE+Y1kxYR1BbL6cqOlTQreSW3Yad7nzawxLBRsoHSpIKGK/+0VhDOSfIMdzz2tgK7NM0kjKlMxq5WKjxrGQnnsDyB7Y9kzCOYMah6GfSo8To0MnqBoH1eu+LCTP9GlGz2VjxqiproAPtcn2viCKp60irDm0cszMAFqKXQGJ7Fih3P74BughPTRaDNIpF070tQQVJO5UBgNvW2I6WVFfpsP4bWgiyC/y817AakuVsb9tsJfEKhZelmdGsa3AWopk0j34swtfa18WlTMscCwVj/NZc4AhqkA1RNvs297j/AE/TAcR02mbwD5GtU7Q7inqT2C76bONrevnivrpvE5pIyjNcVmXsu231Mqnkeq7i4I2vh+oi0xJl9RODTv46CsB2Uj6Few28vT/TlnlqZbbRZtSW0kNtUIoNwL2BNt/UfsCsFfrjiSWYwwjU1DVXOqNgSOnIw324t5WO+JKmRad3mtaoS611KR4J0bZpQONJuH27m+2+PPnoWWWQU5ETEfO0rbGNuBNF389vbtviWJmXpKCDPCmuilKm1VAVJMRuN30Eix73wHlHUAKkELmQBzUZe451L9UDeXJGH3qopkMkvhoqxys6Eb01UB9VyLKDYb+oxQxPGxKIelFLJ1IG/NT1Nv5bW+lWtbftvi0DGUPOImEcjCDMKe30ycdZP1B2HbAWEFVOOoPD8/Q7Ib/7TS97j811F9u4GLBqVEBraIh6aeIyS0fZ1/M6L2YbducVtTlM8KJpj1VWXkEP/vqU6gQLdwLgqeN/S8NPSKjKIm0q/wCNlr3st2AMlOb8BrDY/bAfQfhA/gDTL1YSbwPe56Z4VvMobrfyA73x5io+F6yRZQIomakn1OOB8tIL642A7FgSB5k4MBsJY723Oxvt39D6Y+f/ABDlEkEiNG41s7CFTveaS5klb0jjFl/9Y2mZ1LqshjV3ZEJCAfUx+mxPlb98YCspSFWJpvxSflUc8h2AlqZfTbw3HFh54CGKGnYBQerTgMWsunTBHfqMxvdnlmQ79xhI5c08ixS+B6m9RVkmxjplN44r/l2sLW3wzkGcmqdaaMhIGnJWwVdNNBpJXYb6mIvfzOF8wDVKJoLCXM5dRYn6KVLkJfsLC5HHOAYNcDatVVIXVBl1OBe7A2Mn359sVsSKgko5Lqigy5lMDuz/AFCNDxyQvrviemzWFZpalDeGjRaakVu8rX8QFrE7Ek+2Oq6ihSVaR5XaGmRqqt/4sh0sFvzve1vXz3wHNRA06xx6DHUSfyo7AilpRy7b21sLXJ33xNDUgI9b0jJFTaaahiIuHfYGS1t7kD9/LCmXUs8y6b/2jM2LM1/5VMhva3a/0jGiy+uh1zFQBS5YmmMHhpSD4j6i1h/mPngK6NnpZQoUVebVA1EN9MA3Nh7D2wvFl69eRJI/4jmO7spYiGIeRv3F7W9e2JUR4IYdCs2Z5lctIfqjRtyV/p0qR+npjqsUfMLleVlo3veqn5ZgBvqY79+dtyBgJKaozSS6xJl94/qiXSSN+/NvLFfnGaMwP8TykKpuBNALMnqDuNuecIV+QQUU8sZqJZC9lWmgY9SQX21sBYcXth+iqekxWgklil26lBVi+vzCFuWtxY7+uA8pp5aaLqxMcwy1t3jksXTtuDcgj9PbnHVRSGmpvm6E9agnsZ6eSxK77gW4sCRfkW74hM/ytSkuVRStDIjPPCwup0NZlCncFL8cj2w1QyilH8ShkD0U8jLUQKlhGpJt4b7EbX9/XAIZfRKsSQySF8vqwei7qbwTXsoJBstibG2xx3VU9SlzIAKzL9MhlTczU525P5hv24xePFSwM2XSkmirbPTvq2jY76dXbxWI97HFc2crHHDJLbr0svylSAT46dgwuR3BYAg9jgKmYy9Z5NXVYR/MwMwB6sJPjjfzAF9uxBxamIDwwMRHMBPlrk/y5uWh8hfddJ/847p4BTJMgGqfLnaWAM20lPLYlbeVr+xOBcqhdDFG1o6pfmKHxfy51F2j/Xb7HywCD9OZjK8Yj+YUw1ANwIatd43P9Ore1vM+eLLJc0dI46x2ZZIZRTVqKPCyfSsj97hbeL0OKtqg1QQvYfOo0MqgaVWqitoY24Y7G58zhuozNY6dKsxBopU+Vr41JB6i7dS/9Vu55uMBdtmE1PTzwxSmSSkYTJqOvrUrHgnyALDbuvrisOXIXFMjjRP/AGugk3IikH1R88G3A88cZeZYadVMTiqy6QawOZKVmN+B40tv5bXxZLl0LBaAuejOWnoJ1O6N9TLcb+EsLejW7YBz4Wr2araSniYJK1qyFtmhntcSC/KuObeh74MNfBdX15GaQFK6BRDUA7B1vs9hybbg+uDAN5rmTrUJEJLpAr1NQ21wtn6aWG/O/G4XGXrZdEaG9546GSRVI5kqXUG++xva3vjY5jVxQzTDQGLopkv31OsaLfy3O3vjGZ3KXnYqgHzFaihv+FSAM32LXFvQ4BaWg6JqDEdLxxQ0EGxN5HCl+3N2Piw5mFYIvmZIlBNPHHQ03/7W2e3sLH10nGezLNpY0oHXxSTVUlUL97yaFFu+w/cWxe1qazTXXT181dgRtdUYqD9xbAMHJkSpo6ZiBHQ03zEq22aS9gT5m4vfGfkQyU8MJN58zqOq5UbiIttf05a3p6YuPiGbQmcVRY6i60qE+VlJA/8At+2GcnCfxNdCgxU2XoYR5Are9vM3wEVE5FVmNav8qmpzTR6jbxKF29vDb74pqrJW+RyyjFlkqpTJJbYkcgt/lUj7jEkzvJkMrb65HFRK1rKQZdwD5g228saKdw+ZZaA5BNG1jYeEkCxF9idjt6YDvN3K1ddWEXaip1SHfh2S97epYDGa+BczrI3qk6cauoead3srksp0rvYfWQ3IA3xbQVOo51rJIWRjJb6jEqMAE7AgqNzttjmWNGra6RlOibLtYF9yDpv99hgMtlPxElFSzKgAr3cMZfBItidwrKSAbbn1OLH4f+MBWvHSZjGJA7ARzKNLo5+nccb7XGKzJcihky+GQkhpasQytf6U7W7Dexv3vbHUOQvBSZkjEGSF4wVA8QCtfWD2Ug3vgLKgqnyytkSZZGgbW0BqNgZRbx33tfxKSOQwx1ktWnzFRRSRMkeYhXSwt0yykjY+R2uPLCeSVbZhQSUbqZ6mM6qcFrFVtYtqP1WP5edxh3OVmqaOnzBJAJ6IFJUtYgow3v7W29cB5lNBKaSsyqZCs8IM8F7b2N/Dbm9uf8WJY4I6ujq63q3b5PpSxkeISoVIc+YNsSfFudv1IMxiXSVdQJAPqhkUEAg7c6x7jDuYZbHFm0TqpWmrUaNwAQCxU9uDfZh98BDSV0fzOV15NknhNPKT9OtfDY+5uPtipemmjpauMC0mX1IliN7FYybm3oRY4ghyVjR5jR62LUsokQdrC4a3kSN8aWKneeso5ih0V1EUmsDpLdMne2w34vgFc7pNSuIlIM6CvgZPyzxoA4ItwQce5dCs0vTkfXBm0Jfw7aJkC69uL3w9kM2iPLRIQXinlo39irDfyvoUj374qsvp701OB4ejmuiNu+lpAWA/Uj9MA9TZyF0ZjIjHpBqOsFtwtwFk09rtYkeuHvh/4fYRVVENQRSJqKex8IcG2k22KsNx/i7746+Xietk0hnpcwEkM2x8E0dx9gQpFz3tjv4MhmNPPQiR45qOeySHcMpJZAR/SV8JHlxgI6Cp1dDM7ASITBXBb9rLqI5OkhSLD6Tgxq6XK3jqWkRlEMq3lj/4osNa/wCYbEf4R5nBgFs2gvPGGUDqzx+IclYUeYavIB0A++M3moCvT6TZBTVkzauQW0gEfdjxjRyIyzBnSxCVEgu197oqn7p27XxSZtTdaOWRnVJHoIogBtpMrt/qQBb0wFFk9EvzVLFUP02o6aJFGkHVM6lyp1AjYaSPUY6qI3gfL42bqRxVUZjbYkq6Am5HJ1Fj7Ww1XwrJVl76V/iMMTG/PTjUAD3cW++I6GdHTL7byRVqwSEcXiRkBA9VAOAg+IIUalrlHiVszQMSeAREDY9tyRf3w8g6eZZm0f8Ad0a6R2FoxYe22KnN6C1JmumWOTTVJMAjaiAP6rcXsR/ynFvUU3VzRlB/CrMv3I77abj2BGAUgmZqJMs0Aq2XGYG9m1gggX4tiGtX5lMonhsNatTs1rlSVHkRuLNbfYn1x7TyrbKK1G0jalmB7XBBB/Qj9MJ0MbQw11NoIlpKhaqJd/pBF7emm3vqwDmWov8AEq+gWURrMyKdXLoEAdRf8xUk3898GWVLJWZazoQnTko3vuCysVsfuAcVvxUy0+bU2YDenmMcocb7WCt+29sO/E9ITJVwRuRIGWupTfdjYiQKb3Ph3A98BY5bl4kFdlfTSI9ZpAASCUZboyezBfscK0eZmOSlrpFXRVJ8pVW4DqdIc/Yd8L5xUhUp87p5Xdy6rMpOwAUKU/UH9ceT0MLPVUyuUirVSooz2aS2rSDwCWOmx8xgMnRF8szJdV/wpLHtqQ7X9ipx9BgWP+JVlFKw6FdGsqW2uSPykd9j72xjvjvVPT0lYy2YqYJr8iVL7Ec8A/phpyJ6HLql7/2eo6ErnsgYML+YsQL+pwFtlmYtU5TJRx06ymAMkgLkEKt2R1sNzqH0j/Q4azXNTLk9HWqo1UsqMwBt9N0Pf1GJXi/h2bwBSkdNVDp6FG3hBVLnzLFd/U++FsuoNCZ1l6oODKl+LMtrelrC3/jAPVWlM7jAQmOupfxFHFyGBJ+yjjucJZXmBpKF2BMnyNY0YAYr4CxXxc6r3vbDGWVXzD5LUsLSt1EYrwVUMN/S4v8Ac4gpqZRWVtKxZqaujkmQgWIZSSbAjkEbewOAlzZEJroWAREeGuQjkgka7/p6fUcT1ORsPngCUiSeKshcg6bbM9iB6EG17WviLJq9Zvlg4JNVQSR+LcExna/ra9/tjr4SzN2XL5TIdBD0cqG5BZblWPa5A/cYC1eBmerpYJen8yoqaaRW21EgvptwNQBPnrOHIneGspjIiiWop2SVgTu8ekqo303Opu1yB6YqqOn0mik3vR1MlKwG3gbVGhPoLocP1MAYyoHMs1LVJUKpvdFf35GkyDbyPlgLnIJ0Khog3Tm1SAWJ0NfxhiSbHWT4eBY48x1lUCwyvHGrdOUmYMN0VjbUoPa58dvU4MBD8WzNHTVElx/K0JYeIM50834uV/TFd8TJD8sUBAaOemRydrsrxsov7EYsPitkaEJs2uohjYC3eVLj3tjOZx0rVzy3IFbT6lHkvSKn9SfsMAjWKVaVtjpzeNjY9iU/74VpqNUmqiSAkWaRON7bvs2/l4x+mLDPIFjjzFmJKx1cNRsO1o2I/QnCecQsrZwuq+gw1aC/9J1E/olsAvTZesUzQWCirjqIZAD/AH0bsyk+pVlt6YdyKuX5fKqgqAYZflZDcAjUDGL+mrS1sU+YJI0tTUxkkI0Fcne4IKtb7X/TD0U9IkmY0srhUnZJYGN9OqRQykW4sxvfyGA6my638UoSpPTPzlNze/O3oGNvucMxVCPW0lWf5VdTGCQ326gB2Prbb7YhzrN5FNNXgETU7/LVinYb2+r0J3B9Rhmqy7eqoY28XhraPjYkkkA/5wdvInAUlCOhBLDIiyfI1BWRTuDBNsx8xbkeuO6mCVFKWEk2XkTJJe/VpX1eC/JIGLKaaGZoa8+COpU0tankxBUE+VmA38rYqhRTw2a2qpy8aJI+etTMTZhbyFxbASZTSxa5KO9qXMY+rTf4HHb0IIsftiDI0aWnaklVvnMvfqwr3ZUYEoPTbb3GO4MsVwlOvgiqj8xQS33jksNUZN7gbAbe+LWoqT+FmunVU0t4ayFfIXVmA3O19W+1va+A6gqoY6xECI9PXXnhZhfpzsuk3vtbcj0vig+G8qmqKWry4SBHimLuhF9S6bWX11qP1xc57kEjxyCmQst1raVz+UkjqR7C29wwXvvhaOtR6yhzBS8STfhVTJdQJlI2fy1Egb8i2AXzOpkmyijrNX4tJMFLcnY2BP6LfzxoarS2Z1Nz4ZstLEjbgrv+mKSqoWhOb0Gq4eI1MdvQ6yLcdrYeyycPV0z7EPljA+tv/WA8+FIdEOTkHVtO17W51G32O2F/hlZXbJ5mOo6qhLk3OnS53+1/2xafB8umPKEZRqaOZgO+g7g+1iP1GFfhZg38L07KJaogHkLokt+lxgIsjqiTlbILaaqogPqhP/UDD/yax0bRjj+KBbjlfxlG3kbWwplMKA5RGuwM88vva9jv56sW2XwRS0jU9RKQ9TLPMrAfTok8/MWB/XAM1MQlnzKkAszRxzKL8tY2YA99SL+2O6jNwvy1S9M6ipXoTu3haMfl1KLixbUL9r+uG87yqRp6atgIZ438VttcDixXmzab6hjmPLnSWemlqA0NSC0CsfGrcuo81F1I8t8BY/DtI8MXQci0TFYrHdoQBov7A2Ptgw3l8oe9wOpGdDGx2NgTYkXIIIODAYn4tFPHS1TRM11qYJ3241PHuPMabn3wxWhZHzSJSTrgimUHjdGBPv4V/bD3xFlqNJUxJYTVdIUQHgmPVbb/AJx+npiaipkUwSy7STQ/LPpF7tbULkbC2lx7nAJrmYaSZotMqT0Qlj2DBmTUpFu/Ki3viqhkD1VBWFharganl0C6l9Nwp8tx+oxLTZS9HTQOWCtRzuHOoWNPJISb3/wMGA8xiTM8tSJKmCG5kicV0CexBYL6E6l/5xgKTLVEFLCW+iKWakqbEXEcjnQWXyBIYX4BvhbLsrSeQUE9o5RAac92JiOqKQeamM88dsaf+Hxy1BkaO8GZQKrW/JIoJF+24Ox81OK15ZjTlioaty2YFv6pIhtfjfVGT6bYAyJllVJJyAlXEaOYEHeojuFJ8rqGF/MDCxmdKdJmUiqypxG45MkRFr7dmG49VOGzSrO0sMbhYK+Pr0xI+ioXdgPI/msPI4VOYN+HXMG1RH5XMIh4gVUGz278g39fQ4CKrECSzxI1qKviWRW0kLDMw8BJttqYX9PTHVVWaI6aokBirkc0zuT4GK8LN5o67hvvvixRo4IKuieLrU4HWhA3LwOQX0ebRkkj7Yo6mN40JZFqKdYV6luZICT05FB/vY9w3v8AoEi9DRJE2sQia+4IehlPDKf90SORYD74Yo56hJJDZTXRx6vBuldCBzty/qN74hp1ld1KFWqYoraT9NZSnjfu4FxiOGhiIjjjZxTyuzUc9yrU0wv+E9+FuLc74BM1SdMRQzOkLuZKaTUVNPPY3hc/0k+fp2wlDnCGSWlnBVKradCLCGfYB1tsQTYn/wAYeqlZzIs1MEqCAKqnUAdWMXInivtrU+R7k4lqIolWEyv4ZB/ZMwCgabfknU7Egi18AxG9QXWoZNMtJTSRVisbM8YU6GA/MGte/FxiCgjeAGJrCRcuWKMki3VmYFVB8yL8dgfLFlnMbVCQFswpUdozFVssg/Ej1WuBbc2B8rE4YjzGGpKVbBkpKUhadCBqnlHgDWBJIGwA9Te2AkcvSiSaykUFItOhHeZlVmt7ArjlaP5SnDaozJSUThge001mAvxc7i177jzxBLTqV6U7XihdqquIH1SsbpCDwSBYH2XzwyJTNLDFItuoxrqgHmONP5Ubeuyg+x8sAZVCRV0cJsPkaIyN7uALennf0OJvh6mjU5Yl764p5tz/ALyzED7vitShqZKOuqAX+YrHRUVd2SEt4b/0gqST6WxeUdPHFWq73WKlhjpYrqTeV+dJ77aBftvgOvhQ9emow4LxvDJC47HTa19/6VIB5xpK/JI5FUG+qNg8bXuyEW+kngECxHcXxXrlElLBpp2LaajqKlgLI7+JODsoZiNuwHri6pIGUuWkLhm1KCPpFh4R5i4J++AnicMLj/16Y8x3bHuAoPibLXkkpJor64ahSbEfy2Gl7358OKuto2iiqYY5NUyuauCMMQ2m4NvbWHFvXGvqHAViWCi27E2t63xkVjnj+WqJAzPCzQTMQLvGxAEl7XsGAbaw3PlgOazVMBJINNLW0wWQNzFIR4TftfVp9wvnisyupkEaM9hV5eRFU99dOebE8iwDf8uLCOolhMlGsPW6UuvpsBaSmka/hJ2LRsSLf4R54nzimlDvUGLW8QKsqCwnp25U35dLXt5jyOAqqzURVUUbqsjn5qjZW2IvfSvkQynYdmxDDnDSQR5sItM0BMVUgv40Fg9hf8pOoX32IwvQ0cWmBUfTFqM1BUkfQxuWge/F/tccbjDFLOIZJpZIdMcg011NYkRk7dZQBZo2Xcked/PAdjLI9S0yTlYZyamjmFvw5b3KX4IINwvNrjyxzNIIj82yAsCYMwRGsD2Eunt538j6YibI4o4xAzMtFM5aCU7NTy3Onc7dN+zd7jzw1NTlZnYgPVCILUQkeCqhBt1F7Fgvbz2wFVQUs0RNKkg6kYMtBPcESRncw+otyt/9MKCrdtLIRCpa+kr/ALPUMCDG6tv0Zb8Ha5G+LCqo4YFSlqtXyUh6lJVAWMDMbhSfy+dztvj2oVzI7NEklbEoEqEApWU17hlHBcAA7cH3wFSlLMsyLTh9ccl1QrdqWQ2urAHeB77EGw9MMJeSGomjh1IW011Gb3WQHxywm+xBuQPTFtmFUJCtUZlhRwRBVxDT0zt+FUA3BAI5Ow3G3dGHwVAmnIoq07iRd6epJG2o/SNXcXvvfnAVVbChjR5ZZKyjtZZ4x+PTm17P/hte4IPpbFlkEtUsYiyyrpquJbkQzpZ1B34uD387b4gelMVTdActqWHiDb0sxPYH6bG3BwzVZcoctX5U6tyKihBIb3CG4v5kDADQVZDl/h+mZmBGoaQL+oub/YjEuX0VOF6ktdBFWhfCFIMdOtwCES9tVri5JNycVyU2WyJcRZr4TuAsjDnceWLSiy6EEmhyYlwR46vwKARsV1k3Pew+/bAX+S0yIsfSqlkpwG0I1tVRKfEWZmAudXGkWw3PnyRyQRvTg1VSQskcZDFFA3Lna6gYoMu+HqpmnqpivzgYojMG6cKW5iFvEQCdxte/e+K+iyVEgLxFqZCCtTW1KlZXU/UIg24DefPHOA3f8WpmTrxjqfi9NTGN2cXWwtyBv6AA+WI6jPIi0jNGDDTkXlJv+N/Sgt4iLgXB5NsVeXZWZoIoaV3p6FRcOpKyS/sCqm5JPJ2xZVWURWpkeCRlhe0aIdSgj6Xcm3AF9+57m2AfNVOwpysQTXZpg5voXTuot+fUQL8bHDlBGgQdMgobkEG43N9j5XwvKo1Ayk+IlFVSxUg/1KNr7Hc7DDdNAqKERQqqLKqgAAeQA2GAlwYMGA5kjDAhgCDsQRcHC1ZC50mMqNwGDcFL7jjm3GG8GAzOVTzrK1LI15IzrSUrtJCT9JI4Zdx9geDjqioZA0sSSl4ixZJNQZoW2vGQ25B7eQuNtsXOY5jFAoaaQIpOkFuLn17YrczzKlph122WY6WlQEjYE6mK7AWv4jgCTKkbXD0kEDFuqjKfGSF8Sb2AvztzirgiME1PHOkkyXKw1I7B/wC7lA5HYMbg7Xta+J6XOlp40Z3M9Pbw1S+LTv8ATJbfy8Q223tbewiqS7pNARNBKbOQ99FhYMva19mHPfAV1TIhnEaxdKdFKoji6TRXuQtjpJ2uAd132tjlMyibpy9B9CMV1MpMlPKedYO+ggjcXAt5bgymimgkEUoFRSgl4Z2ILxHewYd+SA43wxK1bvGSiuh1ia34Ui3tocfUrWtuNtgfTAOZhEZYejNCJgxCS28IsRu635AJ7G438sZaHJmkMUR1SxRkGlrUZS8ZAPhk48I449/PGvpK92RyYXWRBYx7eIgfkbgg9jt62xmaOpDuZKN0ikLsJ6KUqNbdztcq523FwcAxF8PNckGJZJTeaKxaGZb21WIGlyPLjvfbHuW0lOIHpxCyK0ulYKrjzIj3bw2uRa9vTHGWyU8CvHJLNTCT+6kawjbk9KS3Hpcj2xd0bsya0mjqQXGgnSNK7BvEoILWueBfj1wGWzBnI0Bj42t8pXIFQjgLFIoIuBuN2v6YlyTLpKVy6ULL+V2+d1RqvJIVv6R2tfGnzCjjWM60aSMD+Vp13JYG4v4rjfg8HGaiSliLmGSpo2fcmRXMdx5iS68eRGAqK50Mpb/5EEhvqVdSagx7X4IHkRi6psqSCATpOahyylZqhndbk/UqKDYgbCwHvjpDC8XW+cpTEHNnFOgAbyF2O+H0+IqdCifNtM4/JEgOq+4uqqSLDyI9cBTU9dULO/SjmqJWNjPNeKnjAAFkW9yO97b+eJa2vppESOpBrqmG5CxRPoMnkD9AI43O2+NFndZS9NBVsipIRpWXa7c2I8/TDkM0SR6k0iOxI0jaw5IAGAzlLI0fSlrGdakqwEcIkdFVidIKKNJKg2ufLF5WUsrxqsU3TB3Z9N3tzsDsCfUbeWGaidgqlIy9yNrgWBHJv/8A2+IWeoLMtokUr4GJLHVYXunhBF78N2wDFHSLGoRBYepJJPmSdzhjC1HWJJqCMGKNoe3ZhyMM4AwYMGAMGDBgI5+Pp1bjbbz538ucZ7MqaKBbLLFTo7MOnNpMUhO52JBBNzsDb0xpcR1ECOpV1VlPIYAj9DgMmfhYRdV6M/LO1iVuGhk24KdhckXFjixyaMxoFaJKa0g1hNPTlZha6dxdrdgb+d747qPhGie+qnU3FuT+29hiakyXpMCk8ugWtGSGWwFrbjVb74BbLKNxLI/TeG11CB1MTi5IYKN1bffjnviSGczl4aindNS2KtZo2Hchl4O/BsduMO1mWJIdRLq3mjsv7A2/bHsVEyuG60hW1tBsR73te/3wFMMtjKPSJVP1IgGiJ+uHaykGw1r23vcXBvhPNvh8VHT+apBI4NvmIGCMP8R3Btftc2xpsyp9aaTGki/mVu48h2ve3OKhPhSIWaF5aYk6isb7X9VN1OAlqSwiaCnYGSFRf5hXZWW3dzzfzufUYr6SKWUxM9PQtGpBLI99H+JTptx7e+NDQUbopWSYzA93VQfbYAEfbEkNBEqlFiRVb6lCgA+4AscAtTSwRKVjZbXvpV9RuT2BPvsMU2dVIV9P8WWBlsXSQQn6jcXuAR/2ti8GXQhhanj89QRNiLW9b7nceRxJKCyHVECdzpJU3I4523wCOW53SvHcVMUgQhXcFQNRNh6C5NhhysuUPSkjRgd2YagPMHcf64kWEaCOktuQu1jbcdrA3x5LCAjWiUltymwBJ89rffAeNHfdyh40Ejg253Pc+WFZ6uPpmOSqjR7WLqyqQfQMTb749q6FXUSNTRvKFACsRtvewaxtY9wMV9LkbJcpT0cTMeQhYjuL7DUR57b4Cy68touh05YiBqkaTe3mLAhv2wCSQks1MNatpUhlPhPcE7j1HpjusoGZVWOVobG56arv+oNvthpjpuzNsBvfgW5OA9jvc3A52t5evrfEmI4JlcalIIPBGJMAYMGDAGDBgwBgwYMAYMGDAGDBhGrlnWJmRFklB2XVpBF/M3sbfvgHsGMrP8TWHTrKKdNdwAqdVX8x4L9uxscWLfEdKlOs7SaIbcsrC1iBYi2xuQLHAM1uc08Q1STIo1abkj6vL39MdUubwSadEqNqNhY3uQL/AOmM1/8AOYFBLUlSqsNaN0CRJfg3AO5t33xDR/HLTNGlNQTMwYdYMujpg2FwTsxG+2A2okFyO45++IKbMIpFZkfUFNmsDsRheSSqFQAEiNNbdtRDg+1rEXxJUtUaT01i1adtRNtV/QcW74CSrzBI1Vm1WY2GlWY+fAF8ewVeuxCPYm12FtrXvY72vtirlXMd9LUvaxKvt57X33wrJl1crO8mYHpBSQsUCaxwdrq1+44OAt4K6VpNPyzKg5dmX9gCSf2wvmcVVrLR1UMUQtcPFqI8/F1AN/bFbR5nSyzilE9RJKqHUrFluGF7vYKODtxbFJWV2RRNJA4F1/CdT1T9J/NvudQ+rk+dsBsulGR03qCzspU+MKTfe4C2sfIjtiem0okaRqzpawbVqsPMljc/ucL5dQ0r6KmOCPUVGmQxgPptYbkahti0AwAMe4MGAMGDBgDBgwYAwYMGAMRmLxBrnYW52/TEmDAVzZSC8z9SQdZAhAcgLa+6f0sb8jyHliunkp1j6grtAhAjaRpgVFifr1HTqPBJ3xosQSUUbAho0Or6rqN7efngIkzKExiUTRmM8OGGk38je2O66aJUJmZAncuRb99sH8Pi06Okmi99OkWv52tbEGdUyPH4qcT6SGVDp54uNW1wCcBJSVsbjUksbRkDSVYH33BtbFXWSZgsK9IU8k4Y61LMqle1ja4NrYr5aKnWrSD+FjQ4uZ9K6Q1r6bC59ybD3w7mdIKVnqaajaeeWwYK+nYeZa4A9hgGsnzOdkZqun+WK7XMiMreoIOw98Usue0bz9b+LqiRmxi1xqhPqWF2vbscRfEGZlaeNJsqeaOQAtGGD6XBvYgjcC19XnbbFbQ5pFoOjIZQ1wAvTQAgjm5G2A0vxBVU9TRmRMwEMWreeKRbbfl1cfpvjLfDyt4vks9WfQCzJUePbi5JIcD14xtckTXTBZKRIOfwCVYbeoAH7YQjgmKSq2WU4JsoAmGl1P1aj0gQBYbaTf8AfAI09TnHKGgnFrkh3Xfy2U/ri5yuSuZJDNDTJJYdPQ7Mrf5jpBH2GJMjy4okivTwwBjYLCSfDa3iOlfFe/HnhGj+B6eMi0lSwBuFaoksLdtm3Hob4B3IjXFnarECqfoSIsSN+7EAG49MXWPLY9wBgwYMAYMGDAGDBgwBgwYMAYMGDAGDBgwBjzBgwAMGDBgPccnBgwGQzuJRmdIwUAlmuQNzaM8nvjXjBgwHuPRgwYAwYMGAMGDBgDBgwYD/2Q==");
                // return dummyFp;
                deviceInfo = new SGDeviceInfoParam();
                deviceInfo.imageWidth = 300;
                deviceInfo.imageHeight = 400;
                // throw new DeviceNotFoundException();
            }
        }

        log.debug("Scanning fingerprint from device");

        try {
            byte[] buffer = new byte[deviceInfo.imageWidth * deviceInfo.imageHeight];
            long targetQuality = config.getMatchingThreshold();
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
        long error = -1;

        if (client != null) {
            client.OpenDevice(config.getComPort()); // two usb devices connected => 1, one usb device =>
                                                    // 0
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