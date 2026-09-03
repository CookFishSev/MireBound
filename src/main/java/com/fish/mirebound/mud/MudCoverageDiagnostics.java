package com.fish.mirebound.mud;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Builds opt-in coverage diagnostics outside the exact pixel sampling owner. */
final class MudCoverageDiagnostics {
    private MudCoverageDiagnostics() {
    }

    static String sableColumn(ServerPlayer player, SableCoverageContext context) {
        Vec3 feetWorld = player.position();
        Vec3 eyeWorld = player.getEyePosition();
        Vec3 feetLocal = SableCompat.toLocal(context.subLevel(), feetWorld);
        Vec3 eyeLocal = SableCompat.toLocal(context.subLevel(), eyeWorld);
        StringBuilder builder = new StringBuilder(512);
        builder.append("contact=").append(context.contactMedium().serializedName())
                .append(" column=").append(blockPos(context.columnPos()))
                .append(" up=").append(context.localUp())
                .append(" yaw=").append(round3(player.getYRot()))
                .append(" bodyYaw=").append(round3(player.yBodyRot))
                .append(" headYaw=").append(round3(player.yHeadRot))
                .append(" surfaceY=").append(round3(context.surfaceY()))
                .append(" availableDepth=").append(round3(context.availableDepth()))
                .append(" feetWorld=").append(CoverageDebugLog.vec(feetWorld))
                .append(" feetLocal=").append(feetLocal == null ? "none" : CoverageDebugLog.vec(feetLocal))
                .append(" eyeWorld=").append(CoverageDebugLog.vec(eyeWorld))
                .append(" eyeLocal=").append(eyeLocal == null ? "none" : CoverageDebugLog.vec(eyeLocal))
                .append(" layers=[");
        SableLayer[] layers = context.layers();
        for (int i = 0; i < layers.length; i++) {
            if (i > 0) {
                builder.append(';');
            }
            SableLayer layer = layers[i];
            builder.append(i).append(':').append(layer.medium().serializedName())
                    .append(" top=").append(round3(layer.topCoordinate()))
                    .append(" bottom=").append(round3(layer.bottomCoordinate()));
        }
        return builder.append(']').toString();
    }

    static String serverState(ServerPlayer player, Level level, MudPlayerData data,
            SinkingMedium contactMedium, double surfaceY, double depth) {
        Vec3 feet = player.position();
        Vec3 eye = player.getEyePosition();
        StringBuilder builder = new StringBuilder(1024);
        builder.append("contactMedium=").append(contactMedium.serializedName())
                .append(" dataMedium=").append(data.medium.serializedName())
                .append(" surfaceY=").append(round3(surfaceY))
                .append(" depth=").append(round3(depth))
                .append(" coverage=").append(round3(data.coverage))
                .append(" eyeSubmerged=").append(data.eyeSubmerged)
                .append(" feet=").append(CoverageDebugLog.vec(feet))
                .append(" eye=").append(CoverageDebugLog.vec(eye))
                .append(" bb=[").append(round3(player.getBoundingBox().minX)).append(',')
                .append(round3(player.getBoundingBox().minY)).append(',')
                .append(round3(player.getBoundingBox().minZ)).append(" -> ")
                .append(round3(player.getBoundingBox().maxX)).append(',')
                .append(round3(player.getBoundingBox().maxY)).append(',')
                .append(round3(player.getBoundingBox().maxZ)).append(']')
                .append(" coverageCells=").append(coverageSummary(data));
        double height = Math.max(player.getBbHeight(), 0.1D);
        builder.append(" probes=[")
                .append(probe(level, player, "feet", feet.add(0.0D, 0.055D, 0.0D))).append("; ")
                .append(probe(level, player, "thigh", feet.add(0.0D, height * 0.36D, 0.0D))).append("; ")
                .append(probe(level, player, "waist", feet.add(0.0D, height * 0.54D, 0.0D))).append("; ")
                .append(probe(level, player, "chest", feet.add(0.0D, height * 0.74D, 0.0D))).append("; ")
                .append(probe(level, player, "eye", eye)).append(']');
        return builder.toString();
    }

    static String coverageSummary(MudPlayerData data) {
        float[] weights = new float[SinkingMedium.COUNT];
        float[] max = new float[SinkingMedium.COUNT];
        int[] counts = new int[SinkingMedium.COUNT];
        for (MudBodyPart part : MudBodyPart.values()) {
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        float coverage = data.surfacePixelCoverage(part, surface, row, column);
                        if (coverage <= 0.01F) {
                            continue;
                        }
                        int id = data.surfacePixelMedium(part, surface, row, column).id();
                        weights[id] += coverage;
                        max[id] = Math.max(max[id], coverage);
                        counts[id]++;
                    }
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        for (SinkingMedium medium : SinkingMedium.values()) {
            int id = medium.id();
            if (counts[id] <= 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(medium.serializedName())
                    .append("(cells=").append(counts[id])
                    .append(",sum=").append(round3(weights[id]))
                    .append(",max=").append(round3(max[id])).append(')');
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    static String blockPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static double round3(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private static String probe(Level level, Entity entity, String label, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        SinkingMedium worldMedium = ModBlocks.mediumOf(level.getBlockState(pos).getBlock());
        StringBuilder builder = new StringBuilder(256);
        builder.append(label).append("{world=").append(CoverageDebugLog.vec(point))
                .append(" block=").append(blockPos(pos)).append(':')
                .append(worldMedium == null ? "none" : worldMedium.serializedName());
        if (worldMedium != null) {
            BlockPos topPos = MudColumnResolver.findTop(level, pos);
            BlockPos bottomPos = MudColumnResolver.findBottom(level, topPos);
            BlockState topState = level.getBlockState(topPos);
            SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
            SinkingMedium surfaceMedium = topMedium == null ? worldMedium : topMedium;
            double worldSurfaceY = topPos.getY()
                    + MudMediumRuntime.surfaceHeightAt(
                            level, topPos, topState, surfaceMedium, point.x, point.z);
            builder.append(" top=").append(blockPos(topPos)).append(':').append(surfaceMedium.serializedName())
                    .append(" bottom=").append(blockPos(bottomPos))
                    .append(" depth=").append(round3(worldSurfaceY - point.y));
        }
        SinkingSample sample = SableCompat.sampleSinking(level, point, entity);
        if (sample == null) {
            return builder.append(" sable=none}").toString();
        }
        SinkingMedium topMedium = sample.topMedium() == null ? sample.medium() : sample.topMedium();
        double layerSurfaceLocalY = sample.pos().getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, sample.pos(), sample.state(), sample.medium(),
                        sample.localPoint().x, sample.localPoint().z);
        double topSurfaceLocalY = sample.topPos().getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, sample.topPos(), sample.topState(), topMedium,
                        sample.localPoint().x, sample.localPoint().z);
        builder.append(" sable={pos=").append(blockPos(sample.pos()))
                .append(" raw=").append(sample.medium().serializedName())
                .append(" final=").append(sample.medium().serializedName())
                .append(" local=").append(CoverageDebugLog.vec(sample.localPoint()))
                .append(" layerSurfaceY=").append(round3(layerSurfaceLocalY))
                .append(" top=").append(blockPos(sample.topPos())).append(':').append(topMedium.serializedName())
                .append(" bottom=").append(blockPos(sample.bottomPos()))
                .append(" layerDepth=").append(round3(layerSurfaceLocalY - sample.localPoint().y))
                .append(" topDepth=").append(round3(topSurfaceLocalY - sample.localPoint().y))
                .append(" aboveLayerWorldSurface=")
                .append(SableCompat.isWorldPointAboveLocalSurface(sample, layerSurfaceLocalY, 0.0D))
                .append(" aboveTopWorldSurface=")
                .append(SableCompat.isWorldPointAboveLocalSurface(sample, topSurfaceLocalY, 0.0D))
                .append("}}");
        return builder.toString();
    }
}
