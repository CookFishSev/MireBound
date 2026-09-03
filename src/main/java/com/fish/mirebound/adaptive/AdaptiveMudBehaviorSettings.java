package com.fish.mirebound.adaptive;

import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.MudSinkingDepthControl;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.AdaptiveMudProfileSyncPayload;
import java.util.Arrays;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-world universal behavior baseline for newly converted full blocks. */
public final class AdaptiveMudBehaviorSettings extends SavedData {
    private static final String DATA_NAME = "mirebound_adaptive_behavior";
    private static final int DATA_VERSION = 3;
    private static final Factory<AdaptiveMudBehaviorSettings> FACTORY =
            new Factory<>(AdaptiveMudBehaviorSettings::new, AdaptiveMudBehaviorSettings::load);
    private static volatile double[] clientValues = defaults();
    private static volatile MudBlockProfileStore.Profile clientProfile =
            MudBlockProfileStore.Profile.createAdaptive(clientValues);

    private double[] values = defaults();
    private MudBlockProfileStore.Profile profile =
            MudBlockProfileStore.Profile.createAdaptive(values);

    public static AdaptiveMudBehaviorSettings get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static AdaptiveMudBehaviorSettings load(
            CompoundTag tag, HolderLookup.Provider registries) {
        AdaptiveMudBehaviorSettings settings = new AdaptiveMudBehaviorSettings();
        long[] packed = tag.getLongArray("Values");
        if (packed.length > 0 && packed.length <= MudPhysicsParameter.COUNT) {
            double[] loaded = defaults();
            for (int index = 0; index < packed.length; index++) {
                loaded[index] = Double.longBitsToDouble(packed[index]);
            }
            migrateLoadedValues(tag.getInt("Version"), loaded);
            settings.replace(loaded, false);
            if (tag.getInt("Version") < DATA_VERSION) {
                settings.setDirty();
            }
        }
        return settings;
    }

    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public MudBlockProfileStore.Profile profile() {
        return profile;
    }

    public void update(double[] requested) {
        replace(requested, true);
    }

    public void reset() {
        replace(defaults(), true);
    }

    private void replace(double[] requested, boolean dirty) {
        double[] sanitized = defaults();
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            int index = parameter.ordinal();
            if (index < requested.length && parameter.appliesToAdaptive()) {
                sanitized[index] = parameter.sanitize(requested[index]);
            }
        }
        MudSinkingDepthControl.enforceSimpleBounds(sanitized);
        values = sanitized;
        profile = MudBlockProfileStore.Profile.createAdaptive(values);
        if (dirty) {
            setDirty();
        }
    }

    public static double[] defaults() {
        return MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);
    }

    public static double[] clientValues() {
        return Arrays.copyOf(clientValues, clientValues.length);
    }

    public static MudBlockProfileStore.Profile clientProfile() {
        return clientProfile;
    }

    public static void acceptClient(double[] requested) {
        AdaptiveMudBehaviorSettings scratch = new AdaptiveMudBehaviorSettings();
        scratch.replace(requested, false);
        clientValues = scratch.values;
        clientProfile = scratch.profile;
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new AdaptiveMudProfileSyncPayload(get(player.serverLevel()).values()));
    }

    public static void broadcast(ServerLevel level) {
        AdaptiveMudProfileSyncPayload payload =
                new AdaptiveMudProfileSyncPayload(get(level).values());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", DATA_VERSION);
        long[] packed = new long[values.length];
        for (int index = 0; index < values.length; index++) {
            packed[index] = Double.doubleToRawLongBits(values[index]);
        }
        tag.putLongArray("Values", packed);
        return tag;
    }

    static void migrateLoadedValues(int version, double[] loaded) {
        int closeTicks = MudPhysicsParameter.SURFACE_CLOSE_TICKS.ordinal();
        if (version < 1 && closeTicks < loaded.length
                && Math.abs(loaded[closeTicks] - 90.0D) <= 1.0E-9D) {
            loaded[closeTicks] = defaults()[closeTicks];
        }
        if (version < 2
                && MudSinkingDepthControl.mode(loaded) == MudSinkingDepthControl.Mode.SIMPLE) {
            loaded[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()] =
                    MudSinkingDepthControl.maximumDepth(
                            loaded[MudPhysicsParameter.MAX_DEPTH_FACTOR.ordinal()],
                            loaded[MudPhysicsParameter.COLUMN_MARGIN.ordinal()]);
        }
    }
}
