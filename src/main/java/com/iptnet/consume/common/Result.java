package com.iptnet.consume.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result(200, "success", data);
    }

    public static Result<?> fail(String msg) {
        return new Result(500,msg,null);
    }
}

