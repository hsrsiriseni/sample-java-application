package com.datadoghq.workshops.samplejavaapp.exception;

public class UnableToTestDomainException extends DomainTestException {
  public UnableToTestDomainException(String message) {
    super(message);
  }
}
