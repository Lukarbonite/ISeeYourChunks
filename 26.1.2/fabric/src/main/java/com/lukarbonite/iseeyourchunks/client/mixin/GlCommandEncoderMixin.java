package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mirrors every depth clear for {@link ReverseZ}: while the world pass is reversed, clearing to vanilla's "far" (1)
 * must write reverse-Z's far (0). 26.1.2 sets the clear depth right before each clear (in both clear methods and in
 * render-pass creation), so no state needs tracking. The encoder class is package-private, hence the string target.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
abstract class GlCommandEncoderMixin {
	@WrapOperation(
		method = {"createRenderPass", "clearColorAndDepthTextures", "clearDepthTexture"},
		at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClearDepth(D)V")
	)
	private void iSeeYourChunks$mirrorClearDepth(double depth, Operation<Void> original) {
		original.call(ReverseZ.onClearDepth(depth));
	}
}
