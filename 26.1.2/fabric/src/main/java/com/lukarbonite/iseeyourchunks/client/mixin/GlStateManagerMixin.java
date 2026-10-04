package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mirrors vanilla's depth comparison and polygon offset for {@link ReverseZ} at the GL calls: while the world pass is
 * reversed, every depth comparison flips direction and polygon offsets change sign (towards the camera is a larger
 * depth when reversed). The logical values are recorded either way, so leaving reverse-Z can restore exactly what
 * vanilla expects. Every pipeline's depth state is applied through these calls.
 */
@Mixin(GlStateManager.class)
abstract class GlStateManagerMixin {
	@WrapOperation(method = "_depthFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V"))
	private static void iSeeYourChunks$mirrorDepthFunc(int func, Operation<Void> original) {
		original.call(ReverseZ.onDepthFunc(func));
	}

	@WrapOperation(method = "_polygonOffset", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPolygonOffset(FF)V"))
	private static void iSeeYourChunks$mirrorPolygonOffset(float factor, float units, Operation<Void> original) {
		ReverseZ.onPolygonOffset(factor, units);
		float sign = ReverseZ.polygonOffsetSign();
		original.call(sign * factor, sign * units);
	}
}
