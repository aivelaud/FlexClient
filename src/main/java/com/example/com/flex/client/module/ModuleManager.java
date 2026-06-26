package com.flex.client.module;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public static final List<Module> modules = new ArrayList<>();

    public static void init() {
        modules.add(new Module("KillAura", "Combat"));
        modules.add(new Module("Fly", "Movement"));
        modules.add(new Module("Speed", "Movement"));
        modules.add(new Module("Xray", "Render"));
    }

    public static Module get(String name) {
        for (Module m : modules) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    public static boolean isEnabled(String name) {
        Module m = get(name);
        return m != null && m.isEnabled();
    }
}
