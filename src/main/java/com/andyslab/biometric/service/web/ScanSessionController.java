package com.andyslab.biometric.service.web;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.andyslab.biometric.service.api.ScanSessionManager;
import com.andyslab.biometric.service.model.BiometricScanSession;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@CrossOrigin
@RequestMapping("/fingerprint/session")
public class ScanSessionController {

    final ScanSessionManager sessionManager;

    ScanSessionController(ScanSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @GetMapping
    @ResponseBody
    public BiometricScanSession getSession(@RequestParam(required = false) String uuid) {
        return sessionManager.getSession(uuid);
    }

    @PostMapping
    @ResponseBody
    public BiometricScanSession updateSession(@RequestParam(required = true) BiometricScanSession session) {
        return sessionManager.updateSession(session);
    }

    @DeleteMapping
    public void destroySession(@RequestParam(required = true) String uuid) {
        sessionManager.destroySession(uuid);
    }
}
