package com.datadoghq.workshops.samplejavaapp.service;

import com.datadoghq.workshops.samplejavaapp.config.URLValidationProperties;
import com.datadoghq.workshops.samplejavaapp.exception.InvalidURLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class URLValidationService {

  private static final String GENERIC_INVALID_MESSAGE = "Invalid or disallowed URL";

  @FunctionalInterface
  interface HostResolver {
    InetAddress[] resolveAllByName(String host) throws UnknownHostException;
  }

  private final Logger log = LoggerFactory.getLogger(URLValidationService.class);
  private final URLValidationProperties props;
  private final HostResolver hostResolver;
  private final List<Ipv4Cidr> blockedIpv4Cidrs;

  @Autowired
  public URLValidationService(URLValidationProperties props) {
    this(props, InetAddress::getAllByName);
  }

  // Visible for tests (allows a fake resolver to avoid network DNS).
  URLValidationService(URLValidationProperties props, HostResolver hostResolver) {
    this.props = Objects.requireNonNull(props, "props");
    this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver");
    List<String> ranges = props.getBlacklist() == null ? List.of() : props.getBlacklist().getIpRanges();
    this.blockedIpv4Cidrs = parseBlockedCidrs(ranges);
  }

  /**
   * Validates a user supplied URL for SSRF protections. Returns true if allowed, otherwise throws.
   */
  public boolean validateURL(String rawUrl) {
    if (rawUrl == null || rawUrl.trim().isEmpty()) {
      throw new InvalidURLException(InvalidURLException.Reason.MALFORMED_URL, GENERIC_INVALID_MESSAGE);
    }

    final URL url = parseUrl(rawUrl.trim());

    final String protocol = url.getProtocol();
    if (protocol == null || !(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
      throw new InvalidURLException(InvalidURLException.Reason.DISALLOWED_PROTOCOL, GENERIC_INVALID_MESSAGE);
    }

    // Block URLs that embed credentials or confusing userinfo (e.g., http://good.com@evil.com).
    String userInfo = null;
    try {
      userInfo = url.toURI().getUserInfo();
    } catch (URISyntaxException ignored) {
      // If it can't be represented as a URI, treat it as malformed below/elsewhere.
    }
    if (userInfo != null && !userInfo.isEmpty()) {
      throw new InvalidURLException(InvalidURLException.Reason.MALFORMED_URL, GENERIC_INVALID_MESSAGE);
    }

    final String host = url.getHost();
    if (host == null || host.isBlank()) {
      throw new InvalidURLException(InvalidURLException.Reason.MALFORMED_URL, GENERIC_INVALID_MESSAGE);
    }

    final String normalizedHost = host.toLowerCase(Locale.ROOT);

    if (isExplicitlyBlacklistedHost(normalizedHost)) {
      log.warn("Blocked URL by host blacklist. host={}", normalizedHost);
      throw new InvalidURLException(InvalidURLException.Reason.DISALLOWED_HOST, GENERIC_INVALID_MESSAGE);
    }

    if (!isWhitelistedDomain(normalizedHost)) {
      log.warn("Blocked URL by domain whitelist. host={}", normalizedHost);
      throw new InvalidURLException(InvalidURLException.Reason.DISALLOWED_DOMAIN, GENERIC_INVALID_MESSAGE);
    }

    final int port = url.getPort(); // -1 means "not explicitly specified"
    if (!isAllowedPort(port)) {
      log.warn("Blocked URL by port policy. host={} port={}", normalizedHost, port);
      throw new InvalidURLException(InvalidURLException.Reason.DISALLOWED_PORT, GENERIC_INVALID_MESSAGE);
    }

    // Resolve all A/AAAA records and block if any result is internal/private/link-local/etc (DNS rebinding defense).
    final InetAddress[] resolved = resolveAll(host, normalizedHost);
    for (InetAddress addr : resolved) {
      if (isBlockedAddress(addr) || isBlockedByCidr(addr)) {
        log.warn("Blocked URL by IP policy. host={}", normalizedHost);
        throw new InvalidURLException(InvalidURLException.Reason.DISALLOWED_IP, GENERIC_INVALID_MESSAGE);
      }
    }

    return true;
  }

  private URL parseUrl(String rawUrl) {
    try {
      return new URL(rawUrl);
    } catch (MalformedURLException e) {
      throw new InvalidURLException(InvalidURLException.Reason.MALFORMED_URL, GENERIC_INVALID_MESSAGE, e);
    }
  }

  private InetAddress[] resolveAll(String host, String normalizedHost) {
    try {
      return hostResolver.resolveAllByName(host);
    } catch (UnknownHostException e) {
      log.warn("Blocked URL due to unresolvable host. host={}", normalizedHost);
      throw new InvalidURLException(InvalidURLException.Reason.UNRESOLVABLE_HOST, GENERIC_INVALID_MESSAGE, e);
    }
  }

  private boolean isAllowedPort(int port) {
    List<Integer> allowed = props.getAllowedPorts();
    if (allowed == null || allowed.isEmpty()) {
      return true;
    }
    // URL uses -1 when port isn't explicitly specified (default port based on scheme).
    if (port == -1) {
      return true;
    }
    return allowed.contains(port);
  }

  private boolean isExplicitlyBlacklistedHost(String normalizedHost) {
    List<String> blacklist = props.getBlacklist() == null ? null : props.getBlacklist().getHosts();
    if (blacklist == null) {
      return false;
    }
    for (String entry : blacklist) {
      if (entry == null) {
        continue;
      }
      if (normalizedHost.equals(entry.trim().toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private boolean isWhitelistedDomain(String normalizedHost) {
    if ("localhost".equals(normalizedHost)) {
      return false;
    }

    List<String> whitelist = props.getWhitelist() == null ? null : props.getWhitelist().getDomains();
    if (whitelist == null || whitelist.isEmpty()) {
      // Fail closed if misconfigured.
      return false;
    }

    for (String entry : whitelist) {
      if (entry == null) {
        continue;
      }
      String domain = entry.trim().toLowerCase(Locale.ROOT);
      if (domain.isEmpty()) {
        continue;
      }
      if (normalizedHost.equals(domain)) {
        return true;
      }
      if (normalizedHost.endsWith("." + domain)) {
        return true;
      }
    }
    return false;
  }

  private boolean isBlockedByCidr(InetAddress addr) {
    if (!(addr instanceof java.net.Inet4Address)) {
      return false;
    }
    int ip = ipv4ToInt(addr);
    for (Ipv4Cidr cidr : blockedIpv4Cidrs) {
      if (cidr.contains(ip)) {
        return true;
      }
    }
    return false;
  }

  private boolean isBlockedAddress(InetAddress addr) {
    if (addr.isAnyLocalAddress()) { // 0.0.0.0 / ::
      return true;
    }
    if (addr.isLoopbackAddress()) {
      return true;
    }
    if (addr.isLinkLocalAddress()) { // 169.254.0.0/16, fe80::/10
      return true;
    }
    if (addr.isSiteLocalAddress()) { // RFC1918 (and some IPv6 site-local legacy)
      return true;
    }
    if (addr.isMulticastAddress()) {
      return true;
    }

    if (addr instanceof Inet6Address inet6) {
      // Block IPv6 Unique Local Addresses (fc00::/7) which aren't always covered by isSiteLocalAddress().
      byte[] b = inet6.getAddress();
      if (b.length >= 1) {
        int first = b[0] & 0xFF;
        if ((first & 0xFE) == 0xFC) {
          return true;
        }
      }
    }
    return false;
  }

  private static int ipv4ToInt(InetAddress addr) {
    byte[] bytes = addr.getAddress();
    return ByteBuffer.wrap(bytes).getInt();
  }

  private static List<Ipv4Cidr> parseBlockedCidrs(List<String> cidrs) {
    List<Ipv4Cidr> out = new ArrayList<>();
    if (cidrs == null) {
      return out;
    }
    for (String s : cidrs) {
      if (s == null) {
        continue;
      }
      String trimmed = s.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      out.add(Ipv4Cidr.parse(trimmed));
    }
    return out;
  }

  private record Ipv4Cidr(int network, int maskBits, int mask) {
    static Ipv4Cidr parse(String cidr) {
      String[] parts = cidr.split("/", -1);
      if (parts.length != 2) {
        throw new IllegalStateException("Invalid CIDR in url.validation.blacklist.ip-ranges");
      }
      int bits;
      try {
        bits = Integer.parseInt(parts[1]);
      } catch (NumberFormatException e) {
        throw new IllegalStateException("Invalid CIDR in url.validation.blacklist.ip-ranges", e);
      }
      if (bits < 0 || bits > 32) {
        throw new IllegalStateException("Invalid CIDR in url.validation.blacklist.ip-ranges");
      }
      InetAddress base;
      try {
        base = InetAddress.getByName(parts[0]);
      } catch (Exception e) {
        throw new IllegalStateException("Invalid CIDR in url.validation.blacklist.ip-ranges", e);
      }
      if (!(base instanceof java.net.Inet4Address)) {
        throw new IllegalStateException("Only IPv4 CIDRs are supported in url.validation.blacklist.ip-ranges");
      }
      int mask = bits == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - bits));
      int network = ipv4ToInt(base) & mask;
      return new Ipv4Cidr(network, bits, mask);
    }

    boolean contains(int ipv4) {
      return (ipv4 & mask) == network;
    }
  }
}

