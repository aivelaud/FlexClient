package com.flex.client;

import com.flex.client.gui.ClickGui;
import com.flex.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class FlexClient implements ClientModInitializer {
    public static final String MOD_ID = "flexclient";
    public static FlexClient INSTANCE;

    // G tusu (71) - PojavLauncher mobil klavyesinde gorunuyor
    private static KeyBinding guiKey;

    private static int tickCooldown = 0;
    private static final int COOLDOWN_TICKS = 10;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();

        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "FlexClient GUI",
            InputUtil.Type.KEYSYM,
            71, // GLFW_KEY_G
            "FlexClient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (tickCooldown > 0) {
                tickCooldown--;
                return;
            }

            if (guiKey.wasPressed()) {
                if (client.currentScreen instanceof ClickGui) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new ClickGui());
                }
                tickCooldown = COOLDOWN_TICKS;
            }
        });

        System.out.println("[FlexClient] Yuklendi! Klavyeden G tusuna bas.");
    }
}
