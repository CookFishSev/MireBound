package com.fish.mirebound.assimilation;

import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Set;

/**
 * Compatibility facade for the assimilation template.
 *
 * <p>Assimilation is configured through the same world/local mud-profile pipeline as every other
 * behavior. Keeping this facade avoids coupling callers to the tuning store without maintaining a
 * second, conflicting TOML configuration.</p>
 */
public final class AssimilationConfig {
    private AssimilationConfig() {
    }

    public static AssimilationProfile profile() {
        return profileFor(SinkingMedium.ASSIMILATION_SLIME);
    }

    public static AssimilationBehaviorTemplate defaultTemplate() {
        return templateFor(SinkingMedium.ASSIMILATION_SLIME);
    }

    public static AssimilationBehaviorTemplate templateFor(SinkingMedium medium) {
        AssimilationProfile profile = profileFor(medium);
        return medium != null && profile.enabled()
                ? new AssimilationBehaviorTemplate(
                        AssimilationBehaviorTemplate.DEFAULT_ID, profile, Set.of(medium))
                : null;
    }

    public static AssimilationBehaviorTemplate template(String id) {
        return AssimilationBehaviorTemplate.DEFAULT_ID.equals(id) ? defaultTemplate() : null;
    }

    public static AssimilationProfile profileFor(SinkingMedium medium) {
        return MudPhysicsSettings.assimilationProfile(
                medium == null ? SinkingMedium.ASSIMILATION_SLIME : medium);
    }

    public static boolean appliesTo(SinkingMedium medium) {
        return medium != null && profileFor(medium).enabled();
    }
}
