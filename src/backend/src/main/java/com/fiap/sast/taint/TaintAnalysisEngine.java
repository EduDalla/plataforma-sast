package com.fiap.sast.taint;

import com.fiap.sast.analysis.SecurityFinding;
import com.fiap.sast.analysis.TaintTrace;
import com.fiap.sast.analysis.TraceStep;
import com.fiap.sast.rules.RuleSupport;
import com.fiap.sast.rules.SecurityRule;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Taint Analysis intraprocedural (ADR-001): rastreia entradas HTTP até Runtime.exec/ProcessBuilder
 * dentro de um único método, sem seguir chamadas para outros métodos ou classes.
 */
@Component
public class TaintAnalysisEngine implements SecurityRule {
    private static final String ENGINE_VERSION = "1.0.0";

    @Override
    public String ruleId() {
        return "TAINT-CMDI-001";
    }

    @Override
    public List<SecurityFinding> analyze(CompilationUnit ast, String source, String file) {
        var out = new ArrayList<SecurityFinding>();
        ast.findAll(MethodDeclaration.class).forEach(method -> {
            if (method.getBody().isPresent()) analyzeMethod(method, source, file, out);
        });
        return out;
    }

    private void analyzeMethod(MethodDeclaration method, String source, String file, List<SecurityFinding> out) {
        Map<String, List<TraceStep>> tainted = new HashMap<>();
        for (Parameter parameter : method.getParameters()) {
            if (TaintSourceCatalog.isHttpParameter(parameter)) {
                tainted.put(parameter.getNameAsString(), List.of(step("http_param", parameter)));
            }
        }
        for (Statement statement : orderedStatements(method)) {
            var expression = expressionOf(statement);
            if (expression == null) continue;
            checkSinks(expression, tainted, source, file, out);
            updateTaint(expression, tainted);
        }
    }

    private List<Statement> orderedStatements(MethodDeclaration method) {
        var statements = new ArrayList<Statement>();
        statements.addAll(method.findAll(ExpressionStmt.class));
        statements.addAll(method.findAll(ReturnStmt.class));
        statements.sort(Comparator
                .comparingInt((Statement s) -> s.getRange().map(r -> r.begin.line).orElse(0))
                .thenComparingInt(s -> s.getRange().map(r -> r.begin.column).orElse(0)));
        return statements;
    }

    private Expression expressionOf(Statement statement) {
        if (statement instanceof ExpressionStmt expressionStmt) return expressionStmt.getExpression();
        if (statement instanceof ReturnStmt returnStmt) return returnStmt.getExpression().orElse(null);
        return null;
    }

    private void checkSinks(Expression expression, Map<String, List<TraceStep>> tainted,
            String source, String file, List<SecurityFinding> out) {
        expression.findAll(MethodCallExpr.class).stream().filter(TaintSinkCatalog::isRuntimeExec)
                .forEach(call -> call.getArguments()
                        .forEach(argument -> report(argument, call, tainted, source, file, out)));
        expression.findAll(ObjectCreationExpr.class).stream().filter(TaintSinkCatalog::isProcessBuilderCreation)
                .forEach(creation -> creation.getArguments()
                        .forEach(argument -> report(argument, creation, tainted, source, file, out)));
    }

    private void report(Expression argument, Node sinkNode, Map<String, List<TraceStep>> tainted,
            String source, String file, List<SecurityFinding> out) {
        var chain = evaluateTaint(argument, tainted);
        if (chain.isEmpty()) return;
        var trace = new TaintTrace(ENGINE_VERSION, chain.get(0),
                chain.size() > 1 ? chain.subList(1, chain.size()) : List.of(), step("sink", sinkNode));
        out.add(RuleSupport.finding(ruleId(), "Command injection confirmado por Taint Analysis", "Critical", "CWE-78",
                "Entrada HTTP não sanitizada alcança execução de comando.", file, source, sinkNode, trace));
    }

    private void updateTaint(Expression expression, Map<String, List<TraceStep>> tainted) {
        if (expression instanceof VariableDeclarationExpr declarationExpr) {
            for (VariableDeclarator declarator : declarationExpr.getVariables()) {
                declarator.getInitializer().ifPresentOrElse(
                        initializer -> assign(declarator.getNameAsString(), initializer, declarator, tainted),
                        () -> tainted.remove(declarator.getNameAsString()));
            }
        } else if (expression instanceof AssignExpr assignExpr && assignExpr.getTarget().isNameExpr()) {
            assign(assignExpr.getTarget().asNameExpr().getNameAsString(), assignExpr.getValue(), assignExpr, tainted);
        }
    }

    private void assign(String name, Expression valueExpr, Node assignmentNode, Map<String, List<TraceStep>> tainted) {
        var chain = evaluateTaint(valueExpr, tainted);
        if (chain.isEmpty()) {
            tainted.remove(name);
            return;
        }
        var updated = new ArrayList<>(chain);
        updated.add(step("assignment", assignmentNode));
        tainted.put(name, updated);
    }

    private List<TraceStep> evaluateTaint(Expression expression, Map<String, List<TraceStep>> tainted) {
        if (expression.isEnclosedExpr()) return evaluateTaint(expression.asEnclosedExpr().getInner(), tainted);
        if (expression.isCastExpr()) return evaluateTaint(expression.asCastExpr().getExpression(), tainted);
        if (expression.isNameExpr()) {
            return tainted.getOrDefault(expression.asNameExpr().getNameAsString(), List.of());
        }
        if (expression.isMethodCallExpr()) {
            var call = expression.asMethodCallExpr();
            if (TaintSanitizerCatalog.isSanitizerCall(call)) return List.of();
            if (TaintSourceCatalog.isHttpParameterCall(call)) return List.of(step("http_param", call));
            return List.of();
        }
        if (expression.isBinaryExpr()) {
            var binary = expression.asBinaryExpr();
            if (binary.getOperator() != BinaryExpr.Operator.PLUS) return List.of();
            return mergeBranches(evaluateTaint(binary.getLeft(), tainted), evaluateTaint(binary.getRight(), tainted),
                    "concatenation", binary);
        }
        if (expression.isConditionalExpr()) {
            var conditional = expression.asConditionalExpr();
            return mergeBranches(evaluateTaint(conditional.getThenExpr(), tainted),
                    evaluateTaint(conditional.getElseExpr(), tainted), "conditional", conditional);
        }
        return List.of();
    }

    private List<TraceStep> mergeBranches(List<TraceStep> left, List<TraceStep> right, String kind, Node node) {
        if (left.isEmpty() && right.isEmpty()) return List.of();
        var merged = new ArrayList<TraceStep>();
        merged.addAll(left);
        right.stream().filter(candidate -> !merged.contains(candidate)).forEach(merged::add);
        merged.add(step(kind, node));
        return merged;
    }

    private TraceStep step(String kind, Node node) {
        var range = node.getRange().orElseThrow();
        return new TraceStep(kind, range.begin.line, range.begin.column);
    }
}
