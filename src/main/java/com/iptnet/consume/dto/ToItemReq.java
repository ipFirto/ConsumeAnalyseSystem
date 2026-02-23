package com.iptnet.consume.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class ToItemReq {
    private Integer pid;
    private Integer cityId;
    // getter/setter
}
