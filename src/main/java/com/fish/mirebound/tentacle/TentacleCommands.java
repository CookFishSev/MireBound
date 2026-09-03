package com.fish.mirebound.tentacle;

import com.fish.mirebound.mud.MudPhysicsSettings;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Brigadier command subtree for runtime procedural-tentacle diagnostics and control. */
public final class TentacleCommands {
    private TentacleCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("tentacle")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(context -> spawn(
                                context.getSource(), 1.0D, context.getSource().getPosition()))
                        .then(Commands.argument("volume",
                                        DoubleArgumentType.doubleArg(0.015625D, 125.0D))
                                .executes(context -> spawn(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "volume"),
                                        context.getSource().getPosition()))
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(context -> spawn(
                                                context.getSource(),
                                                DoubleArgumentType.getDouble(context, "volume"),
                                                Vec3Argument.getVec3(context, "position"))))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> remove(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("grab")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.literal("mode")
                                        .then(Commands.argument("behavior", StringArgumentType.word())
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(
                                                                Arrays.stream(TentacleGrabMode.values())
                                                                        .map(TentacleGrabMode::serializedName),
                                                                builder))
                                                .executes(context -> setGrabMode(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                context, "id"),
                                                        StringArgumentType.getString(
                                                                context, "behavior")))))
                                .then(Commands.literal("release")
                                        .executes(context -> releaseGrab(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"))))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setGrabEnabled(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"),
                                                BoolArgumentType.getBool(context, "enabled"))))))
                .then(Commands.literal("state")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.literal("idle")
                                        .executes(context -> setIdle(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"))))
                                .then(Commands.literal("emerge")
                                        .executes(context -> emerge(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"))))
                                .then(Commands.literal("retract")
                                        .executes(context -> retract(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"))))
                                .then(Commands.literal("track")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> track(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                context, "id"),
                                                        EntityArgument.getPlayer(
                                                                context, "player")))))));
    }

    private static int spawn(CommandSourceStack source, double volume, Vec3 position) {
        double maximumVolume = MudPhysicsSettings.tentacleProfile().maximumVolume();
        double clampedVolume = Math.min(volume, maximumVolume);
        int id = TentacleSystem.spawn(source.getLevel(), position, clampedVolume);
        if (id < 0) {
            return fail(source, "commands.mirebound.tentacle.limit");
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.tentacle.created", id,
                formatPosition(position),
                String.format(Locale.ROOT, "%.3f", clampedVolume)), true);
        return id;
    }

    private static int list(CommandSourceStack source) {
        var descriptions = TentacleSystem.describe(source.getLevel());
        if (descriptions.isEmpty()) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.mirebound.tentacle.list_empty"), false);
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.mirebound.tentacle.list", descriptions.size()), false);
        descriptions.forEach(
                line -> source.sendSuccess(() -> Component.literal(line), false));
        return descriptions.size();
    }

    private static int clear(CommandSourceStack source) {
        int count = TentacleSystem.clear(source.getLevel());
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.mirebound.tentacle.cleared", count), true);
        return count;
    }

    private static int remove(CommandSourceStack source, int id) {
        if (!TentacleSystem.remove(source.getLevel(), id)) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.tentacle.removed", id), true);
        return 1;
    }

    private static int setIdle(CommandSourceStack source, int id) {
        if (!TentacleSystem.setIdle(source.getLevel(), id)) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.tentacle.idle", id), true);
        return 1;
    }

    private static int emerge(CommandSourceStack source, int id) {
        if (!TentacleSystem.emerge(source.getLevel(), id)) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.mirebound.tentacle.emerging", id), true);
        return 1;
    }

    private static int retract(CommandSourceStack source, int id) {
        if (!TentacleSystem.retract(source.getLevel(), id)) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.tentacle.retracting", id), true);
        return 1;
    }

    private static int track(CommandSourceStack source, int id, ServerPlayer player) {
        if (!TentacleSystem.setTracking(source.getLevel(), id, player.getUUID())) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.tentacle.tracking", id,
                player.getGameProfile().getName()), true);
        return 1;
    }

    private static int setGrabEnabled(
            CommandSourceStack source, int id, boolean enabled) {
        TentacleSystem.GrabEnableResult result =
                TentacleSystem.setGrabEnabled(source.getLevel(), id, enabled);
        if (result == TentacleSystem.GrabEnableResult.UNKNOWN_INSTANCE) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        if (result == TentacleSystem.GrabEnableResult.VOLUME_TOO_SMALL) {
            return fail(source, "commands.mirebound.tentacle.grab_too_small", id,
                    String.format(Locale.ROOT, "%.3f",
                            TentacleSystem.MINIMUM_GRAB_VOLUME));
        }
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "commands.mirebound.tentacle.grab_enabled"
                        : "commands.mirebound.tentacle.grab_disabled",
                id), true);
        return 1;
    }

    private static int setGrabMode(CommandSourceStack source, int id, String name) {
        TentacleGrabMode mode = TentacleGrabMode.byName(name);
        if (mode == null) {
            return fail(source,
                    "commands.mirebound.tentacle.unknown_grab_mode", name);
        }
        if (!TentacleSystem.setGrabMode(source.getLevel(), id, mode)) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.tentacle.grab_mode", id,
                mode.serializedName()), true);
        return 1;
    }

    private static int releaseGrab(CommandSourceStack source, int id) {
        if (!TentacleSystem.releaseGrab(source.getLevel(), id)) {
            return fail(source, "commands.mirebound.tentacle.unknown", id);
        }
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.mirebound.tentacle.released", id), true);
        return 1;
    }

    private static String formatPosition(Vec3 position) {
        return String.format(
                Locale.ROOT, "%.2f %.2f %.2f", position.x, position.y, position.z);
    }

    private static int fail(CommandSourceStack source, String key, Object... arguments) {
        source.sendFailure(Component.translatable(key, arguments));
        return 0;
    }
}
