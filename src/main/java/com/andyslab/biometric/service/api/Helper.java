package com.andyslab.biometric.service.api;

import java.util.Base64;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.andyslab.biometric.service.model.BiometricTemplateFormat;
import com.andyslab.biometric.service.model.Fingerprint;
import com.secugen.secusearch.api.SSIdTemplatePair;

import SecuGen.FDxSDKPro.jni.SGFDxDeviceName;
import SecuGen.FDxSDKPro.jni.SGFDxTemplateFormat;
import SecuGen.FDxSDKPro.jni.SGFingerPosition;
import SecuGen.FDxSDKPro.jni.SGImpressionType;

public class Helper {
    /**
     * Maps the integer type to one of the SGFingerPosition constants.
     */
    public static int getFingerPosition(int type) {
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

    public static String getDeviceDisplayName(long value) {
        String driverWithText;

        if (value == SGFDxDeviceName.SG_DEV_UNKNOWN) {
            driverWithText = "Generic Fp Scanner";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU02) {
            driverWithText = "FDU02 USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU03) {
            driverWithText = "FDU03 / SDU03 USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU04) {
            driverWithText = "FDU04 / SDU04 USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU05) {
            driverWithText = "U20 USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU06) {
            driverWithText = "UPx USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU06AP) {
            driverWithText = "UPx-AP USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU07) {
            driverWithText = "U10 USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU08) {
            driverWithText = "U20-A USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU08A) {
            driverWithText = "U20-AP USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU09A) {
            driverWithText = "U30 USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDU10A) {
            driverWithText = "U-Air USB";
        } else if (value == SGFDxDeviceName.SG_DEV_FDUSDA) {
            driverWithText = "U20-ASF-BT (Bluetooth SPP)";
        } else if (value == SGFDxDeviceName.SG_DEV_FDUSDA_BLE) {
            driverWithText = "U20-ASF-BT (Bluetooth BLE)";
        } else if (value == SGFDxDeviceName.SG_DEV_AUTO) {
            driverWithText = "Auto-detected";
        } else {
            driverWithText = "Unknown Device";
        }

        return driverWithText.trim();
    }

    public static short getSecuGenTemplateFormat(BiometricTemplateFormat format) {
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
     * The type of fingerprint impression:<br>
     * 1. Live-scan plain => Bare finger pressed flat against a digital scanner
     * plate<br>
     * 2. Live-scan rolled => Bare finger rolled nail-to-nail across a digital
     * scanner plate<br>
     * 3. Non-live-scan plain => Inked finger pressed flat onto paper, then later
     * digitized via card scanner<br>
     * 4. Non-live-scan rolled => Inked finger rolled on paper, then later digitized
     * via card scanner<br>
     */

    public static int getImpressionType(int type) {
        int result = SGImpressionType.SG_IMPTYPE_LP;
        switch (type) {
            case 0:
                result = SGImpressionType.SG_IMPTYPE_LP;
                break;
            case 1:
                result = SGImpressionType.SG_IMPTYPE_LR;
                break;
            case 2:
                result = SGImpressionType.SG_IMPTYPE_NP;
                break;
            case 3:
                result = SGImpressionType.SG_IMPTYPE_NR;
                break;
        }
        return result;
    }

    public static SSIdTemplatePair mapToSSIdTemplatePair(Fingerprint fingerprint) {
        final Log log = LogFactory.getLog(BiometricMatchingEngine.class);

        byte[] templateBytes = new byte[400];
        templateBytes = Base64.getDecoder().decode(fingerprint.getTemplate());
        log.debug("========== Byte array size: " + templateBytes.length);
        return new SSIdTemplatePair(fingerprint.getId(), templateBytes);
    }

    public static int[] buildFingerprintIdList(List<Fingerprint> fingerprints) {
        int[] ids = new int[fingerprints.size()];

        for (int i = 0; i < fingerprints.size(); i++) {
            ids[i] = fingerprints.get(i).getId();
        }
        return ids;
    }
}
