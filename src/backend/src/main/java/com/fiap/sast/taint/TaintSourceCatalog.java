package com.fiap.sast.taint;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.Set;

final class TaintSourceCatalog {
    private static final Set<String> HTTP_PARAMETER_ANNOTATIONS =
            Set.of("RequestParam", "PathVariable", "RequestBody", "ModelAttribute");
    private static final String HTTP_PARAMETER_CALL = "getParameter";

    private TaintSourceCatalog() {
    }

    static boolean isHttpParameter(Parameter parameter) {
        return parameter.getAnnotations().stream()
                .map(annotation -> simpleName(annotation.getNameAsString()))
                .anyMatch(HTTP_PARAMETER_ANNOTATIONS::contains);
    }

    static boolean isHttpParameterCall(MethodCallExpr call) {
        return call.getNameAsString().equals(HTTP_PARAMETER_CALL);
    }

    private static String simpleName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }
}
