package com.datadoghq.workshops.samplejavaapp.service;

import com.datadoghq.workshops.samplejavaapp.config.URLValidationProperties;
import com.datadoghq.workshops.samplejavaapp.http.WebsiteTestRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "url.validation.whitelist.domains=example.com",
    "url.validation.blacklist.ip-ranges=127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16",
    "url.validation.blacklist.hosts=169.254.169.254,metadata.google.internal",
    "url.validation.allowed-ports=80,443"
})
@Import(WebsiteTestEndpointSecurityTests.TestConfig.class)
class WebsiteTestEndpointSecurityTests {

  @Autowired
  private TestRestTemplate client;

  @MockBean
  private RestTemplate restTemplate;

  @Test
  void testWebsite_blocksInternalIpSsrfsWithHttp400_andDoesNotCallRestTemplate() {
    WebsiteTestRequest req = new WebsiteTestRequest();
    req.url = "http://internal.example.com";

    ResponseEntity<String> resp = client.postForEntity("/test-website", req, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    verify(restTemplate, never()).exchange(anyString(), any(), any(), ArgumentMatchers.<Class<String>>any());
  }

  @Test
  void testWebsite_blocksCloudMetadataEndpointWithHttp400_andDoesNotCallRestTemplate() {
    WebsiteTestRequest req = new WebsiteTestRequest();
    req.url = "http://169.254.169.254/latest/meta-data/";

    ResponseEntity<String> resp = client.postForEntity("/test-website", req, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    verify(restTemplate, never()).exchange(anyString(), any(), any(), ArgumentMatchers.<Class<String>>any());
  }

  @Test
  void testWebsite_allowsWhitelistedExternalUrl_andCallsRestTemplate() {
    when(restTemplate.exchange(
        eq("http://example.com"),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(String.class))
    ).thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

    WebsiteTestRequest req = new WebsiteTestRequest();
    req.url = "http://example.com";

    ResponseEntity<String> resp = client.postForEntity("/test-website", req, String.class);

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals("ok", resp.getBody());
    verify(restTemplate, times(1)).exchange(eq("http://example.com"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    URLValidationService urlValidationService(URLValidationProperties props) {
      return new URLValidationService(props, (host) -> {
        // Avoid network DNS in tests: deterministically resolve specific hosts.
        if ("example.com".equals(host)) {
          return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
        }
        if ("internal.example.com".equals(host)) {
          return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        }
        // IP literals (e.g., 169.254.169.254) resolve without external DNS.
        if (host.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$")) {
          return InetAddress.getAllByName(host);
        }
        throw new UnknownHostException(host);
      });
    }
  }
}

