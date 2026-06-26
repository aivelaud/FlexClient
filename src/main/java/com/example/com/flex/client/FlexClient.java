package com.flex.client;

import com.flex.client.gui.ClickGui;
import com.flex.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FlexClient implements ClientModInitializer {
    public static final String MOD_ID = "flexclient";
    public static FlexClient INSTANCE;
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();

        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "FlexClient GUI",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "FlexClient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (guiKey.wasPressed()) {
                client.setScreen(new ClickGui());
            }
        });

        System.out.println("[FlexClient] Yuklendi! RSHIFT ile GUI ac.");
    }
}
