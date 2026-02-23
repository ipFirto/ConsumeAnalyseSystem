package com.iptnet.consume.utils;

import java.util.*;
import java.util.stream.*;

public class ListUtils {

    public static List<Integer> parseIntList(String s) {
        if (s == null) return Collections.emptyList();
        s = s.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isBlank()) return Collections.emptyList();

        return Arrays.stream(s.split("\\s*,\\s*")) // 逗号分隔，自动去空格
                .filter(t -> !t.isBlank())
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }

}
