package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import com.lukarbonite.iseeyourchunks.client.render.WorldProjection;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the level's far clip plane out far enough to draw a managed entity past render distance.
 *
 * <p>Vanilla builds the level projection with a far plane at roughly the render distance, so a viewed
 * player streamed thousands of blocks away is sliced by that plane - its limbs vanish into the sky colour
 * one at a time as it recedes. Voxy draws far <em>terrain</em> through its own extended projection, which
 * is why the ground persists while the vanilla entity pass keeps the short plane and clips the player.
 *
 * <p>The widening is deliberately conditional: {@link ClientEntityVisibility#requiredFarPlaneBlocks()}
 * returns {@code 0} whenever no managed entity is on screen, so {@link Math#max} leaves vanilla's far
 * plane exactly as it was and the depth buffer keeps its full precision in ordinary play. It only stretches
 * - up to a bounded ceiling - while there is genuinely a distant entity to reach.
 *
 * <p>Two things move together: the projection matrix (the geometric clip plane, via the
 * {@code setupPerspective} argument) and {@link CameraRenderState#depthFar} (which feeds the cull frustum),
 * or the entity would be clipped by whichever of the two was left short.
 *
 * <p>Also marks the camera's projection as the world projection for {@link ReverseZ}, and keeps the screen-point
 * projection (the locator bar's) on vanilla's standard depth, since it tests {@code z > 1} for "behind the camera".
 */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	@Final
	private Projection projection;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void iSeeYourChunks$markWorldProjection(CallbackInfo ci) {
		((WorldProjection) (Object) this.projection).iSeeYourChunks$markWorldProjection();
	}

	@WrapOperation(
		method = "getViewRotationProjectionMatrix",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Projection;getMatrix(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;")
	)
	private Matrix4f iSeeYourChunks$standardScreenProjection(Projection projection, Matrix4f dest, Operation<Matrix4f> original) {
		return ((WorldProjection) (Object) projection).iSeeYourChunks$standardMatrix(dest);
	}

	@ModifyArg(
		method = "update(Lnet/minecraft/client/DeltaTracker;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"),
		index = 1
	)
	private float iSeeYourChunks$extendFarClipPlane(float vanillaFar) {
		float needed = ClientEntityVisibility.requiredFarPlaneBlocks();
		return Math.max(vanillaFar, needed);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void iSeeYourChunks$extendCullFarPlane(CameraRenderState state, float partialTick, CallbackInfo ci) {
		float needed = ClientEntityVisibility.requiredFarPlaneBlocks();
		if (state.depthFar < needed) {
			state.depthFar = needed;
		}
	}
}
