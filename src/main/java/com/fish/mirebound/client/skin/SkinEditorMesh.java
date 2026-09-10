package com.fish.mirebound.client.skin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;

/** Captures vanilla's actual baked skin quads, including slim arms and mirrored legacy limbs. */
public final class SkinEditorMesh {
    public record Vertex(double x, double y, double z, float u, float v) {}
    public record Face(String part, boolean outer, List<Vertex> vertices, float nx, float ny, float nz) {}
    public static final List<String> PARTS = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final String[] OUTER = {"hat", "jacket", "right_sleeve", "left_sleeve", "right_pants", "left_pants"};

    private SkinEditorMesh() {}

    public static List<Face> create(boolean slim, boolean legacy) {
        var mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim && !legacy);
        if (legacy) {
            mesh.getRoot().addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror()
                    .addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
            mesh.getRoot().addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror()
                    .addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(1.9F, 12, 0));
        }
        var root = mesh.getRoot().bake(64, legacy ? 32 : 64);
        List<Face> faces = new ArrayList<>();
        for (int i = 0; i < PARTS.size(); i++) {
            String name = PARTS.get(i);
            root.getChild(name).render(new PoseStack(), new Collector(name, false, faces), 0, 0);
            if (!legacy || i == 0)
                root.getChild(OUTER[i]).render(new PoseStack(), new Collector(name, true, faces), 0, 0);
        }
        return List.copyOf(faces);
    }

    private static final class Collector implements VertexConsumer {
        private final String part;
        private final boolean outer;
        private final List<Face> faces;
        private final List<Vertex> vertices = new ArrayList<>(4);
        private float x, y, z, u, v;
        Collector(String part, boolean outer, List<Face> faces) { this.part = part; this.outer = outer; this.faces = faces; }
        public VertexConsumer addVertex(float x, float y, float z) { this.x=x; this.y=y; this.z=z; return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { this.u=u; this.v=v; return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            vertices.add(new Vertex(x * 16D, y * 16D, z * 16D, u, v));
            if (vertices.size() == 4) { faces.add(new Face(part, outer, List.copyOf(vertices), nx, ny, nz)); vertices.clear(); }
            return this;
        }
    }
}
