package com.datadoghq.workshops.samplejavaapp.exception;

public class InvalidDomainException extends DomainTestException {
  public InvalidDomainException(String message) {
    super(message);
  }
}
