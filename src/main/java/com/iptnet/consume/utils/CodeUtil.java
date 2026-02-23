package com.iptnet.consume.utils;

public class CodeUtil {
    public static String generateCode() {
        return String.valueOf((int)((Math.random() * 9 + 1) * 100000));
    }
}
