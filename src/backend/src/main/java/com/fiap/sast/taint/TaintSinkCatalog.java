package com.fiap.sast.taint;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

final class TaintSinkCatalog {
    private TaintSinkCatalog() {
    }

    static boolean isRuntimeExec(MethodCallExpr call) {
        return call.getNameAsString().equals("exec")
                && call.getScope().map(scope -> scope.toString().equals("Runtime.getRuntime()")).orElse(false);
    }

    static boolean isProcessBuilderCreation(ObjectCreationExpr creation) {
        return creation.getTypeAsString().equals("ProcessBuilder");
    }
}
