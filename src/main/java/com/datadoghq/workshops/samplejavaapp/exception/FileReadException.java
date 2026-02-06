package com.datadoghq.workshops.samplejavaapp.exception;

public class FileReadException extends Exception {
    public FileReadException(String message) {
        super(message);
    }
}

