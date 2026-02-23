package com.iptnet.consume.exception;

import com.iptnet.consume.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        e.printStackTrace();
        String msg = "请求方法不支持，请使用: "
                + StringUtils.arrayToDelimitedString(e.getSupportedMethods(), ", ");
        return Result.fail(msg);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public Result handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        e.printStackTrace();
        return Result.fail("请求格式不支持，请检查 Content-Type / Accept");
    }

    @ExceptionHandler(NullPointerException.class)
    public Result handleNullPointer(NullPointerException e) {
        e.printStackTrace();
        return Result.fail("数据为空，请检查参数");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result handleIllegalArgument(IllegalArgumentException e) {
        e.printStackTrace();
        return Result.fail("参数非法: " + (StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "无效参数"));
    }

    @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
    public void handleClientDisconnect(Exception e, HttpServletRequest request) throws Exception {
        if (isClientDisconnectError(e)) {
            log.debug("Client disconnected during response write, uri={}, msg={}", request.getRequestURI(), e.getMessage());
            return;
        }
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception e) {
        if (isClientDisconnectError(e)) {
            return null;
        }
        e.printStackTrace();
        String msg = StringUtils.hasLength(e.getMessage()) ? e.getMessage() : "操作失败";
        return Result.fail(msg);
    }

    private boolean isClientDisconnectError(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String className = t.getClass().getName();
            if (className.contains("AsyncRequestNotUsableException")
                    || className.contains("ClientAbortException")) {
                return true;
            }

            String msg = t.getMessage();
            if (msg == null) {
                continue;
            }
            String lower = msg.toLowerCase();
            if (lower.contains("broken pipe")
                    || lower.contains("connection reset by peer")
                    || lower.contains("forcibly closed")
                    || msg.contains("断开的管道")
                    || msg.contains("连接被对方重置")) {
                return true;
            }
        }
        return false;
    }
}
