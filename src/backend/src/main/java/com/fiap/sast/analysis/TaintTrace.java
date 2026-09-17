package com.fiap.sast.analysis;

import java.util.List;

public record TaintTrace(String engineVersion, TraceStep source, List<TraceStep> steps, TraceStep sink) {}
