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

    // GLFW_KEY_RIGHT_SHIFT (344) yerine INSERT (260) kullaniyoruz.
    // PojavLauncher'da sanal klavyeden INSERT gonderilebilir.
    // Istersen bunu GLFW.GLFW_KEY_F1 (290) gibi baska bir tusa da degistirebilirsin.
    private static KeyBinding guiKey;

    // GUI'nin kac tick'te bir acilip kapanacagini kontrol etmek icin sayac
    private static int tickCooldown = 0;
    private static final int COOLDOWN_TICKS = 10;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();

        // INSERT tusu (260) - PojavLauncher sanal klavyesinden erisebilirsin
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "FlexClient GUI",
            InputUtil.Type.KEYSYM,
            260, // GLFW_KEY_INSERT - RSHIFT yerine
            "FlexClient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Cooldown kontrolu - cift acilmayi engeller
            if (tickCooldown > 0) {
                tickCooldown--;
                return;
            }

            // Tus basildi mi?
            if (guiKey.wasPressed()) {
                // Eger zaten ClickGui aciksa kapat, degilse ac
                if (client.currentScreen instanceof ClickGui) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new ClickGui());
                }
                tickCooldown = COOLDOWN_TICKS;
            }
        });

        System.out.println("[FlexClient] Yuklendi! INSERT tusu ile GUI ac.");
        System.out.println("[FlexClient] PojavLauncher: Sanal klavyeden INSERT'e bas.");
    }
}
