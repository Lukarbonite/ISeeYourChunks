package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.FarEntityFogPass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
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
 * exact arguments vanilla builds its own with. A dispatcher owns a single reusable prepared frame and vertex buffer,
 * so vanilla's cannot prepare a second, differently-fogged set of entities in the same frame. The second one gets its
 * own staged vertex buffer (see FeatureRenderDispatcherMixin), whose frame is ended alongside vanilla's.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@WrapOperation(
		method = "<init>",
		at = @At(value = "NEW", target = "net/minecraft/client/renderer/feature/FeatureRenderDispatcher")
	)
	private FeatureRenderDispatcher iSeeYourChunks$alsoCreateFarDispatcher(
		RenderBuffers renderBuffers, ModelManager modelManager, AtlasManager atlasManager, Font font,
		GameRenderState gameRenderState, Operation<FeatureRenderDispatcher> original
	) {
		FeatureRenderDispatcher vanilla = original.call(renderBuffers, modelManager, atlasManager, font, gameRenderState);
		FarEntityFogPass.beginDispatcherConstruction();
		try {
			FarEntityFogPass.setDispatcher(original.call(renderBuffers, modelManager, atlasManager, font, gameRenderState));
		} finally {
			FarEntityFogPass.endDispatcherConstruction();
		}
		return vanilla;
	}

	/** Ends our staged vertex buffer's frame at the same point vanilla ends its own. */
	@Inject(
		method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;endFrame()V", shift = At.Shift.AFTER)
	)
	private void iSeeYourChunks$endFarStagedFrame(CallbackInfo ci) {
		FarEntityFogPass.endStagedFrame();
	}

	@Inject(method = "close", at = @At("TAIL"))
	private void iSeeYourChunks$closeFarDispatcher(CallbackInfo ci) {
		FarEntityFogPass.close();
	}
}
