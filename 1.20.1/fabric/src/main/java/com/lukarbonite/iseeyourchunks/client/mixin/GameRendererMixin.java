package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the level projection for distant entities: a far plane pushed out far enough to reach them, and reverse-Z
 * depth for the whole world pass (see {@link ReverseZ}).
 *
 * <p>Vanilla builds the level projection with a far plane at roughly the render distance ({@code getDepthFar()}), so
 * a viewed player streamed thousands of blocks away would be sliced by that plane. On 1.20.1 the projection is built
 * in {@link GameRenderer#getProjectionMatrix(double)} via {@code Matrix4f.setPerspective(fov, aspect, zNear, zFar)}; this
 * widens {@code zFar}. The cull frustum is derived from this same matrix, so the clip plane and frustum move together.
 * The widening is conditional: {@link ClientEntityVisibility#requiredFarPlaneBlocks()} is {@code 0} whenever no
 * managed entity is on screen, leaving vanilla's far plane exactly as it was.
 *
 * <p>While {@link ReverseZ} is active the same call builds the reversed projection instead: 0..1 depth with near and
 * far swapped, so the near plane maps to 1 and the far plane to 0. Reverse-Z spans exactly {@code renderLevel}, which
 * builds the world projection, draws the world, and draws the hand.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void iSeeYourChunks$beginReverseZ(float partialTick, long finishNanoTime, PoseStack poseStack, CallbackInfo ci) {
		ReverseZ.begin();
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void iSeeYourChunks$endReverseZ(float partialTick, long finishNanoTime, PoseStack poseStack, CallbackInfo ci) {
		ReverseZ.end();
	}

	@WrapOperation(
		method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;",
		at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;", remap = false)
	)
	private Matrix4f iSeeYourChunks$buildProjection(
		Matrix4f matrix, float fov, float aspect, float zNear, float zFar, Operation<Matrix4f> original
	) {
		float far = Math.max(zFar, ClientEntityVisibility.requiredFarPlaneBlocks());
		if (ReverseZ.isActive()) {
			return matrix.setPerspective(fov, aspect, far, zNear, true);
		}
		return original.call(matrix, fov, aspect, zNear, far);
	}
}
