package com.datadoghq.workshops.samplejavaapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "url.validation")
public class URLValidationProperties {

  private Whitelist whitelist = new Whitelist();
  private Blacklist blacklist = new Blacklist();

  /**
   * If non-empty, only these ports are allowed. Use -1 (unset) to represent default ports.
   * Note: java.net.URL uses -1 when the URL omits an explicit port.
   */
  private List<Integer> allowedPorts = new ArrayList<>(List.of(80, 443));

  public Whitelist getWhitelist() {
    return whitelist;
  }

  public void setWhitelist(Whitelist whitelist) {
    this.whitelist = whitelist;
  }

  public Blacklist getBlacklist() {
    return blacklist;
  }

  public void setBlacklist(Blacklist blacklist) {
    this.blacklist = blacklist;
  }

  public List<Integer> getAllowedPorts() {
    return allowedPorts;
  }

  public void setAllowedPorts(List<Integer> allowedPorts) {
    this.allowedPorts = allowedPorts;
  }

  public static class Whitelist {
    /**
     * Domains allowed for outbound requests. Exact match or subdomain match is allowed.
     * Example: "example.com" allows "example.com" and "api.example.com".
     */
    private List<String> domains = new ArrayList<>(List.of("example.com", "httpbin.org"));

    public List<String> getDomains() {
      return domains;
    }

    public void setDomains(List<String> domains) {
      this.domains = domains;
    }
  }

  public static class Blacklist {
    /**
     * CIDR blocks to block for IPv4 destinations (defense-in-depth; private ranges are also blocked
     * via InetAddress checks).
     */
    private List<String> ipRanges = new ArrayList<>(
        List.of("127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
    );

    /**
     * Specific hostnames or IP literals to block (cloud metadata endpoints, etc).
     */
    private List<String> hosts = new ArrayList<>(
        List.of("169.254.169.254", "metadata.google.internal")
    );

    public List<String> getIpRanges() {
      return ipRanges;
    }

    public void setIpRanges(List<String> ipRanges) {
      this.ipRanges = ipRanges;
    }

    public List<String> getHosts() {
      return hosts;
    }

    public void setHosts(List<String> hosts) {
      this.hosts = hosts;
    }
  }
}

