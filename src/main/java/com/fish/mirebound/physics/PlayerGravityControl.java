package com.fish.mirebound.physics;

import com.fish.mirebound.Mirebound;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Coordinates temporary no-gravity ownership between mod systems.
 *
 * <p>The first owner captures the player's pre-existing state. The original state is restored
 * only after the final owner releases it, so mud exit and tentacle release order cannot leave a
 * player permanently floating or re-enable gravity while another in-mod controller still needs it.
 */
public final class PlayerGravityControl {
    private static final String SESSION_KEY = Mirebound.MOD_ID + ":gravity_control";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_ORIGINAL_NO_GRAVITY = "OriginalNoGravity";
    private static final int SESSION_VERSION = 1;

    public enum Owner {
        MUD,
        TENTACLE_HOLD,
        ASSIMILATION,
        DIMENSION_TRANSITION
    }

    private static final Registry RUNTIME = new Registry();

    private PlayerGravityControl() {
    }

    public static synchronized boolean acquire(Player player, Owner owner) {
        return RUNTIME.acquire(new PlayerSubject(player), owner);
    }

    public static synchronized void release(Player player, Owner owner) {
        RUNTIME.release(new PlayerSubject(player), owner);
    }

    public static synchronized void releaseAll(Player player) {
        RUNTIME.restoreOriginal(new PlayerSubject(player));
    }

    /**
     * Repairs a session that was saved while a mod controller owned gravity.
     * The marker and NoGravity value are written into the same player NBT, so either
     * both survive an interrupted shutdown or neither does.
     */
    public static synchronized boolean restoreAfterInterruptedSession(Player player) {
        return RUNTIME.restoreOriginal(new PlayerSubject(player));
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreAfterInterruptedSession(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            releaseAll(player);
        }
    }

    public static synchronized void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getOriginal() instanceof ServerPlayer original)) {
            return;
        }
        RestoredState restored = RUNTIME.restoreOriginalState(
                new PlayerSubject(original));
        RUNTIME.finishClone(new PlayerSubject(player), restored, event.isWasDeath());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            releaseAll(player);
        }
    }

    interface Subject {
        Object identity();

        boolean isNoGravity();

        void setNoGravity(boolean noGravity);

        CompoundTag persistentData();
    }

    static final class Registry {
        private final Map<Object, State> states = new WeakHashMap<>();

        boolean acquire(Subject subject, Owner owner) {
            Object identity = subject.identity();
            State state = states.get(identity);
            if (state == null) {
                // A marker without an in-memory state can only belong to an earlier
                // server process. Restore it before opening the new ownership session.
                restoreMarker(subject);
                state = new State(subject.isNoGravity());
                states.put(identity, state);
                writeMarker(subject, state.originalNoGravity);
            }
            state.owners.add(owner);
            subject.setNoGravity(true);
            return state.originalNoGravity;
        }

        void release(Subject subject, Owner owner) {
            State state = states.get(subject.identity());
            if (state == null) {
                restoreMarker(subject);
                return;
            }
            if (!state.owners.remove(owner)) {
                return;
            }
            if (state.owners.isEmpty()) {
                states.remove(subject.identity());
                clearMarker(subject);
                subject.setNoGravity(state.originalNoGravity);
            } else {
                subject.setNoGravity(true);
            }
        }

        boolean restoreOriginal(Subject subject) {
            return restoreOriginalState(subject).managed();
        }

        RestoredState restoreOriginalState(Subject subject) {
            Object identity = subject.identity();
            State state = states.remove(identity);
            Boolean markedOriginal = readMarkedOriginal(subject);
            if (state == null && markedOriginal == null) {
                // This value was not created by a mod ownership session.
                // In particular, do not copy an old markerless NoGravity=true into
                // a fresh death clone: vanilla intentionally resets it on respawn.
                return RestoredState.UNMANAGED;
            }
            boolean original = state != null
                    ? state.originalNoGravity : markedOriginal;
            clearMarker(subject);
            subject.setNoGravity(original);
            return new RestoredState(true, original);
        }

        void finishClone(Subject clone, RestoredState restored, boolean wasDeath) {
            states.remove(clone.identity());
            clearMarker(clone);
            // A death clone starts with vanilla's default gravity. Do not overwrite
            // that default, even if the dead entity entered this session with a
            // pre-existing NoGravity value. Non-death clones retain the original
            // pre-session state when this controller actually managed the source.
            if (restored.managed() && !wasDeath) {
                clone.setNoGravity(restored.originalNoGravity());
            }
        }

        boolean hasMarker(Subject subject) {
            return subject.persistentData().contains(SESSION_KEY, Tag.TAG_COMPOUND);
        }

        private static void writeMarker(Subject subject, boolean originalNoGravity) {
            CompoundTag marker = new CompoundTag();
            marker.putInt(TAG_VERSION, SESSION_VERSION);
            marker.putBoolean(TAG_ORIGINAL_NO_GRAVITY, originalNoGravity);
            subject.persistentData().put(SESSION_KEY, marker);
        }

        private static boolean restoreMarker(Subject subject) {
            Boolean original = readMarkedOriginal(subject);
            if (original == null) {
                return false;
            }
            clearMarker(subject);
            subject.setNoGravity(original);
            return true;
        }

        private static Boolean readMarkedOriginal(Subject subject) {
            CompoundTag persistentData = subject.persistentData();
            if (!persistentData.contains(SESSION_KEY, Tag.TAG_COMPOUND)) {
                return null;
            }
            CompoundTag marker = persistentData.getCompound(SESSION_KEY);
            if (marker.getInt(TAG_VERSION) != SESSION_VERSION) {
                return null;
            }
            return marker.getBoolean(TAG_ORIGINAL_NO_GRAVITY);
        }

        private static void clearMarker(Subject subject) {
            subject.persistentData().remove(SESSION_KEY);
        }
    }

    private record PlayerSubject(Player player) implements Subject {
        @Override
        public Object identity() {
            return player;
        }

        @Override
        public boolean isNoGravity() {
            return player.isNoGravity();
        }

        @Override
        public void setNoGravity(boolean noGravity) {
            player.setNoGravity(noGravity);
        }

        @Override
        public CompoundTag persistentData() {
            return player.getPersistentData();
        }
    }

    private static final class State {
        private final boolean originalNoGravity;
        private final EnumSet<Owner> owners = EnumSet.noneOf(Owner.class);

        private State(boolean originalNoGravity) {
            this.originalNoGravity = originalNoGravity;
        }
    }

    record RestoredState(boolean managed, boolean originalNoGravity) {
        private static final RestoredState UNMANAGED = new RestoredState(false, false);
    }
}
