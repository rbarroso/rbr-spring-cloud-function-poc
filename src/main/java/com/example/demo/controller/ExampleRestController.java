package com.example.demo.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ExampleRestController {

  private static final String DESTINATION = "rbr-test.MESSAGES";

  @Autowired
  private StreamBridge streamBridge;


  @GetMapping("/invoke")
  public String invoke() {
    log.info("Sending message to queue...");
    streamBridge.send(DESTINATION, "put this in upper case");
    return "true";
  }
}
