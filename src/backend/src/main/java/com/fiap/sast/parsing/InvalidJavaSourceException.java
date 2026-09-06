package com.fiap.sast.parsing;
public class InvalidJavaSourceException extends RuntimeException { public final int line; public final int column; public InvalidJavaSourceException(String message,int line,int column){super(message);this.line=line;this.column=column;} }
