package com.fiap.sast.semantic;

import java.time.Duration;

public interface OllamaGateway {
    /**
     * Solicita uma resposta estruturada ao modelo local.
     *
     * @param prompt contexto mínimo e não confiável do finding
     * @param timeout tempo máximo disponível para a chamada
     * @return conteúdo textual retornado pelo modelo
     * @throws OllamaFailure quando a chamada não pode ser concluída
     */
    String generate(String prompt, Duration timeout) throws OllamaFailure;

    class OllamaFailure extends RuntimeException {
        private final boolean retryable;

        /**
         * Cria uma falha classificada conforme a possibilidade de nova tentativa.
         *
         * @param retryable indica se uma nova tentativa pode resolver a falha
         */
        public OllamaFailure(boolean retryable) {
            this.retryable = retryable;
        }

        /**
         * Informa se a falha permite uma nova tentativa dentro do orçamento da análise.
         *
         * @return {@code true} para falhas transitórias
         */
        public boolean retryable() {
            return retryable;
        }
    }
}
