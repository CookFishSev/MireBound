package com.fish.mirebound.mixin.client.tentacle;

import com.fish.mirebound.client.tentacle.TentacleGrabCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererGrabPickMixin {
    private static final String PICK_METHOD =
            "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;";

    @ModifyExpressionValue(method = PICK_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 mirebound$useRagdollEyeForPicking(Vec3 original, Entity entity,
            double blockRange, double entityRange, float partialTick) {
        Vec3 origin = TentacleGrabCamera.interactionRayOrigin(entity, partialTick);
        return origin == null ? original : origin;
    }

    @ModifyExpressionValue(method = PICK_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 mirebound$useRagdollDirectionForEntityPicking(Vec3 original, Entity entity,
            double blockRange, double entityRange, float partialTick) {
        Vec3 direction = TentacleGrabCamera.interactionViewVector(entity, partialTick);
        return direction == null ? original : direction;
    }

    @WrapOperation(method = PICK_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult mirebound$pickFromRagdollEye(Entity entity, double distance,
            float partialTick, boolean includeFluids, Operation<HitResult> original) {
        boolean scoped = TentacleGrabCamera.beginScopedInteractionPick(entity, partialTick);
        try {
            return original.call(entity, distance, partialTick, includeFluids);
        } finally {
            if (scoped) {
                TentacleGrabCamera.endScopedInteractionPick();
            }
        }
    }

    @WrapOperation(method = PICK_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB mirebound$includeRagdollEyeInEntitySearch(
            Entity entity, Operation<AABB> original) {
        AABB bounds = original.call(entity);
        Vec3 origin = TentacleGrabCamera.interactionRayOrigin(entity, 1.0F);
        return origin == null ? bounds : bounds.minmax(new AABB(origin, origin));
    }
}
