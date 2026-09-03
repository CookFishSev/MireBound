package com.fish.mirebound.client.tuning;

import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudSinkingDepthControl;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.flow.MudBlockMotionMode;
import com.fish.mirebound.mud.tuning.MudTuningCapabilities;
import com.fish.mirebound.mud.tuning.MudTuningObjectId;
import com.fish.mirebound.network.payload.MudTuningSessionPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Client session state that survives authoritative refreshes without losing edits. */
public final class MudTuningSessionModel {
    private MudTuningScope scope;
    private boolean editable;
    private MudTuningAnchor first;
    private MudTuningAnchor second;
    private List<ObjectModel> objects;

    public MudTuningSessionModel(MudTuningSessionPayload payload) {
        accept(payload, false);
    }

    public void accept(MudTuningSessionPayload payload, boolean saving) {
        List<ObjectModel> previous = objects == null ? List.of() : objects;
        List<ObjectModel> refreshed = new ArrayList<>(payload.profiles().size());
        for (MudTuningSessionPayload.MediumProfile profile : payload.profiles()) {
            ObjectModel next = new ObjectModel(profile);
            if (!saving) {
                for (ObjectModel old : previous) {
                    if (old.id.equals(next.id)) {
                        next.retainPendingEdits(old);
                        break;
                    }
                }
            }
            refreshed.add(next);
        }
        scope = payload.scope();
        editable = payload.editable();
        first = payload.first();
        second = payload.second();
        objects = List.copyOf(refreshed);
    }

    public MudTuningScope scope() {
        return scope;
    }

    public boolean editable() {
        return editable;
    }

    public MudTuningAnchor first() {
        return first;
    }

    public MudTuningAnchor second() {
        return second;
    }

    public List<ObjectModel> objects() {
        return objects;
    }

    public ObjectModel find(MudTuningObjectId id) {
        for (ObjectModel object : objects) {
            if (object.id.equals(id)) {
                return object;
            }
        }
        return null;
    }

    public enum ObjectFilter {
        NATIVE,
        SOURCE,
        CONVERTED,
        INCOMPATIBLE;

        public boolean accepts(ObjectModel object) {
            return switch (this) {
                case NATIVE -> object.id.kind() == MudTuningObjectId.Kind.NATIVE_MEDIUM
                        || object.id.kind() == MudTuningObjectId.Kind.ADAPTIVE_DEFAULT;
                case SOURCE -> object.id.kind() == MudTuningObjectId.Kind.SOURCE_BLOCK;
                case CONVERTED -> object.id.kind() == MudTuningObjectId.Kind.CONVERTED_BLOCK;
                case INCOMPATIBLE ->
                        object.id.kind() == MudTuningObjectId.Kind.INCOMPATIBLE_BLOCK;
            };
        }

        public String translationKey() {
            return "gui.mirebound.tuning.object_filter."
                    + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final class ObjectModel {
        private final MudTuningObjectId id;
        private final int blockCount;
        private final boolean anyLocal;
        private final boolean allLocal;
        private final MudTuningCapabilities capabilities;
        private final int representativeStateId;
        private final double[] baseline;
        private final double[] serverValues;
        private final double[] values;
        private final boolean[] changed;
        private final int serverBlockVariant;
        private final int serverBlockHeight;
        private int blockVariant;
        private int blockHeight;
        private boolean shapeChanged;
        private BlockState representativeState;

        private ObjectModel(MudTuningSessionPayload.MediumProfile profile) {
            id = profile.objectId();
            blockCount = profile.blockCount();
            anyLocal = profile.anyLocal();
            allLocal = profile.allLocal();
            capabilities = new MudTuningCapabilities(profile.capabilities());
            representativeStateId = profile.representativeStateId();
            baseline = Arrays.copyOf(profile.resetValues(), MudPhysicsParameter.COUNT);
            values = Arrays.copyOf(profile.values(), MudPhysicsParameter.COUNT);
            serverValues = Arrays.copyOf(values, values.length);
            changed = new boolean[MudPhysicsParameter.COUNT];
            blockVariant = profile.blockVariant();
            blockHeight = profile.blockHeight();
            serverBlockVariant = blockVariant;
            serverBlockHeight = blockHeight;
        }

        public MudTuningObjectId id() {
            return id;
        }

        public int blockCount() {
            return blockCount;
        }

        public boolean anyLocal() {
            return anyLocal;
        }

        public boolean allLocal() {
            return allLocal;
        }

        public MudTuningCapabilities capabilities() {
            return capabilities;
        }

        public double[] values() {
            return values;
        }

        public double[] baseline() {
            return baseline;
        }

        public boolean[] changed() {
            return changed;
        }

        public int blockVariant() {
            return blockVariant;
        }

        public void setBlockVariant(int value) {
            blockVariant = value;
            shapeChanged = blockVariant != serverBlockVariant
                    || blockHeight != serverBlockHeight;
        }

        public int blockHeight() {
            return blockHeight;
        }

        public void setBlockHeight(int value) {
            blockHeight = Math.max(1, Math.min(16, value));
            blockVariant = (blockHeight == 16
                    ? MudBlockVariant.DEFAULT : MudBlockVariant.HEIGHT).ordinal();
            shapeChanged = blockVariant != serverBlockVariant
                    || blockHeight != serverBlockHeight;
        }

        public boolean blockVariantDiffersFromBaseline() {
            return blockVariant != MudBlockVariant.DEFAULT.ordinal();
        }

        public boolean blockHeightDiffersFromBaseline() {
            return blockHeight != 16;
        }

        public void resetBlockVariant() {
            setBlockVariant(MudBlockVariant.DEFAULT.ordinal());
        }

        public void resetBlockHeight() {
            setBlockHeight(16);
        }

        public boolean shapeChanged() {
            return shapeChanged;
        }

        public SinkingMedium nativeMedium() {
            return id.nativeMedium();
        }

        public boolean accepts(MudPhysicsParameter parameter) {
            if (id.kind() == MudTuningObjectId.Kind.TENTACLE) {
                return parameter.appliesToTentacle();
            }
            SinkingMedium medium = nativeMedium();
            return medium == null ? parameter.appliesToAdaptive() : parameter.appliesTo(medium);
        }

        public Component name() {
            BlockState representativeState = representativeState();
            Component base = representativeState.getBlock().getName();
            if (id.kind() == MudTuningObjectId.Kind.TENTACLE) {
                return Component.translatable("gui.mirebound.tuning.tentacle_system");
            }
            Component name = switch (id.kind()) {
                case NATIVE_MEDIUM, SOURCE_BLOCK, INCOMPATIBLE_BLOCK -> base;
                case CONVERTED_BLOCK -> Component.translatable(
                        "block.mirebound.adaptive_named", base);
                case ADAPTIVE_DEFAULT -> Component.translatable(
                        "gui.mirebound.tuning.adaptive_default");
                case TENTACLE -> Component.translatable(
                        "gui.mirebound.tuning.tentacle_system");
            };
            return blockCount > 0
                    ? Component.translatable("gui.mirebound.physics.tab_count", name, blockCount)
                    : name;
        }

        public ItemStack icon() {
            return new ItemStack(representativeState().getBlock().asItem());
        }

        private BlockState representativeState() {
            if (representativeState == null) {
                representativeState = Block.stateById(representativeStateId);
            }
            return representativeState;
        }

        public List<MudPhysicsParameter> parameters(MudTuningNavigation.Page page) {
            return Arrays.stream(MudPhysicsParameter.values())
                    .filter(this::accepts)
                    .filter(page::accepts)
                    .filter(parameter -> parameter
                            != MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE)
                    .filter(parameter -> parameter
                            != MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH)
                    .filter(parameter -> parameter
                            != MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH)
                    .filter(parameter -> parameter
                            != MudPhysicsParameter.HARVEST_OVERRIDE_SOURCE_ENABLED
                            || capabilities.has(MudTuningCapabilities.HARVEST_OVERRIDE))
                    .filter(parameter -> id.kind() != MudTuningObjectId.Kind.TENTACLE
                            || parameter != MudPhysicsParameter.TENTACLE_ENABLED)
                    .sorted(Comparator
                            .comparingInt(page::parameterPriority)
                            .thenComparingInt(MudPhysicsParameter::ordinal))
                    .toList();
        }

        public boolean differsFromBaseline(MudPhysicsParameter parameter) {
            int index = parameter.ordinal();
            return !parameter.displayEquivalent(values[index], baseline[index]);
        }

        public double maximumSinkingDepth() {
            return MudSinkingDepthControl.selectedMaximumDepth(values);
        }

        public double naturalSinkingDepth() {
            return MudSinkingDepthControl.simpleNaturalDepth(values);
        }

        public MudSinkingDepthControl.Mode depthControlMode() {
            return MudSinkingDepthControl.mode(
                    values[MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE.ordinal()]);
        }

        public void setDepthControlMode(MudSinkingDepthControl.Mode mode) {
            setRaw(MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE, mode.parameterValue());
        }

        public void resetDepthControlMode() {
            setDepthControlMode(MudSinkingDepthControl.mode(
                    baseline[MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE.ordinal()]));
        }

        public boolean depthControlModeDiffersFromBaseline() {
            return depthControlMode() != MudSinkingDepthControl.mode(
                    baseline[MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE.ordinal()]);
        }

        public boolean canEditDepthParameters() {
            return depthControlMode() == MudSinkingDepthControl.Mode.ADVANCED;
        }

        public void setMaximumSinkingDepth(double value) {
            if (depthControlMode() != MudSinkingDepthControl.Mode.SIMPLE) {
                return;
            }
            setRaw(MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH, value);
            if (naturalSinkingDepth() > maximumSinkingDepth()) {
                setRaw(MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH,
                        maximumSinkingDepth());
            }
        }

        public void setNaturalSinkingDepth(double value) {
            if (depthControlMode() != MudSinkingDepthControl.Mode.SIMPLE) {
                return;
            }
            setRaw(MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH,
                    Math.min(value, maximumSinkingDepth()));
        }

        public void resetMaximumSinkingDepth() {
            if (depthControlMode() != MudSinkingDepthControl.Mode.SIMPLE) {
                return;
            }
            setRaw(MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH,
                    baseline[MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH.ordinal()]);
            if (naturalSinkingDepth() > maximumSinkingDepth()) {
                setRaw(MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH,
                        maximumSinkingDepth());
            }
        }

        public void resetNaturalSinkingDepth() {
            if (depthControlMode() != MudSinkingDepthControl.Mode.SIMPLE) {
                return;
            }
            setNaturalSinkingDepth(
                    baseline[MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH.ordinal()]);
        }

        public boolean maximumSinkingDepthDiffersFromBaseline() {
            return !MudSinkingDepthControl.displayEquivalent(
                    maximumSinkingDepth(),
                    MudSinkingDepthControl.selectedMaximumDepth(baseline));
        }

        public boolean naturalSinkingDepthDiffersFromBaseline() {
            return !MudSinkingDepthControl.displayEquivalent(
                    naturalSinkingDepth(),
                    MudSinkingDepthControl.simpleNaturalDepth(baseline));
        }

        public void set(MudPhysicsParameter parameter, double value) {
            if (parameter == MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE) {
                setDepthControlMode(MudSinkingDepthControl.mode(parameter.sanitize(value)));
                return;
            }
            if ((parameter == MudPhysicsParameter.MAX_DEPTH_FACTOR
                    || parameter == MudPhysicsParameter.COLUMN_MARGIN)
                    && !canEditDepthParameters()) {
                return;
            }
            if (parameter == MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH) {
                if (!canEditDepthParameters()) {
                    setMaximumSinkingDepth(value);
                }
                return;
            }
            if (parameter == MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH) {
                if (!canEditDepthParameters()) {
                    setNaturalSinkingDepth(value);
                }
                return;
            }
            setRaw(parameter, value);
        }

        private void setRaw(MudPhysicsParameter parameter, double value) {
            int index = parameter.ordinal();
            values[index] = parameter.sanitize(value);
            boolean[] edited = new boolean[MudPhysicsParameter.COUNT];
            edited[index] = true;
            MudBlockMotionMode.enforceExclusive(values, edited);
            updateChanged(parameter);
            if (parameter == MudPhysicsParameter.FLOW_ENABLED
                    || parameter == MudPhysicsParameter.GRAVITY_FALLING_ENABLED) {
                updateChanged(MudPhysicsParameter.FLOW_ENABLED);
                updateChanged(MudPhysicsParameter.GRAVITY_FALLING_ENABLED);
            }
        }

        private void updateChanged(MudPhysicsParameter parameter) {
            int index = parameter.ordinal();
            changed[index] = !parameter.displayEquivalent(
                    values[index], serverValues[index]);
        }

        public void reset(MudPhysicsParameter parameter) {
            set(parameter, baseline[parameter.ordinal()]);
        }

        private void retainPendingEdits(ObjectModel old) {
            for (int index = 0; index < changed.length; index++) {
                if (old.changed[index]) {
                    setRaw(MudPhysicsParameter.values()[index], old.values[index]);
                }
            }
            if (old.shapeChanged) {
                blockVariant = old.blockVariant;
                blockHeight = old.blockHeight;
                shapeChanged = blockVariant != serverBlockVariant
                        || blockHeight != serverBlockHeight;
            }
        }
    }
}
