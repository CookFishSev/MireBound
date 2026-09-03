package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.ClientMudDebugOptions;
import com.fish.mirebound.client.ClientPollutionVisibility;
import com.fish.mirebound.client.MudCapeTextureCache;
import com.fish.mirebound.client.MudSkinTextureCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void mirebound$useBakedMudSkin(CallbackInfoReturnable<PlayerSkin> callback) {
        PlayerSkin skin = callback.getReturnValue();
        if (skin == null) {
            return;
        }

        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (ClientPollutionVisibility.isSuppressed(player)) {
            return;
        }
        ResourceLocation bakedTexture = skin.texture();
        if (ClientMudDebugOptions.bakedSkin()
                && !MudSkinTextureCache.isGeneratedSkin(bakedTexture)) {
            ResourceLocation generatedSkin = MudSkinTextureCache.bakedSkinFor(
                    player.getId(), bakedTexture, skin.model() == PlayerSkin.Model.SLIM);
            if (generatedSkin != null) {
                bakedTexture = generatedSkin;
            }
        }
        ResourceLocation bakedCape = skin.capeTexture();
        if (ClientMudDebugOptions.skinLayer() && bakedCape != null
                && !MudCapeTextureCache.isGeneratedCape(bakedCape)) {
            ResourceLocation generatedCape = MudCapeTextureCache.bakedCapeFor(player, bakedCape);
            if (generatedCape != null) {
                bakedCape = generatedCape;
            }
        }
        if (bakedTexture.equals(skin.texture())
                && java.util.Objects.equals(bakedCape, skin.capeTexture())) {
            return;
        }

        callback.setReturnValue(new PlayerSkin(
                bakedTexture,
                skin.textureUrl(),
                bakedCape,
                skin.elytraTexture(),
                skin.model(),
                skin.secure()));
    }
}
