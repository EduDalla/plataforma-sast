package com.fiap.sast.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {
    private final LocaleConfig locale;

    public ConfigController(LocaleConfig locale) {
        this.locale = locale;
    }

    @GetMapping("/api/config")
    public Map<String, String> config() {
        return Map.of("locale", locale.locale());
    }
}
