package com.fish.mirebound.generation.natural;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile.Rule;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Per-world persisted natural-generation choices with a server-scoped hot cache. */
public final class NaturalMudGenerationSettings extends SavedData {
    private static final String DATA_NAME = "mirebound_natural_mud_generation";
    private static final int DATA_VERSION = 6;
    private static final int MAX_PERSISTED_RULES = SinkingMedium.COUNT;
    private static final int MAX_BIOME_SELECTORS_PER_RULE = 4096;
    private static final int MAX_SELECTOR_LENGTH = 256;
    private static final int MAX_FORMS_PER_RULE = NaturalMudDepositForm.values().length;
    private static final Factory<NaturalMudGenerationSettings> FACTORY =
            new Factory<>(NaturalMudGenerationSettings::create,
                    NaturalMudGenerationSettings::load);
    private static final Map<MinecraftServer, NaturalMudGenerationProfile> ACTIVE =
            new ConcurrentHashMap<>();

    private NaturalMudGenerationProfile profile;

    private NaturalMudGenerationSettings(NaturalMudGenerationProfile profile) {
        this.profile = profile;
    }

    private static NaturalMudGenerationSettings create() {
        NaturalMudGenerationSettings settings = new NaturalMudGenerationSettings(
                NaturalMudWorldCreationBridge.consumeOrDefault());
        settings.setDirty();
        return settings;
    }

    public static NaturalMudGenerationSettings get(ServerLevel level) {
        ServerLevel owner = level.getServer().overworld();
        if (owner == null) {
            owner = level;
        }
        return owner.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static NaturalMudGenerationProfile active(ServerLevel level) {
        MinecraftServer server = level.getServer();
        NaturalMudGenerationProfile cached = ACTIVE.get(server);
        if (cached != null) {
            return cached;
        }
        NaturalMudGenerationProfile loaded = get(level).profile;
        NaturalMudGenerationProfile raced = ACTIVE.putIfAbsent(server, loaded);
        return raced == null ? loaded : raced;
    }

    public NaturalMudGenerationProfile profile() {
        return profile;
    }

    public void update(NaturalMudGenerationProfile replacement) {
        profile = replacement;
        setDirty();
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD) {
            ACTIVE.put(level.getServer(), get(level).profile);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.remove(event.getServer());
        NaturalMudWorldCreationBridge.clear();
    }

    private static NaturalMudGenerationSettings load(
            CompoundTag tag, HolderLookup.Provider registries) {
        NaturalMudGenerationProfile profile = loadProfile(tag);
        NaturalMudGenerationSettings settings =
                new NaturalMudGenerationSettings(profile);
        if (tag.getInt("Version") < DATA_VERSION) {
            settings.setDirty();
        }
        return settings;
    }

    static NaturalMudGenerationProfile loadProfile(CompoundTag tag) {
        NaturalMudGenerationProfile profile =
                NaturalMudGenerationProfile.defaults();
        ListTag entries = tag.getList("Rules", Tag.TAG_COMPOUND);
        int ruleCount = Math.min(MAX_PERSISTED_RULES, entries.size());
        if (entries.size() > ruleCount) {
            Mirebound.LOGGER.warn("Ignoring {} natural mud generation rules beyond the limit of {}",
                    entries.size() - ruleCount, MAX_PERSISTED_RULES);
        }
        for (int index = 0; index < ruleCount; index++) {
            CompoundTag entry = entries.getCompound(index);
            SinkingMedium medium = mediumByName(entry.getString("Medium"));
            if (medium == null) {
                continue;
            }
            Rule defaults = profile.rule(medium);
            if (defaults == null) {
                continue;
            }
            Set<String> selectors = new LinkedHashSet<>();
            ListTag savedSelectors = entry.getList("Biomes", Tag.TAG_STRING);
            int selectorCount = Math.min(MAX_BIOME_SELECTORS_PER_RULE, savedSelectors.size());
            if (savedSelectors.size() > selectorCount) {
                Mirebound.LOGGER.warn("Ignoring {} biome selectors beyond the per-rule limit of {}",
                        savedSelectors.size() - selectorCount, MAX_BIOME_SELECTORS_PER_RULE);
            }
            for (int selectorIndex = 0;
                    selectorIndex < selectorCount; selectorIndex++) {
                String selector = savedSelectors.getString(selectorIndex);
                if (!selector.isBlank() && selector.length() <= MAX_SELECTOR_LENGTH) {
                    selectors.add(selector);
                }
            }
            Rule loaded = defaults
                    .withEnabled(entry.getBoolean("Enabled"))
                    .withChance(entry.getInt("Chance"))
                    .withBiomeSelectors(selectors);
            if (entry.contains("Forms", Tag.TAG_LIST)) {
                List<NaturalMudDepositForm> forms = new ArrayList<>();
                ListTag savedForms = entry.getList("Forms", Tag.TAG_STRING);
                int formCount = Math.min(MAX_FORMS_PER_RULE, savedForms.size());
                for (int formIndex = 0; formIndex < formCount; formIndex++) {
                    NaturalMudDepositForm form = formByName(
                            savedForms.getString(formIndex));
                    if (form != null && !forms.contains(form)) {
                        forms.add(form);
                    }
                }
                loaded = loaded.withForms(forms);
            }
            if (entry.contains("MinimumRadius", Tag.TAG_INT)
                    && entry.contains("MaximumRadius", Tag.TAG_INT)) {
                loaded = loaded.withRadiusRange(
                        entry.getInt("MinimumRadius"),
                        entry.getInt("MaximumRadius"));
            }
            profile = profile.withRule(loaded);
        }
        return profile;
    }

    private static SinkingMedium mediumByName(String serializedName) {
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (medium.serializedName().equals(serializedName)) {
                return medium;
            }
        }
        return null;
    }

    private static NaturalMudDepositForm formByName(String serializedName) {
        for (NaturalMudDepositForm form : NaturalMudDepositForm.values()) {
            if (form.name().equals(serializedName)) {
                return form;
            }
        }
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", DATA_VERSION);
        ListTag entries = new ListTag();
        for (Rule rule : profile.rules()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Medium", rule.medium().serializedName());
            entry.putBoolean("Enabled", rule.enabled());
            entry.putInt("Chance", rule.chancePerHundredThousandChunks());
            entry.putInt("MinimumRadius", rule.minimumRadius());
            entry.putInt("MaximumRadius", rule.maximumRadius());
            ListTag selectors = new ListTag();
            for (String selector : rule.biomeSelectors()) {
                selectors.add(StringTag.valueOf(selector));
            }
            entry.put("Biomes", selectors);
            ListTag forms = new ListTag();
            for (NaturalMudDepositForm form : rule.forms()) {
                forms.add(StringTag.valueOf(form.name()));
            }
            entry.put("Forms", forms);
            entries.add(entry);
        }
        tag.put("Rules", entries);
        return tag;
    }
}
