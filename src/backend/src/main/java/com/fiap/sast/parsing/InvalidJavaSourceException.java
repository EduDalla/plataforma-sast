package com.fiap.sast.parsing;
public class InvalidJavaSourceException extends RuntimeException {
    public final String fileName;
    public final int line;
    public final int column;

    public InvalidJavaSourceException(String message, int line, int column) {
        this(message, null, line, column);
    }

    public InvalidJavaSourceException(String message, String fileName, int line, int column) {
        super(message);
        this.fileName = fileName;
        this.line = line;
        this.column = column;
    }
}
