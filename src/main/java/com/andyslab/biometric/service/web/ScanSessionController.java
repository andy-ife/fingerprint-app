package com.andyslab.biometric.service.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@RequestMapping(value = "/session")
public class ScanSessionController {
    
    @ResponseBody
    @PostMapping(value = "/create")
    public String createSessionId() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }
}
