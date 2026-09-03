package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Captures the model-space muzzle for both local and remote rendered water guns. */
final class WaterGunNozzleFocus {
    static final float NOZZLE_X = 8.0F / 16.0F;
    static final float NOZZLE_Y = 12.2F / 16.0F;
    static final float NOZZLE_Z = -15.45F / 16.0F;
    private static final double MAX_SAMPLE_AGE_TICKS = 3.0D;
    private static final Map<Integer, Sample> THIRD_PERSON = new HashMap<>();
    private static Sample firstPerson;
    private static int renderedPlayerId = -1;

    private WaterGunNozzleFocus() {
    }

    static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        renderedPlayerId = event.getEntity().getId();
    }

    static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (renderedPlayerId == event.getEntity().getId()) {
            renderedPlayerId = -1;
        }
    }

    static void capture(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || stack.getItem() != ModBlocks.WATER_GUN.get()) {
            return;
        }
        Vector3f rendered = poseStack.last().pose().transformPosition(
                NOZZLE_X, NOZZLE_Y, NOZZLE_Z, new Vector3f());
        double time = minecraft.level.getGameTime()
                + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        if (context.firstPerson()) {
            if (minecraft.player == null || !isMainHandContext(context, minecraft.player.getMainArm())) {
                return;
            }
            // The hand pass runs under its own FOV, so only the on-screen direction of the
            // muzzle transfers to the world pass. Store screen x/y plus the muzzle's true
            // view-space distance, and let resolve() rebuild a world point from the pair.
            // Carrying the hand pass' NDC depth across projections is what used to push the
            // stream origin off the barrel.
            Vector4f clip = new Vector4f(rendered.x, rendered.y, rendered.z, 1.0F);
            new Matrix4f(RenderSystem.getModelViewMatrix()).transform(clip);
            new Matrix4f(RenderSystem.getProjectionMatrix()).transform(clip);
            if (Math.abs(clip.w) > 1.0E-8F) {
                firstPerson = new Sample(
                        new Vec3(clip.x / clip.w, clip.y / clip.w, rendered.length()), time);
            }
            return;
        }
        if (renderedPlayerId < 0
                || !(minecraft.level.getEntity(renderedPlayerId) instanceof Player player)
                || !isMainHandContext(context, player.getMainArm())
                || player.getMainHandItem().getItem() != ModBlocks.WATER_GUN.get()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        THIRD_PERSON.put(renderedPlayerId,
                new Sample(camera.add(rendered.x, rendered.y, rendered.z), time));
        if (THIRD_PERSON.size() > 128) {
            THIRD_PERSON.values().removeIf(sample -> time - sample.time() > MAX_SAMPLE_AGE_TICKS);
        }
    }

    static Vec3 resolve(Player player, float partialTick, Matrix4f modelView, Matrix4f projection) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        boolean localFirstPerson = player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()
                && minecraft.gameRenderer.getMainCamera().getEntity() == player
                && !FreecamCompat.isExternalCameraActive(minecraft);
        Sample sample = localFirstPerson ? firstPerson : THIRD_PERSON.get(player.getId());
        double time = minecraft.level.getGameTime() + partialTick;
        if (sample == null || time < sample.time() - 1.0D
                || time - sample.time() > MAX_SAMPLE_AGE_TICKS) {
            return null;
        }
        if (!localFirstPerson) {
            return sample.position();
        }
        // sample holds (ndcX, ndcY, viewDistance). Un-project the screen position through the
        // world matrices to get the camera ray that passes through the drawn muzzle, then step
        // along it by the muzzle's real view-space distance.
        Matrix4f inverseWorld = new Matrix4f(projection).mul(modelView);
        if (Math.abs(inverseWorld.determinant()) <= 1.0E-12F) {
            return null;
        }
        inverseWorld.invert();
        Vec3 near = unproject(inverseWorld, sample.position().x, sample.position().y, -1.0F);
        Vec3 far = unproject(inverseWorld, sample.position().x, sample.position().y, 1.0F);
        if (near == null || far == null) {
            return null;
        }
        Vec3 direction = far.subtract(near);
        if (direction.lengthSqr() <= 1.0E-10D) {
            return null;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        return camera.add(direction.normalize().scale(sample.position().z));
    }

    /** Un-project a normalised-device point through an inverted view-projection matrix. */
    private static Vec3 unproject(Matrix4f inverseViewProjection, double ndcX, double ndcY,
            float ndcZ) {
        Vector4f point = new Vector4f((float) ndcX, (float) ndcY, ndcZ, 1.0F);
        inverseViewProjection.transform(point);
        if (Math.abs(point.w) <= 1.0E-8F) {
            return null;
        }
        float inverseW = 1.0F / point.w;
        return new Vec3(point.x * inverseW, point.y * inverseW, point.z * inverseW);
    }

    static boolean isLocalMainHandContext(ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && context.firstPerson()
                && isMainHandContext(context, minecraft.player.getMainArm());
    }

    static void reset() {
        firstPerson = null;
        THIRD_PERSON.clear();
        renderedPlayerId = -1;
    }

    private static boolean isMainHandContext(ItemDisplayContext context, HumanoidArm mainArm) {
        return (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                ? mainArm == HumanoidArm.RIGHT
                : (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                        && mainArm == HumanoidArm.LEFT;
    }

    private record Sample(Vec3 position, double time) {
    }
}
