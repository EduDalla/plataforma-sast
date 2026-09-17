package com.fiap.sast.taint;

import com.github.javaparser.ast.expr.MethodCallExpr;

final class TaintSanitizerCatalog {
    private static final String REPLACE_ALL = "replaceAll";

    private TaintSanitizerCatalog() {
    }

    static boolean isSanitizerCall(MethodCallExpr call) {
        return call.getNameAsString().equals(REPLACE_ALL) && call.getArguments().size() == 2;
    }
}
