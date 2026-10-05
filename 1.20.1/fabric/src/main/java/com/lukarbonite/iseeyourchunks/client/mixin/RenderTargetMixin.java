package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Allocates render-target depth as 32-bit float ({@code GL_DEPTH_COMPONENT32F}) instead of the unsized
 * {@code GL_DEPTH_COMPONENT}, which drivers back with 24-bit fixed point. {@link ReverseZ} needs float depth: its
 * precision comes from depth values near 0, where only a float keeps fine steps. Applied to every target alike, so
 * depth copies between them (e.g. the entity-outline target copying the main target's depth) stay format-matched.
 */
@Mixin(RenderTarget.class)
abstract class RenderTargetMixin {
	@Unique
	private static final int GL_DEPTH_COMPONENT = 0x1902;
	@Unique
	private static final int GL_DEPTH_COMPONENT32F = 0x8CAC;

	@ModifyArg(
		method = "createBuffers",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", ordinal = 0),
		index = 2
	)
	private int iSeeYourChunks$floatDepth(int internalFormat) {
		return internalFormat == GL_DEPTH_COMPONENT ? GL_DEPTH_COMPONENT32F : internalFormat;
	}
}
