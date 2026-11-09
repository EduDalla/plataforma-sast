package com.fiap.sast.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Configuração de idioma global da instalação. */
@Configuration
public class LocaleConfig {
    private final boolean english;

    public LocaleConfig(@Value("${sast.is-english:false}") boolean english) {
        this.english = english;
    }

    public String locale() {
        return english ? "en-US" : "pt-BR";
    }

    public boolean isEnglish() {
        return english;
    }

    public String text(String portuguese, String englishText) {
        return english ? englishText : portuguese;
    }
}
