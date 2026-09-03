package com.fish.mirebound.physics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PlayerGravityControlTest {
    @Test
    void interruptedSessionRestoresGravityOnNextLogin() {
        PlayerGravityControl.Registry firstProcess = new PlayerGravityControl.Registry();
        FakeSubject beforeShutdown = new FakeSubject(false, new CompoundTag());

        assertFalse(firstProcess.acquire(
                beforeShutdown, PlayerGravityControl.Owner.TENTACLE_HOLD));
        assertTrue(beforeShutdown.isNoGravity());
        assertTrue(firstProcess.hasMarker(beforeShutdown));

        // Simulate player NBT after the JVM and its WeakHashMap have disappeared.
        FakeSubject afterRestart = new FakeSubject(
                beforeShutdown.isNoGravity(), beforeShutdown.persistentData().copy());
        PlayerGravityControl.Registry secondProcess = new PlayerGravityControl.Registry();

        assertTrue(secondProcess.restoreOriginal(afterRestart));
        assertFalse(afterRestart.isNoGravity());
        assertFalse(secondProcess.hasMarker(afterRestart));
    }

    @Test
    void preexistingNoGravityIsPreservedAcrossInterruptedSession() {
        PlayerGravityControl.Registry firstProcess = new PlayerGravityControl.Registry();
        FakeSubject beforeShutdown = new FakeSubject(true, new CompoundTag());

        assertTrue(firstProcess.acquire(beforeShutdown, PlayerGravityControl.Owner.MUD));
        FakeSubject afterRestart = new FakeSubject(
                true, beforeShutdown.persistentData().copy());
        PlayerGravityControl.Registry secondProcess = new PlayerGravityControl.Registry();

        assertTrue(secondProcess.restoreOriginal(afterRestart));
        assertTrue(afterRestart.isNoGravity());
        assertFalse(secondProcess.hasMarker(afterRestart));
    }

    @Test
    void finalOwnerRestoresGravityButEarlierReleaseDoesNot() {
        PlayerGravityControl.Registry registry = new PlayerGravityControl.Registry();
        FakeSubject player = new FakeSubject(false, new CompoundTag());

        registry.acquire(player, PlayerGravityControl.Owner.MUD);
        registry.acquire(player, PlayerGravityControl.Owner.TENTACLE_HOLD);
        registry.release(player, PlayerGravityControl.Owner.MUD);

        assertTrue(player.isNoGravity());
        assertTrue(registry.hasMarker(player));

        registry.release(player, PlayerGravityControl.Owner.TENTACLE_HOLD);

        assertFalse(player.isNoGravity());
        assertFalse(registry.hasMarker(player));
    }

    @Test
    void normalLogoutRestoresAndClearsTheSavedSession() {
        PlayerGravityControl.Registry registry = new PlayerGravityControl.Registry();
        FakeSubject player = new FakeSubject(false, new CompoundTag());
        registry.acquire(player, PlayerGravityControl.Owner.TENTACLE_HOLD);

        assertTrue(registry.restoreOriginal(player));
        assertFalse(player.isNoGravity());
        assertFalse(registry.hasMarker(player));
    }

    @Test
    void nonDeathCloneReceivesTheStateFromBeforeMireboundTookOwnership() {
        PlayerGravityControl.Registry registry = new PlayerGravityControl.Registry();
        FakeSubject original = new FakeSubject(false, new CompoundTag());
        registry.acquire(original, PlayerGravityControl.Owner.TENTACLE_HOLD);
        FakeSubject clone = new FakeSubject(true, original.persistentData().copy());

        PlayerGravityControl.RestoredState restored =
                registry.restoreOriginalState(original);
        registry.finishClone(clone, restored, false);

        assertTrue(restored.managed());
        assertFalse(original.isNoGravity());
        assertFalse(clone.isNoGravity());
        assertFalse(registry.hasMarker(original));
        assertFalse(registry.hasMarker(clone));
    }

    @Test
    void markerlessLegacyNoGravityIsNotCopiedIntoADeathClone() {
        PlayerGravityControl.Registry registry = new PlayerGravityControl.Registry();
        FakeSubject original = new FakeSubject(true, new CompoundTag());
        // ServerPlayer.restoreFrom creates a fresh entity with vanilla gravity.
        FakeSubject clone = new FakeSubject(false, new CompoundTag());

        PlayerGravityControl.RestoredState restored =
                registry.restoreOriginalState(original);
        registry.finishClone(clone, restored, true);

        assertFalse(restored.managed());
        assertTrue(original.isNoGravity());
        assertFalse(clone.isNoGravity());
        assertFalse(registry.hasMarker(clone));
    }

    @Test
    void deathCloneKeepsVanillaGravityEvenForAManagedSession() {
        PlayerGravityControl.Registry registry = new PlayerGravityControl.Registry();
        FakeSubject original = new FakeSubject(true, new CompoundTag());
        registry.acquire(original, PlayerGravityControl.Owner.TENTACLE_HOLD);
        FakeSubject clone = new FakeSubject(false, original.persistentData().copy());

        PlayerGravityControl.RestoredState restored =
                registry.restoreOriginalState(original);
        registry.finishClone(clone, restored, true);

        assertTrue(restored.managed());
        assertTrue(original.isNoGravity());
        assertFalse(clone.isNoGravity());
        assertFalse(registry.hasMarker(original));
        assertFalse(registry.hasMarker(clone));
    }

    private static final class FakeSubject implements PlayerGravityControl.Subject {
        private final Object identity = new Object();
        private boolean noGravity;
        private final CompoundTag persistentData;

        private FakeSubject(boolean noGravity, CompoundTag persistentData) {
            this.noGravity = noGravity;
            this.persistentData = persistentData;
        }

        @Override
        public Object identity() {
            return identity;
        }

        @Override
        public boolean isNoGravity() {
            return noGravity;
        }

        @Override
        public void setNoGravity(boolean noGravity) {
            this.noGravity = noGravity;
        }

        @Override
        public CompoundTag persistentData() {
            return persistentData;
        }
    }
}
