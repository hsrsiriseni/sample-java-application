package com.datadoghq.workshops.samplejavaapp.controller;

import com.datadoghq.workshops.samplejavaapp.exception.FileForbiddenFileException;
import com.datadoghq.workshops.samplejavaapp.exception.FileReadException;
import com.datadoghq.workshops.samplejavaapp.exception.InvalidDomainException;
import com.datadoghq.workshops.samplejavaapp.exception.UnableToTestDomainException;
import com.datadoghq.workshops.samplejavaapp.http.DomainTestRequest;
import com.datadoghq.workshops.samplejavaapp.http.ViewFileRequest;
import com.datadoghq.workshops.samplejavaapp.http.WebsiteTestRequest;
import com.datadoghq.workshops.samplejavaapp.service.DomainTestService;
import com.datadoghq.workshops.samplejavaapp.service.FileService;
import com.datadoghq.workshops.samplejavaapp.service.WebsiteTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MainController {

  public Logger log = LoggerFactory.getLogger(MainController.class);

  @Autowired
  private DomainTestService domainTestService;

  @Autowired
  private WebsiteTestService websiteTestService;

  @Autowired
  private FileService fileService;

  @RequestMapping(method=RequestMethod.POST, value="/test-domain", consumes="application/json")
  public ResponseEntity<String> testDomain(@RequestBody DomainTestRequest request) {
    log.info("Testing domain {}", request.domainName);
    try {
      String result = domainTestService.testDomain(request.domainName);
      return new ResponseEntity<>(result, HttpStatus.OK);
    } catch(InvalidDomainException e) {
      log.info("Invalid domain name provided: {}", request.domainName);
      return new ResponseEntity<>("Invalid domain name", HttpStatus.BAD_REQUEST);
    } catch (UnableToTestDomainException e) {
      log.warn("Unable to test domain: {}", request.domainName, e);
      return new ResponseEntity<>("Unable to test domain", HttpStatus.INTERNAL_SERVER_ERROR);
    } catch(Exception e) {
      log.error("Unexpected error testing domain: {}", request.domainName, e);
      return new ResponseEntity<>("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @RequestMapping(method=RequestMethod.POST, value="/test-website", consumes="application/json")
  public ResponseEntity<String> testWebsite(@RequestBody WebsiteTestRequest request) {
    log.info("Testing website " + request.url);
    String result = websiteTestService.testWebsite(request);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @RequestMapping(method=RequestMethod.POST, value="/view-file", consumes="application/json")
  public ResponseEntity<String> viewFile(@RequestBody ViewFileRequest request) {
    log.info("Reading file " + request.path);
    try {
      String result = fileService.readFile(request.path);
      return new ResponseEntity<>(result, HttpStatus.OK);
    } catch (FileForbiddenFileException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.FORBIDDEN);
    } catch (FileReadException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

}
