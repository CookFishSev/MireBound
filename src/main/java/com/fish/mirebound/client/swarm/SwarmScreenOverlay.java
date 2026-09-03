package com.fish.mirebound.client.swarm;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.client.ClientMudDebugOptions;
import com.fish.mirebound.client.ClientPollutionVisibility;
import com.fish.mirebound.client.ScreenOverlayLayout;
import com.fish.mirebound.client.ScreenOverlayLayout.CoverRect;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tick-driven screen larvae and a static pixel-art silk mask. The crawler
 * lifecycle follows the proven edge-spawn/side-steering/drop structure used by
 * mature swarm overlays while retaining a bounded allocation-free pool.
 */
public final class SwarmScreenOverlay {
    private static final int POOL_SIZE = 64;
    private static final int FRAME_WIDTH = 16;
    private static final int FRAME_HEIGHT = 8;
    private static final int FRAME_COUNT = 4;
    private static final int INSECT_TEXTURE_WIDTH = FRAME_WIDTH * FRAME_COUNT;
    private static final int SILK_FRAME_WIDTH = 320;
    private static final int SILK_FRAME_HEIGHT = 180;
    private static final int SILK_FRAME_COUNT = 3;
    private static final int SILK_TEXTURE_WIDTH = SILK_FRAME_WIDTH * SILK_FRAME_COUNT;
    private static final ResourceLocation INSECT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Mirebound.MOD_ID, "textures/gui/swarm_insects.png");
    private static final ResourceLocation SILK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Mirebound.MOD_ID, "textures/gui/swarm_silk_overlay.png");
    private static final Crawler[] CRAWLERS = createPool();
    private static long ticks;
    private static long spawnRandom = 0x535741524D535041L;

    private SwarmScreenOverlay() {
    }

    public static void tick() {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.SWARM_SCREEN)) {
            if (ticks != 0L || hasActiveCrawlers()) {
                reset();
            }
            return;
        }
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        float strength = ClientSwarmState.displayed();
        ClientMudDebugOptions.SwarmVisualMode mode = ClientMudDebugOptions.swarmVisualMode();
        float modeScale = mode == ClientMudDebugOptions.SwarmVisualMode.REDUCED ? 0.45F : 1.0F;
        int configuredMaximum = Mth.floor(value(
                minecraft, MudPhysicsParameter.SWARM_MAX_SCREEN_INSECTS));
        int targetCount = mode == ClientMudDebugOptions.SwarmVisualMode.OFF
                ? 0
                : Mth.clamp(
                        Math.round(configuredMaximum * strength * modeScale),
                        0,
                        POOL_SIZE);
        float speed = value(minecraft, MudPhysicsParameter.SWARM_SCREEN_SPEED);
        float wander = value(minecraft, MudPhysicsParameter.SWARM_INSECT_WANDER);
        float dropAcceleration = value(
                minecraft, MudPhysicsParameter.SWARM_DROP_ACCELERATION);
        int lifetime = Mth.floor(value(
                minecraft, MudPhysicsParameter.SWARM_SCREEN_LIFETIME));
        int width = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        int height = Math.max(1, minecraft.getWindow().getGuiScaledHeight());

        int crawling = 0;
        for (Crawler crawler : CRAWLERS) {
            if (!crawler.active) {
                continue;
            }
            crawler.tick(speed, wander, dropAcceleration, lifetime);
            if (crawler.active && !crawler.dropping) {
                crawling++;
            }
        }

        int excess = Math.max(0, crawling - targetCount);
        if (excess > 0) {
            int dropped = 0;
            float dropChance = targetCount == 0 ? 0.24F : 0.055F;
            for (Crawler crawler : CRAWLERS) {
                if (dropped >= excess) {
                    break;
                }
                if (crawler.active
                        && !crawler.dropping
                        && crawler.age < Math.max(1, lifetime - 10)
                        && crawler.randomFloat() < dropChance) {
                    crawler.beginDropping();
                    dropped++;
                }
            }
            if (dropped == 0 && (ticks & 3L) == 0L) {
                for (Crawler crawler : CRAWLERS) {
                    if (crawler.active && !crawler.dropping) {
                        crawler.beginDropping();
                        break;
                    }
                }
            }
            crawling -= dropped;
        }

        int missing = Math.max(0, targetCount - crawling);
        int spawnBudget = Math.min(missing, spawnBudget(strength));
        for (int spawned = 0; spawned < spawnBudget; spawned++) {
            Crawler crawler = firstInactive();
            if (crawler == null) {
                break;
            }
            crawler.spawn(width, height);
        }
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, float partialTick) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.SWARM_SCREEN)) {
            return;
        }
        ClientMudDebugOptions.SwarmVisualMode mode = ClientMudDebugOptions.swarmVisualMode();
        if (mode == ClientMudDebugOptions.SwarmVisualMode.OFF
                || minecraft.level == null
                || ClientPollutionVisibility.isLocalSuppressed(minecraft)) {
            return;
        }
        float strength = ClientSwarmState.displayed();
        boolean hasCrawlers = hasActiveCrawlers();
        if (strength <= 0.002F && !hasCrawlers) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        float modeScale = mode == ClientMudDebugOptions.SwarmVisualMode.REDUCED ? 0.45F : 1.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (strength > 0.002F) {
            renderSilkMask(graphics, minecraft, width, height, strength, modeScale);
        }
        if (hasCrawlers) {
            renderCrawlers(graphics, minecraft, width, height, strength, partialTick);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void reset() {
        ticks = 0L;
        spawnRandom = 0x535741524D535041L;
        for (Crawler crawler : CRAWLERS) {
            crawler.reset();
        }
    }

    private static void renderSilkMask(GuiGraphics graphics, Minecraft minecraft, int width, int height,
            float strength, float modeScale) {
        float opacity = value(minecraft, MudPhysicsParameter.SWARM_SILK_OPACITY);
        float density = value(minecraft, MudPhysicsParameter.SWARM_SILK_DENSITY);
        float reach = value(minecraft, MudPhysicsParameter.SWARM_SILK_REACH);
        float normalizedReach = Mth.clamp((reach - 0.10F) / 0.70F, 0.0F, 1.0F);
        int frame = Mth.clamp(
                Math.round(normalizedReach * (SILK_FRAME_COUNT - 1)),
                0,
                SILK_FRAME_COUNT - 1);
        float alpha = Mth.clamp(
                opacity * density * strength * modeScale,
                0.0F,
                1.0F);
        CoverRect cover = ScreenOverlayLayout.cover(
                width, height, SILK_FRAME_WIDTH, SILK_FRAME_HEIGHT);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(
                SILK_TEXTURE,
                cover.x(),
                cover.y(),
                cover.width(),
                cover.height(),
                frame * SILK_FRAME_WIDTH,
                0,
                SILK_FRAME_WIDTH,
                SILK_FRAME_HEIGHT,
                SILK_TEXTURE_WIDTH,
                SILK_FRAME_HEIGHT);
    }

    private static void renderCrawlers(GuiGraphics graphics, Minecraft minecraft, int width, int height,
            float strength, float partialTick) {
        float opacity = value(minecraft, MudPhysicsParameter.SWARM_SCREEN_OPACITY);
        float speed = value(minecraft, MudPhysicsParameter.SWARM_SCREEN_SPEED);
        float configuredScale = value(minecraft, MudPhysicsParameter.SWARM_INSECT_SCALE);
        float time = (ticks + partialTick) * speed;
        for (Crawler crawler : CRAWLERS) {
            if (!crawler.active) {
                continue;
            }
            float x = Mth.lerp(partialTick, crawler.previousX, crawler.x) * width;
            float y = Mth.lerp(partialTick, crawler.previousY, crawler.y) * height;
            float angle = (float) Math.atan2(
                    crawler.velocityY * height,
                    crawler.velocityX * width);
            float alpha = Mth.clamp(
                    opacity * crawler.lifeAlpha(),
                    0.0F,
                    1.0F);
            if (alpha <= 0.005F) {
                continue;
            }

            int frame = Math.floorMod(
                    Mth.floor(time * 0.42F + crawler.phase * FRAME_COUNT),
                    FRAME_COUNT);
            float scale = configuredScale * crawler.visualScale;
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotation(angle));
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.blit(
                    INSECT_TEXTURE,
                    -FRAME_WIDTH / 2,
                    -FRAME_HEIGHT / 2,
                    FRAME_WIDTH,
                    FRAME_HEIGHT,
                    frame * FRAME_WIDTH,
                    0,
                    FRAME_WIDTH,
                    FRAME_HEIGHT,
                    INSECT_TEXTURE_WIDTH,
                    FRAME_HEIGHT);
            graphics.pose().popPose();
        }
    }

    private static int spawnBudget(float strength) {
        if (strength <= 0.01F) {
            return 0;
        }
        if (strength <= 0.5F) {
            int period = Math.max(
                    1,
                    Math.round((0.5F - strength) / 0.5F * 20.0F + 1.0F));
            return ticks % period == 0L ? 1 : 0;
        }
        float upperStrength = (strength - 0.5F) / 0.5F;
        return 1 + Mth.floor(nextSpawnFloat() * upperStrength * 5.0F);
    }

    private static Crawler firstInactive() {
        for (Crawler crawler : CRAWLERS) {
            if (!crawler.active) {
                return crawler;
            }
        }
        return null;
    }

    private static boolean hasActiveCrawlers() {
        for (Crawler crawler : CRAWLERS) {
            if (crawler.active) {
                return true;
            }
        }
        return false;
    }

    private static Crawler[] createPool() {
        Crawler[] result = new Crawler[POOL_SIZE];
        long seed = 0x464953485153414EL;
        for (int index = 0; index < result.length; index++) {
            seed = mix(seed + index * 0x9E3779B97F4A7C15L);
            result[index] = new Crawler(seed);
        }
        return result;
    }

    private static float nextSpawnFloat() {
        spawnRandom = mix(spawnRandom + 0x9E3779B97F4A7C15L);
        return unit(spawnRandom >>> 17);
    }

    private static float value(Minecraft minecraft, MudPhysicsParameter parameter) {
        return (float) MudMediumRuntime.value(
                minecraft.level, ClientSwarmState.profilePos(),
                ClientSwarmState.medium(), parameter);
    }

    private static float unit(long value) {
        return (value & 0xFFFFL) / 65535.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static final class Crawler {
        private final long initialSeed;
        private long randomState;
        private boolean active;
        private boolean dropping;
        private float x;
        private float y;
        private float previousX;
        private float previousY;
        private float velocityX;
        private float velocityY;
        private float rotationBias;
        private float phase;
        private float visualScale;
        private int age;
        private int lifetime;

        private Crawler(long seed) {
            initialSeed = seed;
            reset();
        }

        private void reset() {
            randomState = initialSeed;
            active = false;
            dropping = false;
            age = 0;
        }

        private void spawn(int width, int height) {
            active = true;
            dropping = false;
            age = 0;
            lifetime = 1;
            float marginX = 20.0F / width;
            float marginY = 20.0F / height;
            float speed = 0.002F + randomFloat() * 0.006F;
            int edge = Mth.floor(randomFloat() * 4.0F) & 3;
            switch (edge) {
                case 0 -> {
                    x = -marginX;
                    y = randomFloat();
                    velocityX = speed;
                    velocityY = 0.0F;
                }
                case 1 -> {
                    x = 1.0F + marginX;
                    y = randomFloat();
                    velocityX = -speed;
                    velocityY = 0.0F;
                }
                case 2 -> {
                    x = randomFloat();
                    y = -marginY;
                    velocityX = 0.0F;
                    velocityY = speed;
                }
                default -> {
                    x = randomFloat();
                    y = 1.0F + marginY;
                    velocityX = 0.0F;
                    velocityY = -speed;
                }
            }
            previousX = x;
            previousY = y;
            rotationBias = 0.0F;
            phase = randomFloat();
            visualScale = 0.90F + randomFloat() * 0.24F;
        }

        private void tick(float speedScale, float wander, float dropAcceleration,
                int configuredLifetime) {
            previousX = x;
            previousY = y;
            if (dropping) {
                velocityY += dropAcceleration;
                velocityX *= 0.5F;
                x += velocityX;
                y += velocityY;
                if (y > 1.34F) {
                    active = false;
                }
                return;
            }

            lifetime = Math.max(20, configuredLifetime);
            age++;
            if (age > lifetime) {
                active = false;
                return;
            }

            x += velocityX * speedScale;
            y += velocityY * speedScale;
            float speed = Mth.sqrt(
                    velocityX * velocityX + velocityY * velocityY);
            if (speed <= 1.0E-6F) {
                return;
            }

            if (randomFloat() < 0.10F) {
                rotationBias = (randomFloat() - 0.5F) * 0.10F;
            }
            float sideX = velocityY;
            float sideY = -velocityX;
            float sideAmount = ((randomFloat() - 0.5F) * 0.50F + rotationBias)
                    * wander;
            float nextX = velocityX + sideX * sideAmount;
            float nextY = velocityY + sideY * sideAmount;
            float nextLength = Mth.sqrt(nextX * nextX + nextY * nextY);
            if (nextLength > 1.0E-6F) {
                velocityX = nextX / nextLength * speed;
                velocityY = nextY / nextLength * speed;
            }
        }

        private void beginDropping() {
            dropping = true;
        }

        private float lifeAlpha() {
            if (dropping) {
                return Mth.clamp((1.34F - y) / 0.28F, 0.0F, 1.0F);
            }
            float fadeIn = Mth.clamp(age / 7.0F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((lifetime - age) / 10.0F, 0.0F, 1.0F);
            return Math.min(fadeIn, fadeOut);
        }

        private float randomFloat() {
            randomState = mix(randomState + 0x9E3779B97F4A7C15L);
            return unit(randomState >>> 17);
        }
    }
}
