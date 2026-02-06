package com.datadoghq.workshops.samplejavaapp.service;

import com.datadoghq.workshops.samplejavaapp.config.URLValidationProperties;
import com.datadoghq.workshops.samplejavaapp.exception.InvalidURLException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class URLValidationServiceTest {

  @Test
  void validateURL_allowsWhitelistedDomainWithPublicIp() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> {
      if ("example.com".equals(host)) {
        return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
      }
      throw new UnknownHostException(host);
    });

    assertTrue(svc.validateURL("https://example.com"));
  }

  @Test
  void validateURL_blocksLoopbackResolutionEvenWhenDomainIsWhitelisted() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> {
      if ("internal.example.com".equals(host)) {
        return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
      }
      if ("example.com".equals(host)) {
        return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
      }
      throw new UnknownHostException(host);
    });

    InvalidURLException ex = assertThrows(
        InvalidURLException.class,
        () -> svc.validateURL("http://internal.example.com")
    );
    assertEquals(InvalidURLException.Reason.DISALLOWED_IP, ex.getReason());
  }

  @Test
  void validateURL_blocksCloudMetadataIpLiteral() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> InetAddress.getAllByName(host));

    InvalidURLException ex = assertThrows(
        InvalidURLException.class,
        () -> svc.validateURL("http://169.254.169.254/latest/meta-data/")
    );
    assertEquals(InvalidURLException.Reason.DISALLOWED_HOST, ex.getReason());
  }

  @Test
  void validateURL_blocksCloudMetadataHostname() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> InetAddress.getAllByName(host));

    InvalidURLException ex = assertThrows(
        InvalidURLException.class,
        () -> svc.validateURL("http://metadata.google.internal/")
    );
    assertEquals(InvalidURLException.Reason.DISALLOWED_HOST, ex.getReason());
  }

  @Test
  void validateURL_blocksNonHttpProtocols() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> InetAddress.getAllByName(host));

    InvalidURLException ex = assertThrows(
        InvalidURLException.class,
        () -> svc.validateURL("file:///etc/passwd")
    );
    assertEquals(InvalidURLException.Reason.DISALLOWED_PROTOCOL, ex.getReason());
  }

  @Test
  void validateURL_blocksMalformedUrls() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> InetAddress.getAllByName(host));

    InvalidURLException ex = assertThrows(
        InvalidURLException.class,
        () -> svc.validateURL("http://")
    );
    assertEquals(InvalidURLException.Reason.MALFORMED_URL, ex.getReason());
  }

  @Test
  void validateURL_blocksDisallowedPorts() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> {
      if ("example.com".equals(host)) {
        return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
      }
      throw new UnknownHostException(host);
    });

    InvalidURLException ex = assertThrows(
        InvalidURLException.class,
        () -> svc.validateURL("http://example.com:8080")
    );
    assertEquals(InvalidURLException.Reason.DISALLOWED_PORT, ex.getReason());
  }

  @Test
  void validateURL_blocksLocalhost() throws Exception {
    URLValidationService svc = newServiceWithResolver((host) -> InetAddress.getAllByName(host));

    InvalidURLException ex = assertThrows(
        InvalidURLException.class,
        () -> svc.validateURL("http://localhost/")
    );
    assertEquals(InvalidURLException.Reason.DISALLOWED_DOMAIN, ex.getReason());
  }

  private static URLValidationService newServiceWithResolver(URLValidationService.HostResolver resolver) {
    URLValidationProperties props = new URLValidationProperties();
    props.getWhitelist().setDomains(List.of("example.com"));
    props.getBlacklist().setIpRanges(List.of("127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"));
    props.getBlacklist().setHosts(List.of("169.254.169.254", "metadata.google.internal"));
    props.setAllowedPorts(List.of(80, 443));
    return new URLValidationService(props, resolver);
  }
}

