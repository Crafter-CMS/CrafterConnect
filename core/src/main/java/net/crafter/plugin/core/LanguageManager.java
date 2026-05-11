package net.crafter.plugin.core;

import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final Map<String, String> messages = new HashMap<>();
    private String language;

    public LanguageManager(String language) {
        this.language = language;
    }

    public void setMessages(Map<String, String> newMessages) {
        messages.clear();
        messages.putAll(newMessages);
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "§cMessage not found: " + key);
    }

    public String getMessage(String key, Object... args) {
        String msg = getMessage(key);
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    public String getLanguage() {
        return language;
    }
}
