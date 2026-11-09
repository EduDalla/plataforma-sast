package com.fiap.sast.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryIdentityTest {
    @Test
    void normalizaProjetoSemMudarOComponenteEstrutural() {
        assertEquals("acme", RepositoryIdentity.normalize(" AcMe "));
        assertEquals("orders-api", RepositoryIdentity.normalize("orders-api"));
    }
}
