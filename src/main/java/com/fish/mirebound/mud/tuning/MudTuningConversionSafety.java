package com.fish.mirebound.mud.tuning;

import com.fish.mirebound.network.payload.MudTuningConversionSafetyPayload;
import com.fish.mirebound.network.payload.MudTuningRequestPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns the per-save, per-player acknowledgements required for adaptive conversion. */
public final class MudTuningConversionSafety {
    static final String PERSISTENT_KEY = "mirebound_conversion_unlocked";
    static final String UNRESTRICTED_UNLOCKED_KEY =
            "mirebound_conversion_unrestricted_unlocked";
    static final String UNRESTRICTED_ENABLED_KEY =
            "mirebound_conversion_unrestricted_enabled";

    private MudTuningConversionSafety() {
    }

    public static boolean isUnlocked(ServerPlayer player) {
        return isUnlocked(player.getPersistentData());
    }

    public static boolean isUnrestrictedUnlocked(ServerPlayer player) {
        return isUnrestrictedUnlocked(player.getPersistentData());
    }

    public static boolean isUnrestrictedEnabled(ServerPlayer player) {
        return isUnrestrictedEnabled(player.getPersistentData());
    }

    public static Change advance(ServerPlayer player) {
        Change change = advance(player.getPersistentData());
        sync(player);
        MudTuningManager.conversionSafetyChanged(player);
        if (change == Change.UNRESTRICTED_UNLOCKED) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.tuning.unrestricted_unlocked"), true);
        } else if (change == Change.UNRESTRICTED_ENABLED) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.tuning.unrestricted_enabled"), true);
        } else if (change == Change.UNRESTRICTED_DISABLED) {
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.tuning.unrestricted_disabled"), true);
        }
        return change;
    }

    public static boolean requiresUnlock(MudTuningRequestPayload.Action action) {
        return switch (action) {
            case CONVERT_SINGLE, RESTORE_SINGLE, CONVERT_RANGE, RESTORE_RANGE -> true;
            default -> false;
        };
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag originalData = original.getPersistentData();
        CompoundTag playerData = player.getPersistentData();
        setUnlocked(playerData, isUnlocked(originalData));
        setUnrestrictedUnlocked(playerData, isUnrestrictedUnlocked(originalData));
        setUnrestrictedEnabled(playerData, isUnrestrictedEnabled(originalData));
    }

    static boolean isUnlocked(CompoundTag data) {
        return data.getBoolean(PERSISTENT_KEY);
    }

    static void setUnlocked(CompoundTag data, boolean unlocked) {
        if (unlocked) {
            data.putBoolean(PERSISTENT_KEY, true);
        } else {
            data.remove(PERSISTENT_KEY);
            data.remove(UNRESTRICTED_UNLOCKED_KEY);
            data.remove(UNRESTRICTED_ENABLED_KEY);
        }
    }

    static boolean isUnrestrictedUnlocked(CompoundTag data) {
        return isUnlocked(data) && data.getBoolean(UNRESTRICTED_UNLOCKED_KEY);
    }

    static void setUnrestrictedUnlocked(CompoundTag data, boolean unlocked) {
        if (unlocked) {
            setUnlocked(data, true);
            data.putBoolean(UNRESTRICTED_UNLOCKED_KEY, true);
        } else {
            data.remove(UNRESTRICTED_UNLOCKED_KEY);
            data.remove(UNRESTRICTED_ENABLED_KEY);
        }
    }

    static boolean isUnrestrictedEnabled(CompoundTag data) {
        return isUnrestrictedUnlocked(data) && data.getBoolean(UNRESTRICTED_ENABLED_KEY);
    }

    static void setUnrestrictedEnabled(CompoundTag data, boolean enabled) {
        if (enabled) {
            setUnrestrictedUnlocked(data, true);
            data.putBoolean(UNRESTRICTED_ENABLED_KEY, true);
        } else {
            data.remove(UNRESTRICTED_ENABLED_KEY);
        }
    }

    static Change advance(CompoundTag data) {
        if (!isUnlocked(data)) {
            setUnlocked(data, true);
            return Change.STANDARD_UNLOCKED;
        }
        if (!isUnrestrictedUnlocked(data)) {
            setUnrestrictedEnabled(data, true);
            return Change.UNRESTRICTED_UNLOCKED;
        }
        boolean enabled = !isUnrestrictedEnabled(data);
        setUnrestrictedEnabled(data, enabled);
        return enabled ? Change.UNRESTRICTED_ENABLED : Change.UNRESTRICTED_DISABLED;
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new MudTuningConversionSafetyPayload(
                        isUnlocked(player),
                        isUnrestrictedUnlocked(player),
                        isUnrestrictedEnabled(player)));
    }

    public enum Change {
        STANDARD_UNLOCKED,
        UNRESTRICTED_UNLOCKED,
        UNRESTRICTED_ENABLED,
        UNRESTRICTED_DISABLED
    }
}
