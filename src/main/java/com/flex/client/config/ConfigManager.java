package com.flex.client.config;

import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.util.Map;

/**
 * FlexClient ConfigManager — Gson tabanlı JSON config sistemi.
 * Modüllerin enabled durumu, keybind ve tüm ayarları
 * .minecraft/config/flexclient/config.json dosyasına kaydeder / yükler.
 */
public class ConfigManager {

    private static final String CONFIG_REL = "config/flexclient/config.json";
    private static final Gson   GSON       = new GsonBuilder().setPrettyPrinting().create();

    // ── Kaydet ──────────────────────────────────────────────────
    public static void save() {
        try {
            Path path = getConfigPath();
            if (path.getParent() != null) Files.createDirectories(path.getParent());

            JsonObject root    = new JsonObject();
            JsonObject modList = new JsonObject();

            for (Module m : ModuleManager.modules) {
                JsonObject entry    = new JsonObject();
                JsonObject settings = new JsonObject();

                entry.addProperty("enabled", m.isEnabled());
                entry.addProperty("keybind", m.getKeybind());

                for (Map.Entry<String, Boolean> e : m.getBoolSettings().entrySet())
                    settings.addProperty(e.getKey(), e.getValue());
                for (Map.Entry<String, Integer> e : m.getIntSettings().entrySet())
                    settings.addProperty(e.getKey(), e.getValue());
                for (Map.Entry<String, Float> e : m.getFloatSettings().entrySet())
                    settings.addProperty(e.getKey(), e.getValue());
                for (Map.Entry<String, String> e : m.getStringSettings().entrySet())
                    settings.addProperty(e.getKey(), e.getValue());

                entry.add("settings", settings);
                modList.add(m.getName(), entry);
            }

            root.add("modules", modList);
            root.addProperty("version", "4.0");
            Files.writeString(path, GSON.toJson(root));
        } catch (Exception e) {
            // Kaydetme hatası — sessizce yoksay
        }
    }

    // ── Yükle ───────────────────────────────────────────────────
    public static void load() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) return;

            String content = Files.readString(path);
            if (content == null || content.isBlank()) return;

            JsonObject root = GSON.fromJson(content, JsonObject.class);
            if (root == null || !root.has("modules")) return;

            JsonObject modList = root.getAsJsonObject("modules");

            for (Module m : ModuleManager.modules) {
                if (!modList.has(m.getName())) continue;
                JsonElement el = modList.get(m.getName());
                if (!el.isJsonObject()) continue;
                JsonObject entry = el.getAsJsonObject();

                if (entry.has("enabled")) m.setEnabled(entry.get("enabled").getAsBoolean());
                if (entry.has("keybind")) m.setKeybind(entry.get("keybind").getAsInt());

                if (!entry.has("settings")) continue;
                JsonObject settings = entry.getAsJsonObject("settings");

                for (String key : m.getBoolSettings().keySet()) {
                    if (settings.has(key) && settings.get(key).isJsonPrimitive())
                        m.setSetting(key, settings.get(key).getAsBoolean());
                }
                for (String key : m.getIntSettings().keySet()) {
                    if (settings.has(key) && settings.get(key).isJsonPrimitive())
                        m.setIntSetting(key, settings.get(key).getAsInt());
                }
                for (String key : m.getFloatSettings().keySet()) {
                    if (settings.has(key) && settings.get(key).isJsonPrimitive())
                        m.setFloatSetting(key, settings.get(key).getAsFloat());
                }
                for (String key : m.getStringSettings().keySet()) {
                    if (settings.has(key) && settings.get(key).isJsonPrimitive())
                        m.setStringSetting(key, settings.get(key).getAsString());
                }
            }
        } catch (Exception e) {
            // Yükleme hatası — sessizce yoksay, varsayılanlar kullanılır
        }
    }

    // ── Yardımcı ────────────────────────────────────────────────
    private static Path getConfigPath() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            File runDir = (mc != null && mc.runDirectory != null) ? mc.runDirectory : new File(".");
            return runDir.toPath().resolve(CONFIG_REL);
        } catch (Exception e) {
            return Path.of(CONFIG_REL);
        }
    }
}
