package com.datadoghq.workshops.samplejavaapp.service;

import com.datadoghq.workshops.samplejavaapp.exception.DomainTestException;
import com.datadoghq.workshops.samplejavaapp.exception.InvalidDomainException;
import com.datadoghq.workshops.samplejavaapp.exception.UnableToTestDomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DomainTestService {

  private static final Logger log = LoggerFactory.getLogger(DomainTestService.class);

  static final int timeoutMs = 10_000;
  static final int minDomainLength = 3; // "a.b"
  static final int maxDomainLength = 253; // RFC 1035/2181 practical maximum for FQDN text form

  static final Pattern domainCharWhitelist = Pattern.compile("^[a-z0-9._-]+$");
  static final Pattern domainValidationRegex = Pattern.compile(
      "^((?!-))(xn--)?[a-z0-9][a-z0-9-_]{0,61}[a-z0-9]{0,1}\\.(xn--)?([a-z0-9\\-]{1,61}|[a-z0-9-]{1,30}\\.[a-z]{2,})$",
      Pattern.CASE_INSENSITIVE
  );

  public String testDomain(String domainName) throws DomainTestException {
    String normalizedDomainName = normalizeAndValidateDomainName(domainName);

    try {
      ProcessBuilder processBuilder = buildPingProcess(normalizedDomainName);
      Process process = processBuilder.start();

      if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw new UnableToTestDomainException("Timed out pinging domain");
      }

      int exitCode = process.exitValue();
      String stdout;
      String stderr;
      try (InputStream stdoutStream = process.getInputStream();
           InputStream stderrStream = process.getErrorStream()) {
        stdout = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
        stderr = new String(stderrStream.readAllBytes(), StandardCharsets.UTF_8);
      }

      if (exitCode != 0) {
        // Keep details server-side for troubleshooting, but do not return them to the client.
        log.warn("Ping failed for domain={} exitCode={} stderr={}", normalizedDomainName, exitCode, stderr);
        throw new UnableToTestDomainException("Unable to test domain");
      }

      return stdout;
    } catch (IOException e) {
      log.error("IOException while testing domain={}", normalizedDomainName, e);
      throw new UnableToTestDomainException("Unable to test domain");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while testing domain={}", normalizedDomainName, e);
      throw new UnableToTestDomainException("Timed out pinging domain");
    }
  }

  /**
   * Normalizes (trim + IDN to ASCII) and validates a domain name string.
   * Package-private for test coverage.
   */
  static String normalizeAndValidateDomainName(String domainName) throws InvalidDomainException {
    if (domainName == null) {
      throw new InvalidDomainException("Invalid domain name");
    }

    String trimmed = domainName.trim();
    if (trimmed.isEmpty()) {
      throw new InvalidDomainException("Invalid domain name");
    }

    if (trimmed.length() < minDomainLength || trimmed.length() > maxDomainLength) {
      throw new InvalidDomainException("Invalid domain name");
    }

    final String ascii;
    try {
      ascii = IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      throw new InvalidDomainException("Invalid domain name");
    }

    if (ascii.length() < minDomainLength || ascii.length() > maxDomainLength) {
      throw new InvalidDomainException("Invalid domain name");
    }

    if (ascii.startsWith(".") || ascii.endsWith(".") || ascii.contains("..")) {
      throw new InvalidDomainException("Invalid domain name");
    }

    if (!domainCharWhitelist.matcher(ascii).matches()) {
      throw new InvalidDomainException("Invalid domain name");
    }

    // Defense-in-depth: validate label shapes (length + leading/trailing hyphen)
    String[] labels = ascii.split("\\.");
    if (labels.length < 2) {
      throw new InvalidDomainException("Invalid domain name");
    }
    for (String label : labels) {
      if (label.isEmpty() || label.length() > 63) {
        throw new InvalidDomainException("Invalid domain name");
      }
      if (label.startsWith("-") || label.endsWith("-")) {
        throw new InvalidDomainException("Invalid domain name");
      }
    }

    if (!isValidDomainName(ascii)) {
      throw new InvalidDomainException("Invalid domain name");
    }

    return ascii;
  }

  ProcessBuilder buildPingProcess(String domainName) {
    return new ProcessBuilder("ping", "-c", "1", domainName);
  }

  private static boolean isValidDomainName(String domainName) {
    Matcher matcher = domainValidationRegex.matcher(domainName);
    return matcher.matches();
  }
}
