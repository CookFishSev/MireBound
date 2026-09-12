package com.fish.mirebound.client.compat;

import com.fish.mirebound.client.ArmorAccessoryRenderContext;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.coverage.EquipmentSurfaceRenderer;
import com.fish.mirebound.client.coverage.SurfaceDrawQueue;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Context only while Sophisticated Backpacks is rendering the worn item, not its inventory icon. */
public final class BackpackSurfaceContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private BackpackSurfaceContext() {}
    public static void begin(LivingEntity entity, ItemStack stack, MultiBufferSource buffers, int light) {
        if (!MireboundClientSettings.independentSurfaceCoverage()) {
            CURRENT.remove();
            return;
        }
        EquipmentSurfaceTarget target = ArmorAccessoryRenderContext.surfaceTarget(stack);
        SurfaceDrawQueue queue = new SurfaceDrawQueue(buffers);
        CURRENT.set(new Frame(stack, queue, new EquipmentSurfaceRenderer.BakedSession(
                entity, stack, target == null ? EquipmentSurfaceTarget.backpack() : target,
                queue, light, OverlayTexture.NO_OVERLAY)));
    }
    public static void end() {
        Frame frame = CURRENT.get();
        CURRENT.remove();
        if (frame != null) {
            frame.session.finish();
            frame.buffers.flush();
        }
    }
    public static void discard() { CURRENT.remove(); }
    public static void render(List<BakedQuad> quads, ItemStack stack, PoseStack pose) {
        Frame frame = CURRENT.get();
        if (frame == null || stack != frame.stack || quads.isEmpty()) return;
        frame.session.render(quads, pose.last());
    }
    private record Frame(ItemStack stack, SurfaceDrawQueue buffers, EquipmentSurfaceRenderer.BakedSession session) {}
}
