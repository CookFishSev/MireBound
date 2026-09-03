package com.fish.mirebound.mixin.mud;

import com.fish.mirebound.mud.MudMobPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Final movement guard for mobs whose AI bypasses PathNavigation/MoveControl.
 */
@Mixin(Entity.class)
public abstract class EntityMudMovementMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, require = 1)
    private Vec3 mirebound$clipMobEntry(Vec3 movement) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Mob mob)) {
            return movement;
        }
        return MudMobPhysics.clipMovement(mob, movement);
    }
}
