package com.fiap.sast.analysis;

import java.util.Locale;

/** Normaliza a identidade lógica de um repositório público do GitHub. */
public final class RepositoryIdentity {
    private RepositoryIdentity() {
    }

    /**
     * Normaliza um componente de identidade sem alterar a URL original recebida.
     *
     * @param value proprietário ou nome do repositório
     * @return componente em minúsculas e sem espaços externos
     */
    public static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
