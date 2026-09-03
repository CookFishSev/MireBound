package com.fish.mirebound.client;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.CoverageDebugLog;
import com.fish.mirebound.network.payload.MudDeveloperOptionsPayload;
import com.fish.mirebound.client.tentacle.TentacleCameraTraceLog;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.Locale;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class ClientMudCommands {
    private ClientMudCommands() {
    }

    static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fmud")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("set")
                        .then(Commands.argument("option", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ClientMudDebugOptions.optionNames(), builder))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> setOption(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "option"),
                                                BoolArgumentType.getBool(context, "value"))))
                                .then(Commands.argument("number", FloatArgumentType.floatArg())
                                        .executes(context -> setNumberOption(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "option"),
                                                FloatArgumentType.getFloat(context, "number"))))))
                .then(Commands.literal("reset")
                        .executes(context -> resetTuning(context.getSource())))
                .then(Commands.literal("screen")
                        .then(Commands.literal("sampling")
                                .executes(context -> setScreenSampling(context.getSource(), true)))
                        .then(Commands.literal("normal")
                                .executes(context -> setScreenSampling(context.getSource(), false))))
                .then(Commands.literal("geometry")
                        .executes(context -> showGeometryMode(context.getSource()))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("model_part", "sodium_vertices", "auto"), builder))
                                .executes(context -> setGeometryMode(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "mode")))))
                .then(Commands.literal("swarm")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("full", "reduced", "off"), builder))
                                .executes(context -> setSwarmVisualMode(
                                        context.getSource(), StringArgumentType.getString(context, "mode")))))
                .then(Commands.literal("export")
                        .executes(context -> exportSkin(context.getSource()))
                        .then(Commands.literal("skin")
                                .executes(context -> exportSkin(context.getSource())))));
    }

    private static int showHelp(CommandSourceStack source) {
        send(source, Component.translatable("command.mirebound.client.help"));
        send(source, "/fmud export skin");
        send(source, Component.translatable("command.mirebound.client.help.clearstains"));
        send(source, "/fmud reset");
        send(source, "/fmud set <option> true|false");
        send(source, "/fmud set <number-option> <value>");
        send(source, "/fmud screen sampling|normal");
        send(source, "/fmud geometry model_part|sodium_vertices|auto");
        send(source, "/fmud swarm full|reduced|off");
        send(source, "coverage_debug_log=" + CoverageDebugLog.debugLogPath());
        send(source, "physics_log_dir=" + Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("Fmud"));
        send(source, "tentacle_camera_log=" + TentacleCameraTraceLog.debugLogPath());
        send(source, AnimatedPlayerGeometryCapture.status(Minecraft.getInstance().player));
        for (String option : ClientMudDebugOptions.booleanOptionNames()) {
            send(source, option + "=" + ClientMudDebugOptions.value(option));
        }
        for (String option : ClientMudDebugOptions.numberOptionNames()) {
            send(source, option + "=" + String.format(Locale.ROOT, "%.3f", ClientMudDebugOptions.numberValue(option)));
        }
        return 1;
    }

    private static int resetTuning(CommandSourceStack source) {
        ClientMudDebugOptions.resetTuning();
        send(source, Component.translatable("command.mirebound.client.reset"));
        return showHelp(source);
    }

    private static int setScreenSampling(CommandSourceStack source, boolean sampling) {
        ClientMudDebugOptions.set("screen_sampling", sampling);
        send(source, Component.translatable("command.mirebound.client.screen_sampling",
                sampling, Component.translatable(sampling
                        ? "command.mirebound.client.screen_sampling.sampled"
                        : "command.mirebound.client.screen_sampling.normal")));
        return 1;
    }

    private static int showGeometryMode(CommandSourceStack source) {
        send(source, AnimatedPlayerGeometryCapture.status(Minecraft.getInstance().player));
        send(source, Component.translatable("command.mirebound.client.geometry.model_part"));
        send(source, Component.translatable("command.mirebound.client.geometry.sodium_vertices"));
        send(source, Component.translatable("command.mirebound.client.geometry.auto"));
        return 1;
    }

    private static int setGeometryMode(CommandSourceStack source, String requestedMode) {
        ContactGeometryMode mode = ContactGeometryMode.byName(requestedMode);
        if (mode == null) {
            source.sendFailure(Component.translatable(
                    "command.mirebound.client.unknown_geometry", requestedMode));
            return 0;
        }
        com.fish.mirebound.client.config.MireboundClientSettings
                .setContactGeometryMode(mode);
        AnimatedPlayerGeometryCapture.reset();
        send(source, Component.translatable("command.mirebound.client.geometry.changed",
                mode.serializedName()));
        return 1;
    }

    private static int setSwarmVisualMode(CommandSourceStack source, String requestedMode) {
        ClientMudDebugOptions.SwarmVisualMode mode = ClientMudDebugOptions.SwarmVisualMode.byName(requestedMode);
        if (mode == null) {
            source.sendFailure(Component.translatable("command.mirebound.client.unknown_swarm", requestedMode));
            return 0;
        }
        ClientMudDebugOptions.setSwarmVisualMode(mode);
        send(source, "swarm_visual=" + mode.serializedName());
        return 1;
    }

    private static int setOption(CommandSourceStack source, String requestedOption, boolean value) {
        String option = ClientMudDebugOptions.canonicalName(requestedOption);
        if (!ClientMudDebugOptions.optionNames().contains(option)) {
            source.sendFailure(Component.translatable("command.mirebound.client.unknown_option", requestedOption));
            return 0;
        }

        ClientMudDebugOptions.set(option, value);
        if ("physics_log".equals(option)) {
            TentacleCameraTraceLog.setEnabled(value);
            PacketDistributor.sendToServer(new MudDeveloperOptionsPayload(value));
        } else if ("screen_overlay".equals(option) && !value) {
            ScreenMudOverlay.reset();
        } else if ("baked_skin".equals(option)) {
            MudSkinTextureCache.reset();
        }

        send(source, option + "=" + value);
        return 1;
    }

    private static int setNumberOption(CommandSourceStack source, String requestedOption, float value) {
        String option = ClientMudDebugOptions.canonicalName(requestedOption);
        if (!ClientMudDebugOptions.numberOptionNames().contains(option)) {
            source.sendFailure(Component.translatable("command.mirebound.client.unknown_number_option", requestedOption));
            return 0;
        }

        ClientMudDebugOptions.setNumber(option, value);
        send(source, option + "=" + String.format(Locale.ROOT, "%.3f", ClientMudDebugOptions.numberValue(option)));
        return 1;
    }

    private static int exportSkin(CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player instanceof AbstractClientPlayer player)) {
            source.sendFailure(Component.translatable(
                    "command.mirebound.client.no_local_player"));
            return 0;
        }

        PlayerSkin skin = player.getSkin();
        Path screenshots = minecraft.gameDirectory.toPath().resolve("screenshots");
        try {
            MudSkinTextureCache.DebugExportResult result = MudSkinTextureCache.exportDebugTextures(
                    player.getId(),
                    skin.texture(),
                    skin.model() == PlayerSkin.Model.SLIM,
                    screenshots);
            send(source, Component.translatable("command.mirebound.client.exported_skin"));
            send(source, result.originalSkin().toString());
            send(source, result.bakedSkin().toString());
            send(source, result.overlaySkin().toString());
            send(source, result.coverageMask().toString());
            return 1;
        } catch (IOException | RuntimeException exception) {
            Mirebound.LOGGER.warn("Failed to export Mirebound: Sinking Depths mud skin debug textures", exception);
            source.sendFailure(Component.translatable(
                    "command.mirebound.client.export_failed", exception.getMessage()));
            return 0;
        }
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }
}
