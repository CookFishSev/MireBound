package com.fish.mirebound.command;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.stain.MudFootprintLedger;
import com.fish.mirebound.tentacle.TentacleCommands;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MudCommands {
    private MudCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        var mediumArgument = Commands.argument("medium", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(SinkingMedium.values()).map(SinkingMedium::serializedName), builder))
                .executes(context -> showProfile(context.getSource(),
                        StringArgumentType.getString(context, "medium")))
                .then(Commands.literal("reset")
                        .executes(context -> resetProfile(context.getSource(),
                                StringArgumentType.getString(context, "medium"))))
                .then(Commands.literal("set")
                        .then(Commands.argument("parameter", StringArgumentType.word())
                                .suggests((context, builder) -> suggestParameters(
                                        StringArgumentType.getString(context, "medium"), builder))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(context -> setProfileValue(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "medium"),
                                                StringArgumentType.getString(context, "parameter"),
                                                DoubleArgumentType.getDouble(context, "value"))))));
        event.getDispatcher().register(Commands.literal("fmud")
                .then(Commands.literal("clearstains")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> clearStains(context.getSource())))
                .then(Commands.literal("assimilation")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(context -> clearAssimilation(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "players")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("value",
                                                        DoubleArgumentType.doubleArg(0.0D, 1.0D))
                                                .executes(context -> setAssimilation(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "players"),
                                                        DoubleArgumentType.getDouble(
                                                                context, "value")))))))
                .then(TentacleCommands.command())
                .then(Commands.literal("profile")
                        .requires(source -> source.hasPermission(2))
                        .then(mediumArgument)));
    }

    private static int clearStains(CommandSourceStack source) {
        int cleared = MudFootprintLedger.get(source.getServer().overworld()).clearAll(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands.mirebound.clear_stains.success", cleared), true);
        return cleared;
    }

    private static int clearAssimilation(CommandSourceStack source,
            java.util.Collection<net.minecraft.server.level.ServerPlayer> players) {
        players.forEach(AssimilationSystem::clear);
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.assimilation.clear.success", players.size()), true);
        return players.size();
    }

    private static int setAssimilation(CommandSourceStack source,
            java.util.Collection<net.minecraft.server.level.ServerPlayer> players,
            double progress) {
        float value = (float) progress;
        players.forEach(player -> AssimilationSystem.setProgress(player, value));
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.assimilation.set.success", players.size(), value), true);
        return players.size();
    }

    private static int showProfile(CommandSourceStack source, String name) {
        SinkingMedium medium = medium(name);
        if (medium == null) {
            return fail(source, "commands.mirebound.profile.unknown_medium", name);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.profile.header", medium.serializedName()), false);
        double[] values = MudPhysicsSettings.values(medium);
        for (MudPhysicsParameter parameter : MudPhysicsParameter.forMedium(medium)) {
            String line = parameter.serializedName() + "="
                    + String.format(Locale.ROOT, "% ." + parameter.decimals() + "f", values[parameter.ordinal()]).trim();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int resetProfile(CommandSourceStack source, String name) {
        SinkingMedium medium = medium(name);
        if (medium == null) {
            return fail(source, "commands.mirebound.profile.unknown_medium", name);
        }
        MudPhysicsSettings.reset(medium);
        MudPhysicsSettings.broadcast(source.getLevel(), medium);
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.profile.reset", medium.serializedName()), true);
        return 1;
    }

    private static int setProfileValue(CommandSourceStack source, String mediumName,
            String parameterName, double value) {
        SinkingMedium medium = medium(mediumName);
        MudPhysicsParameter parameter = parameter(parameterName);
        if (medium == null) {
            return fail(source, "commands.mirebound.profile.unknown_medium", mediumName);
        }
        if (parameter == null || !parameter.appliesTo(medium)) {
            return fail(source, "commands.mirebound.profile.unknown_parameter",
                    medium.serializedName(), parameterName);
        }
        double[] values = MudPhysicsSettings.values(medium);
        values[parameter.ordinal()] = value;
        MudPhysicsSettings.update(medium, values);
        MudPhysicsSettings.broadcast(source.getLevel(), medium);
        double stored = MudPhysicsSettings.value(medium, parameter);
        source.sendSuccess(() -> Component.translatable(
                "commands.mirebound.profile.set",
                medium.serializedName(), parameter.serializedName(), stored), true);
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestParameters(
            String mediumName, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        SinkingMedium medium = medium(mediumName);
        return SharedSuggestionProvider.suggest(medium == null
                ? java.util.stream.Stream.empty()
                : Arrays.stream(MudPhysicsParameter.forMedium(medium)).map(MudPhysicsParameter::serializedName), builder);
    }

    private static SinkingMedium medium(String name) {
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (medium.serializedName().equalsIgnoreCase(name)) {
                return medium;
            }
        }
        return null;
    }

    private static MudPhysicsParameter parameter(String name) {
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (parameter.serializedName().equalsIgnoreCase(name)) {
                return parameter;
            }
        }
        return null;
    }

    private static int fail(CommandSourceStack source, String key, Object... arguments) {
        source.sendFailure(Component.translatable(key, arguments));
        return 0;
    }
}
