package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Pushes Voxy's own render projection far plane out so streamed terrain draws past Voxy's hardcoded limit.
 *
 * <p>Voxy builds its LOD projection in {@code computeProjectionMat} with a fixed far plane of {@code 48000}
 * blocks; every column it draws — including the ones this mod injects as extra render roots — is sliced by
 * that plane, so far terrain simply stops there regardless of how far the streamer keeps sending it. This
 * raises that single constant to {@link ClientEntityVisibility#farRenderPlaneBlocks()}, the same bound the
 * managed-entity far plane uses, so terrain and the players standing on it reach the same distance. That bound
 * tracks the visibility-distance slider live, and {@code computeProjectionMat} runs every frame, so moving the
 * slider takes effect immediately.
 *
 * <p>Cheap by construction: Voxy uses a reverse-Z depth buffer here (it swaps near/far when reverse-Z is
 * active), where depth precision is nearly independent of the far plane, so a larger far plane does not cost
 * precision. Farther terrain is drawn at coarse, screen-space-selected LOD, so it also adds little fill cost.
 *
 * <p>Applied only when Voxy is present — gated by {@link VoxyMixinConfigPlugin} — since Voxy is an optional
 * client dependency and its classes are absent on a vanilla client (and always on a dedicated server).
 */
@Mixin(VoxyRenderSystem.class)
public class VoxyRenderSystemMixin {
	@ModifyConstant(method = "computeProjectionMat", constant = @Constant(floatValue = 48000.0F))
	private static float iSeeYourChunks$extendFarPlane(float original) {
		return ClientEntityVisibility.farRenderPlaneBlocks();
	}
}
