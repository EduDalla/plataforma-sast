package com.fiap.sast.web;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.parsing.InvalidJavaSourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail == null || detail.isBlank() ? status.getReasonPhrase() : detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail bad(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing(Exception exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado");
    }

    @ExceptionHandler(GitHubClient.RateLimitException.class)
    ProblemDetail rate(Exception exception) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Rate limit do GitHub");
    }

    @ExceptionHandler(GitHubClient.LimitException.class)
    ProblemDetail limit(Exception exception) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Limite de análise excedido");
    }

    @ExceptionHandler(InvalidJavaSourceException.class)
    ProblemDetail invalidJava(InvalidJavaSourceException exception) {
        var detail = problem(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        if (exception.fileName != null) {
            detail.setProperty("fileName", exception.fileName);
        }
        detail.setProperty("line", exception.line);
        detail.setProperty("column", exception.column);
        return detail;
    }

    @ExceptionHandler(AnalysisController.Unprocessable.class)
    ProblemDetail invalidAnalysis(Exception exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    ProblemDetail security(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Entrada inválida");
    }

    @ExceptionHandler(GitHubClient.UnavailableException.class)
    ProblemDetail unavailable(Exception exception) {
        return problem(HttpStatus.BAD_GATEWAY, "GitHub indisponível; tente novamente mais tarde");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a solicitação");
    }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ProblemDetail validation(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Verifique os campos informados");
    }
}
