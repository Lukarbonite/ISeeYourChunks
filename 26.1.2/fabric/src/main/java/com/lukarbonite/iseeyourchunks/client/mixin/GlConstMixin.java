package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.textures.TextureFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allocates {@code DEPTH32} textures as 32-bit float ({@code GL_DEPTH_COMPONENT32F}) instead of 32-bit fixed point
 * ({@code GL_DEPTH_COMPONENT32}, which drivers commonly back with 24 bits). {@link ReverseZ} needs float depth: its
 * precision comes from depth values near 0, where only a float keeps fine steps. Applied to every depth texture alike,
 * so depth copies between targets (e.g. the entity-outline target copying the main target's depth) stay matched.
 */
@Mixin(GlConst.class)
abstract class GlConstMixin {
	@Unique
	private static final int GL_DEPTH_COMPONENT32 = 0x81A7;
	@Unique
	private static final int GL_DEPTH_COMPONENT32F = 0x8CAC;

	@Inject(method = "toGlInternalId(Lcom/mojang/blaze3d/textures/TextureFormat;)I", at = @At("RETURN"), cancellable = true)
	private static void iSeeYourChunks$floatDepth(TextureFormat format, CallbackInfoReturnable<Integer> cir) {
		if (cir.getReturnValueI() == GL_DEPTH_COMPONENT32) {
			cir.setReturnValue(GL_DEPTH_COMPONENT32F);
		}
	}
}
