package com.iptnet.consume.service.dashboard;

import java.util.HashSet;
import java.util.Set;

public class TopicMatcher {

    public static Set<String> parseTopics(String topics) {
        if (topics == null || topics.isBlank()) {
            return Set.of("home");
        }

        String[] arr = topics.split(",");
        HashSet<String> set = new HashSet<>();
        for (String s : arr) {
            if (s == null) {
                continue;
            }
            String token = s.trim();
            if (!token.isEmpty()) {
                set.add(token);
            }
        }
        return set.isEmpty() ? Set.of("home") : Set.copyOf(set);
    }

    public static boolean match(Set<String> subscribed, String eventTopic) {
        if (subscribed == null || subscribed.isEmpty()) {
            return false;
        }
        if (eventTopic == null || eventTopic.isBlank()) {
            return false;
        }
        if (subscribed.contains("all") || subscribed.contains(eventTopic)) {
            return true;
        }

        for (String topic : subscribed) {
            if (topic == null || topic.isBlank()) {
                continue;
            }

            String token = topic.trim();
            if (token.endsWith("*")) {
                String prefix = token.substring(0, token.length() - 1);
                if (eventTopic.startsWith(prefix)) {
                    return true;
                }
                continue;
            }

            if (eventTopic.equals(token)
                    || eventTopic.startsWith(token + ".")
                    || eventTopic.startsWith(token + ":")) {
                return true;
            }
        }
        return false;
    }
}
