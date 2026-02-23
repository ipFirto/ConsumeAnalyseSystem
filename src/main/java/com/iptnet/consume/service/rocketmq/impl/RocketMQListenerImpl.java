//package com.iptnet.consume.service.rocketmq.impl;
//
//import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
//import org.apache.rocketmq.spring.core.RocketMQListener;
//import org.springframework.stereotype.Service;
//
//@Service
//@RocketMQMessageListener(topic = "delay_close_order_topic", consumerGroup = "test-consumer-group")
//public class RocketMQListenerImpl implements RocketMQListener<String> {
//
//    @Override
//    public void onMessage(String msg) {
//        System.out.println("收到消息：" + msg);
//    }
//
//}
//
