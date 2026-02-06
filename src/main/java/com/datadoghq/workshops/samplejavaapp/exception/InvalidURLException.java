package com.datadoghq.workshops.samplejavaapp.exception;

/**
 * Thrown when a user-supplied URL is malformed or disallowed by SSRF protections.
 *
 * Intentionally uses sanitized messages that do not disclose internal network details.
 */
public class InvalidURLException extends RuntimeException {

  public enum Reason {
    MALFORMED_URL,
    DISALLOWED_PROTOCOL,
    DISALLOWED_PORT,
    DISALLOWED_HOST,
    DISALLOWED_DOMAIN,
    DISALLOWED_IP,
    UNRESOLVABLE_HOST
  }

  private final Reason reason;

  public InvalidURLException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public InvalidURLException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}

