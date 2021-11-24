package com.example.demo.config;

import java.util.Date;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.PollableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;

@Configuration
@Slf4j
public class RabbitConfiguration {

  @PollableBean
  public Supplier<Date> simpleSupplier() {
    return () -> {
//      log.info("Supplier example execute every second");
      return new Date(12345L);
    };
  }

  @Bean
  public Function<Flux<String>, Flux<String>> toUpperCaseProcessor() {
    return stringFlux -> stringFlux
        .map(i -> toUpperCase(i));
  }

  @Bean
  public Consumer<Message<String>> onUpperCaseMessage() {
    return (message) -> log.info("Received upper case message: {}", message.getPayload());
  }

  @Bean
  public String beanValue() {
    return "beanValue";
  }

  @Bean
  public Consumer<Message<String>> onReceive(@Autowired final String beanValue) {
    return (message) -> {
      log.info("External bean value: {}", beanValue);
      if (message.getHeaders().get("expectedHeader").equals("1")) {
        log.info("Received the value {} in Consumer", message);
      } else {
        log.info("Filtering headers from message" + message);
      }
    };
  }

  private String toUpperCase(String word) {
    log.info("Original string {}", word);
    return word.toUpperCase();
  }
}
