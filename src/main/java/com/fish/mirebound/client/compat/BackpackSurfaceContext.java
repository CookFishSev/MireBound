package com.fish.mirebound.client.compat;

import com.fish.mirebound.client.ArmorAccessoryRenderContext;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.coverage.EquipmentSurfaceRenderer;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Context only while Sophisticated Backpacks is rendering the worn item, not its inventory icon. */
public final class BackpackSurfaceContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private BackpackSurfaceContext() {}
    public static void begin(LivingEntity entity, ItemStack stack, MultiBufferSource buffers) {
        if (!MireboundClientSettings.independentSurfaceCoverage()) {
            CURRENT.remove();
            return;
        }
        EquipmentSurfaceTarget target = ArmorAccessoryRenderContext.surfaceTarget(stack);
        CURRENT.set(new Frame(entity, stack, new com.fish.mirebound.client.coverage.SurfaceDrawQueue(buffers), target == null ? EquipmentSurfaceTarget.backpack() : target));
    }
    public static void end() {
        Frame frame = CURRENT.get(); CURRENT.remove();
        if (frame != null) frame.buffers.flush();
    }
    public static void discard() { CURRENT.remove(); }
    public static void render(List<BakedQuad> quads, ItemStack stack, PoseStack pose, int light, int overlay) {
        Frame frame = CURRENT.get();
        if (frame == null || stack != frame.stack || quads.isEmpty()) return;
        EquipmentSurfaceRenderer.baked(frame.entity, frame.stack, frame.target, quads, pose.last(), frame.buffers, light, overlay);
    }
    private record Frame(LivingEntity entity, ItemStack stack, com.fish.mirebound.client.coverage.SurfaceDrawQueue buffers, EquipmentSurfaceTarget target) {}
}
