package com.datadoghq.workshops.samplejavaapp.service;

import com.datadoghq.workshops.samplejavaapp.exception.InvalidURLException;
import com.datadoghq.workshops.samplejavaapp.http.WebsiteTestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class WebsiteTestService {
    private final Logger log = LoggerFactory.getLogger(WebsiteTestService.class);

    @Autowired
    private RestTemplate rest;

    @Autowired
    private URLValidationService urlValidationService;

    public String testWebsite(WebsiteTestRequest request) {
        try {
            // SSRF protection: validate before processing any user-controlled headers.
            urlValidationService.validateURL(request.url);

            HttpHeaders headers = new HttpHeaders();
            if (request.customHeaderKey != null && !request.customHeaderKey.isEmpty()) {
                headers.set(request.customHeaderKey, request.customHeaderValue);
            }

            HttpEntity<String> entity = new HttpEntity<>("", headers);

            return this.rest.exchange(request.url, HttpMethod.GET, entity, String.class).getBody();
        } catch (InvalidURLException e) {
            log.warn("Blocked website test URL. reason={}", e.getReason());
            throw e;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return "URL returned status code: " + e.getStatusCode();
        }
    }
}
