package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;

@EnableBinding(ExampleChannels.class)
@Slf4j
public class ConditionalConfiguration {

  @StreamListener(value = ExampleChannels.SEND_MESSAGE_CONSUMER)
  public void sendMessage(final String message) {
    log.info("Received the value {} in StreamListener", message);
  }
}
