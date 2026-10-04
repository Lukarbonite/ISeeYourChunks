package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Voxy 0.2.18 ends {@code renderOpaque} by restoring vanilla's depth test as a hardcoded {@code GL_LESS}, through
 * GlStateManager (mirrored by {@link ReverseZ}) and then again as a raw GL call that bypasses it. Mirror the raw call
 * too, so GL and GlStateManager's cache agree while the world pass is reversed.
 */
@Mixin(VoxyRenderSystem.class)
public class VoxyDepthRestoreMixin {
	@WrapOperation(method = "renderOpaque", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL30C;glDepthFunc(I)V"))
	private void iSeeYourChunks$mirrorRestoredDepthFunc(int func, Operation<Void> original) {
		original.call(ReverseZ.mirrorIfActive(func));
	}
}
