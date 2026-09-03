package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Captures the rendered core center so world beams share the exact item pose. */
final class MudTuningWandCoreFocus {
    private static final float GRIP_X = 8.0F / 16.0F;
    private static final float GRIP_Y = 7.0F / 16.0F;
    private static final float GRIP_Z = 8.0F / 16.0F;
    private static final double MAX_SAMPLE_AGE_TICKS = 3.0D;
    private static final int SAMPLE_PRUNE_THRESHOLD = 128;
    private static final Map<Long, FocusSample> FIRST_PERSON = new HashMap<>();
    private static final Map<Long, FocusSample> THIRD_PERSON = new HashMap<>();

    private MudTuningWandCoreFocus() {
    }

    static void applyAim(PoseStack poseStack, ItemDisplayContext context,
            Vec3 target, float amount) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        if (context.firstPerson()) {
            // First-person item poses begin in inverse camera space. Cancel that
            // transform before solving the wand-local direction to the target.
            pose = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        }
        Vector3f pivot = pose.transformPosition(
                GRIP_X, GRIP_Y, GRIP_Z, new Vector3f());
        Vec3 cameraPosition = camera.getPosition();
        Vector3f targetInRenderSpace = new Vector3f(
                (float) (target.x - cameraPosition.x),
                (float) (target.y - cameraPosition.y),
                (float) (target.z - cameraPosition.z));
        if (context.firstPerson()) {
            camera.rotation().transformInverse(targetInRenderSpace);
        }
        Vector3f localDirection = targetInRenderSpace.sub(pivot);
        Matrix3f inversePose = new Matrix3f(pose);
        if (Math.abs(inversePose.determinant()) <= 1.0E-8F) {
            return;
        }
        inversePose.invert().transform(localDirection);
        if (localDirection.lengthSquared() <= 1.0E-8F) {
            return;
        }
        localDirection.normalize();
        Quaternionf targetRotation = new Quaternionf().rotationTo(
                0.0F, 1.0F, 0.0F,
                localDirection.x, localDirection.y, localDirection.z);
        Quaternionf rotation = new Quaternionf().slerp(
                targetRotation, Math.max(0.0F, Math.min(1.0F, amount)));
        poseStack.translate(GRIP_X, GRIP_Y, GRIP_Z);
        poseStack.mulPose(rotation);
        poseStack.translate(-GRIP_X, -GRIP_Y, -GRIP_Z);
    }

    static void capture(ItemStack stack, ItemDisplayContext context,
            PoseStack poseStack, float coreX, float coreY, float coreZ,
            float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MudTuningWandClientEffects.HeldContext held =
                MudTuningWandClientEffects.heldContext(stack, context);
        if (held == null) {
            return;
        }
        Vector3f rendered = poseStack.last().pose().transformPosition(
                coreX, coreY, coreZ, new Vector3f());
        double time = minecraft.level.getGameTime() + partialTick;
        pruneIfNeeded(time);
        long key = key(held.playerId(), held.mainHand());
        if (context.firstPerson()) {
            Vector4f clip = new Vector4f(rendered.x, rendered.y, rendered.z, 1.0F);
            new Matrix4f(RenderSystem.getModelViewMatrix()).transform(clip);
            new Matrix4f(RenderSystem.getProjectionMatrix()).transform(clip);
            if (Math.abs(clip.w) <= 1.0E-8F) {
                return;
            }
            FIRST_PERSON.put(key, new FocusSample(
                    new Vec3(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w),
                    true, time));
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        THIRD_PERSON.put(key, new FocusSample(
                camera.add(rendered.x, rendered.y, rendered.z), false, time));
    }

    static Vec3 resolve(Player player, boolean mainHand, float partialTick,
            Matrix4f modelView, Matrix4f projection) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        boolean localFirstPerson = player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()
                && minecraft.gameRenderer.getMainCamera().getEntity() == player
                && !FreecamCompat.isExternalCameraActive(minecraft);
        FocusSample sample = (localFirstPerson ? FIRST_PERSON : THIRD_PERSON)
                .get(key(player.getId(), mainHand));
        double time = minecraft.level.getGameTime() + partialTick;
        if (sample == null || time < sample.time() - 1.0D
                || time - sample.time() > MAX_SAMPLE_AGE_TICKS) {
            return null;
        }
        return localFirstPerson ? resolveFirstPerson(minecraft, sample, modelView, projection)
                : sample.position();
    }

    static void reset() {
        FIRST_PERSON.clear();
        THIRD_PERSON.clear();
    }

    private static Vec3 resolveFirstPerson(Minecraft minecraft,
            FocusSample sample, Matrix4f modelView, Matrix4f projection) {
        if (!sample.normalizedScreen()) {
            return null;
        }
        Matrix4f inverseWorld = new Matrix4f(projection).mul(modelView);
        if (Math.abs(inverseWorld.determinant()) <= 1.0E-12F) {
            return null;
        }
        inverseWorld.invert();
        Vector4f world = new Vector4f(
                (float) sample.position().x,
                (float) sample.position().y,
                (float) sample.position().z,
                1.0F);
        inverseWorld.transform(world);
        if (Math.abs(world.w) <= 1.0E-8F) {
            return null;
        }
        float inverseW = 1.0F / world.w;
        return minecraft.gameRenderer.getMainCamera().getPosition().add(
                world.x * inverseW, world.y * inverseW, world.z * inverseW);
    }

    private static long key(int playerId, boolean mainHand) {
        return ((long) playerId << 1) ^ (mainHand ? 1L : 0L);
    }

    private static void pruneIfNeeded(double time) {
        if (FIRST_PERSON.size() + THIRD_PERSON.size() <= SAMPLE_PRUNE_THRESHOLD) {
            return;
        }
        FIRST_PERSON.values().removeIf(sample -> time - sample.time() > MAX_SAMPLE_AGE_TICKS);
        THIRD_PERSON.values().removeIf(sample -> time - sample.time() > MAX_SAMPLE_AGE_TICKS);
    }

    private record FocusSample(Vec3 position, boolean normalizedScreen, double time) {
    }
}
