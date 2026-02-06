package com.datadoghq.workshops.samplejavaapp.service;

import com.datadoghq.workshops.samplejavaapp.exception.InvalidDomainException;
import org.junit.jupiter.api.Test;

import java.net.IDN;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DomainTestServiceSecurityTests {

  @Test
  void normalizeAndValidateDomainName_trimsAndLowercases() throws Exception {
    assertEquals("example.com", DomainTestService.normalizeAndValidateDomainName("  Example.COM  "));
  }

  @Test
  void normalizeAndValidateDomainName_supportsIdnViaPunycode() throws Exception {
    String input = "täst.de";
    String expected = IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    assertEquals(expected, DomainTestService.normalizeAndValidateDomainName(input));
  }

  @Test
  void normalizeAndValidateDomainName_rejectsNullEmptyAndWhitespace() {
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName(null));
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName(""));
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName("   "));
  }

  @Test
  void normalizeAndValidateDomainName_rejectsShellMetacharactersAndWhitespace() {
    List<String> payloads = List.of(
        "example.com;whoami",
        "example.com && whoami",
        "example.com|whoami",
        "example.com`whoami`",
        "example.com$(whoami)",
        "example.com\nwhoami",
        "ex ample.com",
        "example.com/../../etc/passwd"
    );

    for (String payload : payloads) {
      assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName(payload), payload);
    }
  }

  @Test
  void normalizeAndValidateDomainName_rejectsInvalidLabelShapes() {
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName("-example.com"));
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName("example-.com"));
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName(".example.com"));
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName("example.com."));
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName("example..com"));
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName("localhost"));
  }

  @Test
  void normalizeAndValidateDomainName_rejectsTooLongDomains() {
    String label = "a".repeat(63);
    String tooLong = String.join(".", label, label, label, label) + ".com"; // > 253 overall, labels are valid
    assertTrue(tooLong.length() > DomainTestService.maxDomainLength);
    assertThrows(InvalidDomainException.class, () -> DomainTestService.normalizeAndValidateDomainName(tooLong));
  }

  @Test
  void buildPingProcess_usesArgumentSeparationAndNoShell() {
    DomainTestService service = new DomainTestService();
    ProcessBuilder pb = service.buildPingProcess("example.com");
    assertEquals(List.of("ping", "-c", "1", "example.com"), pb.command());
    assertFalse(pb.command().contains("sh"));
    assertFalse(pb.command().contains("-c"));
  }
}

