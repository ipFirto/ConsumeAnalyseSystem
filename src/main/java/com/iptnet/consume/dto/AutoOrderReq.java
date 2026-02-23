package com.iptnet.consume.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AutoOrderReq {
    @NotNull
    private Long cityId;
    private BigDecimal amount; // 可选：不传就随机/固定
    private String remark;     // 可选

    public Long getCityId() { return cityId; }
    public void setCityId(Long cityId) { this.cityId = cityId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
