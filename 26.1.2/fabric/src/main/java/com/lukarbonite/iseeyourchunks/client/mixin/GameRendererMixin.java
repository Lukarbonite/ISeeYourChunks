package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.FarEntityFogPass;
import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the second {@link FeatureRenderDispatcher} that {@link FarEntityFogPass} renders far entities with, from the
 * exact arguments vanilla builds its own with except for the submit storage. A dispatcher renders everything in its
 * one storage, so far entities need their own storage (and so their own dispatcher) to be drawn under different fog.
 * The buffer sources are shared: on 26.1.2 features draw immediately and the pass flushes around each far phase.
 *
 * <p>Also brackets {@code renderLevel} with {@link ReverseZ}'s GL state, which spans exactly the world pass: it draws
 * the world and the hand.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@WrapOperation(
		method = "<init>",
		at = @At(value = "NEW", target = "net/minecraft/client/renderer/feature/FeatureRenderDispatcher")
	)
	private FeatureRenderDispatcher iSeeYourChunks$alsoCreateFarDispatcher(
		SubmitNodeStorage submitNodeStorage, ModelManager modelManager, MultiBufferSource.BufferSource bufferSource,
		AtlasManager atlasManager, OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource crumblingBufferSource,
		Font font, GameRenderState gameRenderState, Operation<FeatureRenderDispatcher> original
	) {
		FeatureRenderDispatcher vanilla = original.call(submitNodeStorage, modelManager, bufferSource, atlasManager,
			outlineBufferSource, crumblingBufferSource, font, gameRenderState);
		FeatureRenderDispatcher far = original.call(FarEntityFogPass.STORAGE, modelManager, bufferSource, atlasManager,
			outlineBufferSource, crumblingBufferSource, font, gameRenderState);
		FarEntityFogPass.setDispatcher(far, bufferSource);
		return vanilla;
	}

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void iSeeYourChunks$beginReverseZ(DeltaTracker deltaTracker, CallbackInfo ci) {
		ReverseZ.begin();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void iSeeYourChunks$endReverseZ(DeltaTracker deltaTracker, CallbackInfo ci) {
		ReverseZ.end();
	}

	@Inject(method = "close", at = @At("TAIL"))
	private void iSeeYourChunks$closeFarDispatcher(CallbackInfo ci) {
		FarEntityFogPass.close();
	}
}
