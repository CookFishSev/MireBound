package com.fish.mirebound.client;

import com.fish.mirebound.mud.AnimatedPlayerGeometry;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Converts final rendered quads into the compact body and cape geometry payloads. */
final class RenderedGeometryCollector {
    private static final double UV_EPSILON_PIXELS = 0.35D;
    private static final FaceSection[] BODY_SECTIONS = bodySections(false);
    private static final FaceSection[] SLIM_BODY_SECTIONS = bodySections(true);

    private RenderedGeometryCollector() {
    }

    static final class Body {
        private final LocalPlayer player;
        private final Vec3 cameraPosition;
        private final Vec3[][] faceCenters = new Vec3[MudBodyPart.COUNT][MudSurface.COUNT];
        private int matchedFaces;

        Body(LocalPlayer player, Vec3 cameraPosition) {
            this.player = player;
            this.cameraPosition = cameraPosition;
        }

        void sample(GeometryQuadAssembler.Quad quad) {
            Bounds bounds = uvBounds(quad, 64.0F, 64.0F);
            FaceSection section = exactSection(bounds,
                    player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM);
            if (section == null) {
                return;
            }
            Vec3 center = pointOnQuad(
                    (section.x + section.width * 0.5F) / 64.0F,
                    (section.y + section.height * 0.5F) / 64.0F,
                    quad, cameraPosition);
            if (center != null) {
                if (faceCenters[section.part.ordinal()][section.surface.ordinal()] == null) {
                    matchedFaces++;
                }
                faceCenters[section.part.ordinal()][section.surface.ordinal()] = center;
            }
        }

        AnimatedPlayerGeometry.PartPose[] finish() {
            AnimatedPlayerGeometry.PartPose[] poses =
                    new AnimatedPlayerGeometry.PartPose[MudBodyPart.COUNT];
            for (MudBodyPart part : MudBodyPart.values()) {
                Vec3[] faces = faceCenters[part.ordinal()];
                for (Vec3 face : faces) {
                    if (face == null) {
                        return null;
                    }
                }
                Vec3 halfSide = faces[MudSurface.LEFT.ordinal()]
                        .subtract(faces[MudSurface.RIGHT.ordinal()]).scale(0.5D);
                Vec3 halfUp = faces[MudSurface.TOP.ordinal()]
                        .subtract(faces[MudSurface.BOTTOM.ordinal()]).scale(0.5D);
                Vec3 halfForward = faces[MudSurface.FRONT.ordinal()]
                        .subtract(faces[MudSurface.BACK.ordinal()]).scale(0.5D);
                Vec3 center = opposingCenter(faces[MudSurface.LEFT.ordinal()],
                        faces[MudSurface.RIGHT.ordinal()])
                        .add(opposingCenter(faces[MudSurface.TOP.ordinal()],
                                faces[MudSurface.BOTTOM.ordinal()]))
                        .add(opposingCenter(faces[MudSurface.FRONT.ordinal()],
                                faces[MudSurface.BACK.ordinal()]))
                        .scale(1.0D / 3.0D);
                if (!AnimatedPlayerGeometryCapture.validPartPose(
                        part, halfSide, halfUp, halfForward)) {
                    return null;
                }
                poses[part.ordinal()] = new AnimatedPlayerGeometry.PartPose(
                        center, halfSide, halfUp, halfForward);
            }
            return poses;
        }

        String diagnostic() {
            StringBuilder missing = new StringBuilder();
            for (MudBodyPart part : MudBodyPart.values()) {
                int count = 0;
                for (Vec3 center : faceCenters[part.ordinal()]) {
                    if (center != null) {
                        count++;
                    }
                }
                if (count != MudSurface.COUNT) {
                    if (!missing.isEmpty()) {
                        missing.append(',');
                    }
                    missing.append(part.name().toLowerCase(java.util.Locale.ROOT))
                            .append('=').append(count).append('/').append(MudSurface.COUNT);
                }
            }
            return missing.isEmpty() ? "invalid_axes" : "missing[" + missing + ']';
        }

        int matchedFaces() {
            return matchedFaces;
        }
    }

    static final class Cape {
        private final LocalPlayer player;
        private final Vec3 cameraPosition;
        private final List<CapeFace> faces = new ArrayList<>(6);

        Cape(LocalPlayer player, Vec3 cameraPosition) {
            this.player = player;
            this.cameraPosition = cameraPosition;
        }

        void sample(GeometryQuadAssembler.Quad quad) {
            Bounds bounds = uvBounds(quad, 1.0F, 1.0F);
            Vec3 left = pointOnQuad(bounds.minU, (bounds.minV + bounds.maxV) * 0.5F,
                    quad, cameraPosition);
            Vec3 right = pointOnQuad(bounds.maxU, (bounds.minV + bounds.maxV) * 0.5F,
                    quad, cameraPosition);
            Vec3 top = pointOnQuad((bounds.minU + bounds.maxU) * 0.5F, bounds.minV,
                    quad, cameraPosition);
            Vec3 bottom = pointOnQuad((bounds.minU + bounds.maxU) * 0.5F, bounds.maxV,
                    quad, cameraPosition);
            if (left == null || right == null || top == null || bottom == null) {
                return;
            }
            Vec3 sideHalf = right.subtract(left).scale(0.5D);
            Vec3 downHalf = bottom.subtract(top).scale(0.5D);
            faces.add(new CapeFace(opposingCenter(left, right), sideHalf, downHalf,
                    sideHalf.cross(downHalf).length() * 4.0D));
        }

        AnimatedPlayerGeometry.CapePose finish() {
            if (faces.size() < 2) {
                return null;
            }
            faces.sort(Comparator.comparingDouble(CapeFace::area).reversed());
            CapeFace first = faces.get(0);
            CapeFace second = faces.get(1);
            if (second.area < first.area * 0.72D) {
                return null;
            }
            AnimatedPlayerGeometry.PartPose body =
                    AnimatedPlayerGeometry.part(player, MudBodyPart.BODY);
            if (body == null) {
                return null;
            }
            Vec3 sideHalf = first.sideHalf;
            if (sideHalf.dot(body.side()) < 0.0D) {
                sideHalf = sideHalf.scale(-1.0D);
            }
            Vec3 downHalf = first.downHalf;
            if (downHalf.dot(body.up().scale(-1.0D)) < 0.0D) {
                downHalf = downHalf.scale(-1.0D);
            }
            double scale = (sideHalf.length() / 5.0D + downHalf.length() / 8.0D) * 0.5D;
            if (scale < 0.025D || scale > 0.12D) {
                return null;
            }
            Vec3 center = opposingCenter(first.center, second.center);
            Vec3 expectedBack = body.forward().scale(-1.0D);
            Vec3 normal = first.center.subtract(second.center);
            if (normal.dot(expectedBack) < 0.0D) {
                normal = normal.scale(-1.0D);
            }
            if (normal.lengthSqr() < 1.0E-8D) {
                normal = expectedBack;
            }
            return new AnimatedPlayerGeometry.CapePose(
                    center.subtract(downHalf), sideHalf.normalize(), downHalf.normalize(),
                    normal.normalize(), scale);
        }
    }

    private static FaceSection exactSection(Bounds bounds, boolean slim) {
        for (FaceSection section : slim ? SLIM_BODY_SECTIONS : BODY_SECTIONS) {
            if (close(bounds.minU, section.x)
                    && close(bounds.maxU, section.x + section.width)
                    && close(bounds.minV, section.y)
                    && close(bounds.maxV, section.y + section.height)) {
                return section;
            }
        }
        return null;
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= UV_EPSILON_PIXELS;
    }

    private static Bounds uvBounds(GeometryQuadAssembler.Quad quad,
            float width, float height) {
        return new Bounds(quad.minU(width), quad.maxU(width),
                quad.minV(height), quad.maxV(height));
    }

    private static Vec3 pointOnQuad(float u, float v,
            GeometryQuadAssembler.Quad quad, Vec3 cameraPosition) {
        return quad.interpolate(u, v, cameraPosition);
    }

    private static Vec3 opposingCenter(Vec3 first, Vec3 second) {
        return first.add(second).scale(0.5D);
    }

    private static FaceSection[] bodySections(boolean slim) {
        List<FaceSection> sections = new ArrayList<>(MudBodyPart.COUNT * MudSurface.COUNT);
        addCube(sections, MudBodyPart.HEAD, 0, 0, 8, 8, 8);
        addCube(sections, MudBodyPart.RIGHT_LEG, 0, 16, 4, 12, 4);
        addCube(sections, MudBodyPart.BODY, 16, 16, 8, 12, 4);
        int armWidth = slim ? 3 : 4;
        addCube(sections, MudBodyPart.RIGHT_ARM, 40, 16, armWidth, 12, 4);
        addCube(sections, MudBodyPart.LEFT_LEG, 16, 48, 4, 12, 4);
        addCube(sections, MudBodyPart.LEFT_ARM, 32, 48, armWidth, 12, 4);
        return sections.toArray(FaceSection[]::new);
    }

    private static void addCube(List<FaceSection> sections, MudBodyPart part,
            int x, int y, int width, int height, int depth) {
        sections.add(new FaceSection(part, MudSurface.TOP, x + depth, y, width, depth));
        sections.add(new FaceSection(part, MudSurface.BOTTOM, x + depth + width, y, width, depth));
        sections.add(new FaceSection(part, MudSurface.RIGHT, x, y + depth, depth, height));
        sections.add(new FaceSection(part, MudSurface.FRONT, x + depth, y + depth, width, height));
        sections.add(new FaceSection(part, MudSurface.LEFT,
                x + depth + width, y + depth, depth, height));
        sections.add(new FaceSection(part, MudSurface.BACK,
                x + depth + width + depth, y + depth, width, height));
    }

    private record FaceSection(MudBodyPart part, MudSurface surface,
            int x, int y, int width, int height) {
    }

    private record Bounds(float minU, float maxU, float minV, float maxV) {
    }

    private record CapeFace(Vec3 center, Vec3 sideHalf, Vec3 downHalf, double area) {
    }
}
