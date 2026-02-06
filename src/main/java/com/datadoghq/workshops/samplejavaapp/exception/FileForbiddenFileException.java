package com.datadoghq.workshops.samplejavaapp.exception;

public class FileForbiddenFileException extends Exception {
    public FileForbiddenFileException(String message) {
        super(message);
    }
}

