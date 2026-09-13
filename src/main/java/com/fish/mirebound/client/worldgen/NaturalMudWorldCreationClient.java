package com.fish.mirebound.client.worldgen;

import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile;
import com.fish.mirebound.generation.natural.NaturalMudWorldCreationBridge;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/** Client-owned draft profiles for create-world screens. */
public final class NaturalMudWorldCreationClient {
    private static final Map<CreateWorldScreen, NaturalMudGenerationProfile> DRAFTS =
            new WeakHashMap<>();

    private NaturalMudWorldCreationClient() {
    }

    public static boolean supports(CreateWorldScreen screen) {
        var overworld = screen.getUiState().getSettings().selectedDimensions().dimensions()
                .get(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);
        return overworld != null && !(overworld.generator()
                instanceof net.minecraft.world.level.levelgen.FlatLevelSource);
    }

    public static NaturalMudGenerationProfile profile(CreateWorldScreen screen) {
        return DRAFTS.computeIfAbsent(screen,
                ignored -> NaturalMudGenerationProfile.defaults());
    }

    public static void update(
            CreateWorldScreen screen, NaturalMudGenerationProfile profile) {
        DRAFTS.put(screen, profile);
    }

    public static void open(CreateWorldScreen screen) {
        Minecraft.getInstance().setScreen(
                new NaturalMudWorldgenScreen(screen, profile(screen)));
    }

    public static void stage(CreateWorldScreen screen) {
        if (supports(screen)) {
            NaturalMudWorldCreationBridge.stage(profile(screen));
        } else {
            NaturalMudWorldCreationBridge.clear();
        }
    }

    public static void discard(CreateWorldScreen screen) {
        DRAFTS.remove(screen);
        NaturalMudWorldCreationBridge.clear();
    }
}
