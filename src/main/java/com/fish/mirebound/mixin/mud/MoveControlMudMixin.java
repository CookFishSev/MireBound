package com.fish.mirebound.mixin.mud;

import com.fish.mirebound.mud.MudMobPhysics;
import com.fish.mirebound.mud.PhysicsTraceLog;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MoveControl.class)
public abstract class MoveControlMudMixin {
    @Shadow
    protected Mob mob;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void mirebound$avoidMudTarget(CallbackInfo ci) {
        MoveControl self = (MoveControl) (Object) this;
        if (!MudMobPhysics.shouldBlockMove(this.mob, self)) {
            return;
        }

        if (!MudMobPhysics.replanAroundMud(this.mob, self)) {
            PhysicsTraceLog.traceMob(this.mob, "move-control",
                    "blocked=true replan=false; allowing vanilla MoveControl tick");
            return;
        }
        PhysicsTraceLog.traceMob(this.mob, "move-control",
                "blocked=true replan=true; cancelling MoveControl tick");
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
        ci.cancel();
    }
}
