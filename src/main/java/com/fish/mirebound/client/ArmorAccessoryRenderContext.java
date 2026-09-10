package com.fish.mirebound.client;

import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.client.config.MireboundClientSettings;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.fish.mirebound.client.coverage.EquipmentSurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Tracks the actual equipment/material while a custom layer draws independent model roots. */
public final class ArmorAccessoryRenderContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_EQUIPMENT_CAPTURE = new ThreadLocal<>();
    private static final Map<ProjectionKey, ModelPart> CLASSIC_PROJECTIONS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ProjectionKey, ModelPart> eldest) {
            return size() > 512;
        }
    };

    private ArmorAccessoryRenderContext() {
    }

    public static void begin(LivingEntity entity, RenderLayer<?, ?> layer) {
        if (layer instanceof HumanoidArmorLayer<?, ?, ?>
                || layer instanceof MudSkinLayer || layer instanceof ArmorMudLayer) {
            CURRENT.remove();
            return;
        }
        String layerName = layer.getClass().getName();
        EquipmentSlot strongSlot = inferStrongSlot(entity, layerName, null);
        EquipmentSlot slot = strongSlot != null ? strongSlot : inferSlot(entity, layerName, null);
        CURRENT.set(new State(entity, layerName, slot, strongSlot != null));
    }

    public static void begin(LivingEntity entity, RenderLayer<?, ?> layer, MultiBufferSource buffers) {
        begin(entity, layer);
        State state = CURRENT.get();
        if (state != null && MireboundClientSettings.independentSurfaceCoverage())
            state.buffers = new com.fish.mirebound.client.coverage.SurfaceDrawQueue(buffers);
    }

    public static EquipmentSurfaceTarget surfaceTarget(ItemStack stack) {
        State state = CURRENT.get();
        if (state != null && state.curio != null && state.curio.stack == stack)
            return new EquipmentSurfaceTarget(1, state.curio.index, state.curio.identifier, state.curio.cosmetic);
        return null;
    }

    public static MultiBufferSource surfaceBaseBuffers(MultiBufferSource fallback) {
        State state = CURRENT.get();
        return state == null || state.buffers == null ? fallback : state.buffers.baseBuffers();
    }

    public static void enterSurfacePart(ModelPart part) {
        State state = CURRENT.get();
        if (state == null) return;
        if (!MireboundClientSettings.independentSurfaceCoverage()) {
            if (state.requestedTexture != null && (state.slot != null || state.curio != null)) {
                ProjectionKey key = projectionKey(state);
                if (!CLASSIC_PROJECTIONS.containsKey(key)
                        && part.getAllParts().anyMatch(child -> !child.isEmpty())) {
                    CLASSIC_PROJECTIONS.put(key, part);
                }
            }
            return;
        }
        state.partDepth++;
    }

    public static void exitSurfacePart(ModelPart part, PoseStack pose, int light, int overlay, int color) {
        if (!MireboundClientSettings.independentSurfaceCoverage()) return;
        State state = CURRENT.get();
        if (state == null || --state.partDepth != 0 || state.buffers == null || state.requestedTexture == null) return;
        CaptureTarget capture = captureTarget();
        if (capture == null) return;
        EquipmentSurfaceTarget target = capture.curio()
                ? new EquipmentSurfaceTarget(1, capture.curiosIndex(), capture.curiosIdentifier(), capture.curiosCosmetic())
                : EquipmentSurfaceTarget.armor(capture.armorSlot());
        int root = state.roots.computeIfAbsent(part, ignored -> state.roots.size());
        EquipmentSurfaceRenderer.modelPart(state.entity, capture.stack(), target, state.layerName + "/root/" + root,
                part, state.requestedTexture, pose, state.buffers, light, overlay, color, state.armorViewOffset);
    }

    public static void end() {
        State state = CURRENT.get();
        CURRENT.remove();
        if (state != null && state.buffers != null) state.buffers.flush();
    }

    public static void beginCurio(LivingEntity entity, ItemStack stack, String identifier,
            int index, boolean cosmetic, MultiBufferSource buffers) {
        State state = CURRENT.get();
        if (state == null || state.entity != entity) {
            state = new State(entity, "curios", null, false);
            state.curioOwnedState = true;
            CURRENT.set(state);
        }
        state.curio = new CurioTarget(stack, identifier, index, cosmetic);
        state.roots.clear();
        if (state.buffers == null && MireboundClientSettings.independentSurfaceCoverage())
            state.buffers = new com.fish.mirebound.client.coverage.SurfaceDrawQueue(buffers);
        state.requestedTexture = null;
    }

    public static void endCurio() {
        State state = CURRENT.get();
        if (state == null) {
            return;
        }
        state.curio = null;
        state.requestedTexture = null;
        if (state.curioOwnedState) {
            end();
        }
    }

    public static void recordEquipmentSlot(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
        State state = CURRENT.get();
        if (!Boolean.TRUE.equals(SUPPRESS_EQUIPMENT_CAPTURE.get())
                && state != null && state.entity == entity
                // A custom head/back/feet renderer can use a normal equipment slot without
                // its ItemStack being an ArmorItem (for example a cosmetic skull).
                && isWearableSlot(slot) && !stack.isEmpty()) {
            state.slot = slot;
            state.strongSlotEvidence = true;
        }
    }

    public static void suppressEquipmentCapture(boolean suppress) {
        if (suppress) {
            SUPPRESS_EQUIPMENT_CAPTURE.set(true);
        } else {
            SUPPRESS_EQUIPMENT_CAPTURE.remove();
        }
    }

    public static ResourceLocation armorTexture(ResourceLocation requestedTexture) {
        return equipmentTexture(requestedTexture, true);
    }

    private static ResourceLocation equipmentTexture(ResourceLocation requestedTexture, boolean armorViewOffset) {
        if ("minecraft".equals(requestedTexture.getNamespace())
                && "textures/misc/white.png".equals(requestedTexture.getPath())) return requestedTexture;
        State state = CURRENT.get();
        if (state != null) {
            state.armorViewOffset = armorViewOffset;
            state.requestedTexture = requestedTexture;
            if (state.slot == null && state.curio == null)
                state.slot = inferSlot(state.entity, state.layerName, requestedTexture);
            if (!MireboundClientSettings.independentSurfaceCoverage()
                    && (state.slot != null || state.curio != null)) {
                ModelPart projection = CLASSIC_PROJECTIONS.get(projectionKey(state));
                if (state.curio != null) {
                    return ClassicArmorMudRenderer.accessoryTextureFor(state.entity, state.curio.stack,
                            state.curio.key(), MudBodyPart.BODY, projection, requestedTexture);
                }
                MudBodyPart part = switch (state.slot) {
                    case HEAD -> MudBodyPart.HEAD;
                    case LEGS, FEET -> MudBodyPart.LEFT_LEG;
                    default -> MudBodyPart.BODY;
                };
                return ClassicArmorMudRenderer.accessoryTextureFor(state.entity, state.slot,
                        part, projection, requestedTexture);
            }
        }
        // A shared texture cannot own distinct physical contact cells.
        return requestedTexture;
    }

    public static ResourceLocation genericEquipmentTexture(ResourceLocation requestedTexture) {
        return genericEquipmentTexture(requestedTexture, false);
    }

    public static ResourceLocation genericEquipmentTexture(ResourceLocation requestedTexture, boolean armorViewOffset) {
        State state = CURRENT.get();
        if (state == null) {
            return requestedTexture;
        }
        if (state.curio != null) {
            return equipmentTexture(requestedTexture, armorViewOffset);
        }
        EquipmentSlot strongSlot = inferStrongSlot(state.entity, state.layerName, requestedTexture);
        if (strongSlot != null) {
            state.slot = strongSlot;
            state.strongSlotEvidence = true;
        }
        return state.strongSlotEvidence ? equipmentTexture(requestedTexture, armorViewOffset) : requestedTexture;
    }

    public static CaptureTarget captureTarget() {
        State state = CURRENT.get();
        if (state == null || state.requestedTexture == null) {
            return null;
        }
        if (state.curio != null) {
            return new CaptureTarget(state.entity, state.curio.stack, null,
                    state.curio.identifier, state.curio.index, state.curio.cosmetic,
                    state.curio.key(), state.requestedTexture);
        }
        if (state.slot == null) {
            return null;
        }
        return new CaptureTarget(state.entity, state.entity.getItemBySlot(state.slot), state.slot,
                "", -1, false, armorKey(state.slot), state.requestedTexture);
    }


    static void reset() {
        CURRENT.remove();
        SUPPRESS_EQUIPMENT_CAPTURE.remove();
        CLASSIC_PROJECTIONS.clear();
    }

    private static EquipmentSlot inferSlot(LivingEntity entity, String layerName, ResourceLocation texture) {
        EquipmentSlot strong = inferStrongSlot(entity, layerName, texture);
        if (strong != null) {
            return strong;
        }
        EquipmentSlot only = null;
        for (EquipmentSlot candidate : ArmorMudManager.armorSlots()) {
            if (!equippedArmor(entity, candidate)) {
                continue;
            }
            if (only != null) {
                return null;
            }
            only = candidate;
        }
        return only;
    }

    private static EquipmentSlot inferStrongSlot(LivingEntity entity, String layerName, ResourceLocation texture) {
        String probe = (layerName + " " + (texture == null ? "" : texture.toString())).toLowerCase(Locale.ROOT);
        if (containsAny(probe, "helmet", "head", "hat", "horn", "crown", "mask")) {
            return equippedArmor(entity, EquipmentSlot.HEAD) ? EquipmentSlot.HEAD : null;
        }
        if (containsAny(probe, "boot", "feet", "foot", "shoe")) {
            return equippedArmor(entity, EquipmentSlot.FEET) ? EquipmentSlot.FEET : null;
        }
        if (containsAny(probe, "legging", "pants", "trouser", " leg")) {
            return equippedArmor(entity, EquipmentSlot.LEGS) ? EquipmentSlot.LEGS : null;
        }
        if (containsAny(probe, "chest", "body", "wing")) {
            return equippedArmor(entity, EquipmentSlot.CHEST) ? EquipmentSlot.CHEST : null;
        }

        return itemSemanticSlot(entity, probe);
    }

    private static EquipmentSlot itemSemanticSlot(LivingEntity entity, String probe) {
        EquipmentSlot best = null;
        int bestScore = 0;
        boolean tied = false;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            if (!equippedArmor(entity, slot)) {
                continue;
            }
            String path = BuiltInRegistries.ITEM.getKey(entity.getItemBySlot(slot).getItem()).getPath();
            int score = 0;
            for (String token : path.split("[_/.-]+")) {
                if (token.length() >= 4 && probe.contains(token)) {
                    score += token.length();
                }
            }
            if (score > bestScore) {
                best = slot;
                bestScore = score;
                tied = false;
            } else if (score > 0 && score == bestScore) {
                tied = true;
            }
        }
        return bestScore > 0 && !tied ? best : null;
    }

    private static boolean equippedArmor(LivingEntity entity, EquipmentSlot slot) {
        return ArmorMudManager.validArmor(entity.getItemBySlot(slot), slot);
    }

    private static boolean isWearableSlot(EquipmentSlot slot) {
        for (EquipmentSlot candidate : ArmorMudManager.armorSlots()) {
            if (candidate == slot) {
                return true;
            }
        }
        return false;
    }

    private static String armorKey(EquipmentSlot slot) {
        return "armor:" + slot.getName();
    }

    private static ProjectionKey projectionKey(State state) {
        return new ProjectionKey(state.layerName, state.requestedTexture,
                state.curio == null ? armorKey(state.slot) : state.curio.key());
    }

    private record ProjectionKey(String layerName, ResourceLocation texture, String target) {}


    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }


    public record CaptureTarget(LivingEntity entity, ItemStack stack, EquipmentSlot armorSlot,
            String curiosIdentifier, int curiosIndex, boolean curiosCosmetic,
            String key, ResourceLocation texture) {
        public boolean curio() {
            return armorSlot == null;
        }
    }

    private static final class State {
        private final Map<ModelPart, Integer> roots = new IdentityHashMap<>();
        private com.fish.mirebound.client.coverage.SurfaceDrawQueue buffers;
        private int partDepth;
        private boolean armorViewOffset;
        private final LivingEntity entity;
        private final String layerName;
        private EquipmentSlot slot;
        private ResourceLocation requestedTexture;
        private boolean strongSlotEvidence;
        private CurioTarget curio;
        private boolean curioOwnedState;

        private State(LivingEntity entity, String layerName, EquipmentSlot slot, boolean strongSlotEvidence) {
            this.entity = entity;
            this.layerName = layerName;
            this.slot = slot;
            this.strongSlotEvidence = strongSlotEvidence;
        }
    }

    private record CurioTarget(ItemStack stack, String identifier, int index, boolean cosmetic) {
        private String key() {
            return "curios:" + identifier + ':' + index + ':' + cosmetic;
        }
    }
}
