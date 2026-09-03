package com.fish.mirebound.client.tuning;

import com.fish.mirebound.mud.MudPhysicsParameter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Semantic navigation metadata independent from persisted parameter ordinals. */
public final class MudTuningNavigation {
    private MudTuningNavigation() {
    }

    public static List<Page> pages(Group group) {
        return Arrays.stream(Page.values()).filter(page -> page.group == group).toList();
    }

    public enum Group {
        BASIC,
        PHYSICS,
        COVERAGE,
        SURFACE_ADHESION,
        SPECIAL,
        LIVING_SLIME,
        TENTACLE,
        ASSIMILATION,
        ERUPTION;

        public String translationKey() {
            return "gui.mirebound.tuning.group." + name().toLowerCase(Locale.ROOT);
        }

        public String compactLabel() {
            return switch (this) {
                case BASIC -> "B";
                case PHYSICS -> "P";
                case COVERAGE -> "C";
                case SURFACE_ADHESION -> "S";
                case SPECIAL -> "X";
                case LIVING_SLIME -> "L";
                case TENTACLE -> "T";
                case ASSIMILATION -> "A";
                case ERUPTION -> "E";
            };
        }
    }

    public enum Page {
        BASIC_SHAPE(Group.BASIC, MudPhysicsParameter.Category.SHAPE, Filter.NON_FLOW),
        BASIC_HARVEST(Group.BASIC, MudPhysicsParameter.Category.HARVEST, Filter.ALL),
        BASIC_ITEMS(Group.BASIC, MudPhysicsParameter.Category.DROPPED_ITEMS, Filter.ALL),
        BASIC_FLOW(Group.BASIC, MudPhysicsParameter.Category.SHAPE, Filter.FLOW),

        PHYSICS_DEPTH(Group.PHYSICS, MudPhysicsParameter.Category.SINKING, Filter.ALL),
        PHYSICS_MOVEMENT(Group.PHYSICS, MudPhysicsParameter.Category.MOVEMENT, Filter.ALL),
        PHYSICS_DISTURBANCE(Group.PHYSICS, MudPhysicsParameter.Category.DISTURBANCE, Filter.ALL),
        PHYSICS_RHEOLOGY(Group.PHYSICS, MudPhysicsParameter.Category.RHEOLOGY, Filter.ALL),

        COVERAGE_CORE(Group.COVERAGE, MudPhysicsParameter.Category.COVERAGE, Filter.ALL),
        SURFACE_EFFECTS(Group.SURFACE_ADHESION,
                MudPhysicsParameter.Category.SURFACE_EFFECTS, Filter.ALL),
        ADHESION(Group.SURFACE_ADHESION,
                MudPhysicsParameter.Category.ADHESION_STRANDS, Filter.ALL),

        SPECIAL_COMPONENTS(Group.SPECIAL,
                MudPhysicsParameter.Category.BEHAVIOR_COMPONENTS, Filter.ALL),
        SPECIAL_SWARM(Group.SPECIAL, MudPhysicsParameter.Category.SWARM, Filter.ALL),
        SPECIAL_SCULK(Group.SPECIAL, MudPhysicsParameter.Category.SCULK, Filter.ALL),
        SPECIAL_FLESH(Group.SPECIAL, MudPhysicsParameter.Category.TENDER_FLESH, Filter.ALL),

        SLIME_DEPTH(Group.LIVING_SLIME, MudPhysicsParameter.Category.SLIME_DEPTH, Filter.ALL),
        SLIME_BOUNCE(Group.LIVING_SLIME, MudPhysicsParameter.Category.SLIME_BOUNCE, Filter.ALL),
        SLIME_ANCHOR(Group.LIVING_SLIME, MudPhysicsParameter.Category.SLIME_ANCHOR, Filter.ALL),
        SLIME_STRUGGLE(Group.LIVING_SLIME, MudPhysicsParameter.Category.SLIME_STRUGGLE, Filter.ALL),

        TENTACLE_CORE(Group.TENTACLE, MudPhysicsParameter.Category.TENTACLE, Filter.ALL),
        TENTACLE_LIFECYCLE(Group.TENTACLE,
                MudPhysicsParameter.Category.TENTACLE_LIFECYCLE, Filter.ALL),
        TENTACLE_DYNAMICS(Group.TENTACLE,
                MudPhysicsParameter.Category.TENTACLE_DYNAMICS, Filter.ALL),
        TENTACLE_PATH(Group.TENTACLE, MudPhysicsParameter.Category.TENTACLE_PATH, Filter.ALL),
        TENTACLE_GRAB(Group.TENTACLE, MudPhysicsParameter.Category.TENTACLE_GRAB, Filter.ALL),

        ASSIMILATION_CORE(Group.ASSIMILATION, MudPhysicsParameter.Category.ASSIMILATION,
                Filter.ASSIMILATION_CORE),
        ASSIMILATION_SOUL(Group.ASSIMILATION, MudPhysicsParameter.Category.ASSIMILATION,
                Filter.ASSIMILATION_SOUL),
        ASSIMILATION_RESCUE(Group.ASSIMILATION, MudPhysicsParameter.Category.ASSIMILATION,
                Filter.ASSIMILATION_RESCUE),
        ASSIMILATION_PURGE(Group.ASSIMILATION, MudPhysicsParameter.Category.ASSIMILATION,
                Filter.ASSIMILATION_PURGE),
        ASSIMILATION_CRACKS(Group.ASSIMILATION, MudPhysicsParameter.Category.ASSIMILATION,
                Filter.ASSIMILATION_CRACKS),

        ERUPTION_SPAWNING(Group.ERUPTION, MudPhysicsParameter.Category.ERUPTION_VENTS,
                Filter.ERUPTION_SPAWNING),
        ERUPTION_CONTINUOUS(Group.ERUPTION, MudPhysicsParameter.Category.ERUPTION_VENTS,
                Filter.ERUPTION_CONTINUOUS),
        ERUPTION_SURGES(Group.ERUPTION, MudPhysicsParameter.Category.ERUPTION_VENTS,
                Filter.ERUPTION_SURGES);

        private final Group group;
        private final MudPhysicsParameter.Category category;
        private final Filter filter;

        Page(Group group, MudPhysicsParameter.Category category, Filter filter) {
            this.group = group;
            this.category = category;
            this.filter = filter;
        }

        public Group group() {
            return group;
        }

        public boolean accepts(MudPhysicsParameter parameter) {
            return category != null && parameter.category() == category && filter.accepts(parameter);
        }

        public boolean finiteFlow() {
            return this == BASIC_FLOW;
        }

        public int parameterPriority(MudPhysicsParameter parameter) {
            if (this == BASIC_FLOW) {
                if (parameter == MudPhysicsParameter.GRAVITY_FALLING_ENABLED) {
                    return 0;
                }
                return parameter == MudPhysicsParameter.FLOW_ENABLED ? 1 : 2;
            }
            if (this == BASIC_HARVEST
                    && parameter == MudPhysicsParameter.HARVEST_OVERRIDE_SOURCE_ENABLED) {
                return 0;
            }
            if (this == PHYSICS_MOVEMENT
                    && parameter == MudPhysicsParameter.STEP_HEIGHT) {
                return 0;
            }
            return parameter == primaryToggle() ? 0 : 1;
        }

        private MudPhysicsParameter primaryToggle() {
            return switch (this) {
                case COVERAGE_CORE -> MudPhysicsParameter.COVERAGE_ENABLED;
                case SURFACE_EFFECTS -> MudPhysicsParameter.SURFACE_EFFECTS_ENABLED;
                case ADHESION -> MudPhysicsParameter.ADHESION_STRANDS_ENABLED;
                case SPECIAL_SWARM -> MudPhysicsParameter.SWARM_ENABLED;
                case SPECIAL_SCULK -> MudPhysicsParameter.SCULK_ENABLED;
                case SPECIAL_FLESH -> MudPhysicsParameter.FLESH_ENABLED;
                case TENTACLE_CORE -> MudPhysicsParameter.TENTACLE_ENABLED;
                case ASSIMILATION_CORE -> MudPhysicsParameter.ASSIMILATION_ENABLED;
                case ERUPTION_SPAWNING -> MudPhysicsParameter.ERUPTION_ENABLED;
                case ERUPTION_CONTINUOUS -> MudPhysicsParameter.ERUPTION_CONTINUOUS_ENABLED;
                case ERUPTION_SURGES -> MudPhysicsParameter.ERUPTION_SURGES_ENABLED;
                default -> null;
            };
        }

        public String translationKey() {
            return "gui.mirebound.tuning.page." + name().toLowerCase(Locale.ROOT);
        }
    }

    private enum Filter {
        ALL,
        FLOW,
        NON_FLOW,
        ASSIMILATION_CORE,
        ASSIMILATION_SOUL,
        ASSIMILATION_RESCUE,
        ASSIMILATION_PURGE,
        ASSIMILATION_CRACKS,
        ERUPTION_SPAWNING,
        ERUPTION_CONTINUOUS,
        ERUPTION_SURGES;

        private boolean accepts(MudPhysicsParameter parameter) {
            return switch (this) {
                case ALL -> true;
                case FLOW -> isBlockMotion(parameter);
                case NON_FLOW -> !isBlockMotion(parameter)
                        && parameter != MudPhysicsParameter.BEHAVIOR_PROFILE;
                case ASSIMILATION_CORE -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ASSIMILATION_CORE;
                case ASSIMILATION_SOUL -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ASSIMILATION_SOUL;
                case ASSIMILATION_RESCUE -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ASSIMILATION_RESCUE;
                case ASSIMILATION_PURGE -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ASSIMILATION_PURGE;
                case ASSIMILATION_CRACKS -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ASSIMILATION_CRACKS;
                case ERUPTION_SPAWNING -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ERUPTION_SPAWNING
                        && parameter != MudPhysicsParameter.ERUPTION_MAX_ACTIVE;
                case ERUPTION_CONTINUOUS -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ERUPTION_CONTINUOUS;
                case ERUPTION_SURGES -> parameter.subcategory()
                        == MudPhysicsParameter.Subcategory.ERUPTION_SURGES;
            };
        }

        private static boolean isBlockMotion(MudPhysicsParameter parameter) {
            return parameter.name().startsWith("FLOW_")
                    || parameter == MudPhysicsParameter.GRAVITY_FALLING_ENABLED;
        }
    }
}
