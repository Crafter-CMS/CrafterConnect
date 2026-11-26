package net.crafter.plugin.core;

import java.util.HashMap;
import java.util.Map;

/** Language file messages (with {0}, {1} … placeholders) */
public class LanguageManager {
    private final Map<String, String> messages = new HashMap<>();
    private final String language;

    public LanguageManager(String language) {
        this.language = language;
    }

    public void setMessages(Map<String, String> newMessages) {
        messages.clear();
        messages.putAll(newMessages);
    }

    public boolean has(String key) {
        return messages.containsKey(key);
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

    /** Strips § color codes for the console */
    public static String stripColors(String text) {
        return text == null ? "" : text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
