package com.flagship.admin.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/api")
public class AdminController {

    @Value("${flagship.server.url:http://localhost:8080}")
    private String serverUrl;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "application", "FlagShip Admin Web",
                "version", "1.0.0",
                "serverUrl", serverUrl,
                "status", "UP"
        ));
    }
}
