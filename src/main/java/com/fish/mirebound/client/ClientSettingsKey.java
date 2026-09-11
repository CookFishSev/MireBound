package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Opens the local Mirebound settings from the default F11 shortcut. */
public final class ClientSettingsKey {
    private static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.mirebound.open_client_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F11,
            "key.categories.mirebound");

    private ClientSettingsKey() {
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
    }

    /** Consumes the key before vanilla uses F11 for fullscreen. */
    public static boolean handleKey(long window, int key, int scanCode, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS
                || minecraft.getWindow().getWindow() != window
                || minecraft.player == null
                || minecraft.screen != null
                || !OPEN_SETTINGS.matches(key, scanCode)) {
            return false;
        }
        minecraft.setScreen(new MireboundClientConfigScreen(null));
        return true;
    }
}
