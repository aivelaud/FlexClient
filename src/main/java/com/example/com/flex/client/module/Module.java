package com.flex.client.module;

import java.util.HashMap;
import java.util.Map;

public class Module {
    private final String name;
    private final String category;
    private boolean enabled;
    private final Map<String, Boolean> settings = new HashMap<>();

    public Module(String name, String category) {
        this.name = name;
        this.category = category;
        this.enabled = false;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public void toggle() { this.enabled = !this.enabled; }

    public void setSetting(String key, boolean value) {
        settings.put(key, value);
    }

    public boolean getSetting(String key) {
        return settings.getOrDefault(key, false);
    }

    public boolean isHitAnimals() {
        return getSetting("hitAnimals");
    }
}
