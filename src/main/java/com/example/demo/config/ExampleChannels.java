/*
 * Copyright 2005-2020 Alfresco Software, Ltd. All rights reserved.
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 */
package com.example.demo.config;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.messaging.SubscribableChannel;

public interface ExampleChannels {

  String SEND_MESSAGE_CONSUMER = "sendMessages";

  @Input(SEND_MESSAGE_CONSUMER)
  SubscribableChannel sendMessage();
}
