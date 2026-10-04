package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.render.FarEntityFogPass;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the frame's final fog to {@link FarEntityFogPass}. {@code updateBuffer} receives the {@link FogData} after
 * every {@code setupFog} hook (Voxy's included) has adjusted it, so far entities are fogged from the same values -
 * and the same Voxy fog mode - the rest of the frame uses.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("HEAD"))
	private void iSeeYourChunks$captureFrameFog(FogData fogData, CallbackInfo ci) {
		FarEntityFogPass.captureFog(fogData);
	}
}
