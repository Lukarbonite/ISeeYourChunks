package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.ReverseZ;
import com.lukarbonite.iseeyourchunks.client.render.WorldProjection;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Builds reverse-Z projections for {@link ReverseZ}: 0..1 depth with near and far swapped, so the near plane maps to 1
 * and the far plane to 0. The camera's world projection is reversed whenever the frame's world pass will be (it is
 * copied into the camera render state during extraction, before the pass starts); any other projection only while
 * the pass runs (e.g. the hand's). A projection caches its matrix, so one built in the other mode is marked dirty and
 * rebuilt, which also bumps its version so the uploaded projection buffer refreshes.
 */
@Mixin(Projection.class)
abstract class ProjectionMixin implements WorldProjection {
	@Shadow
	private boolean isMatrixDirty;
	@Shadow
	private ProjectionType projectionType;
	@Shadow
	private float zNear;
	@Shadow
	private float zFar;
	@Shadow
	private float perspectiveFov;
	@Shadow
	private float width;
	@Shadow
	private float height;
	@Shadow
	private boolean orthoInvertY;

	@Unique
	private boolean iSeeYourChunks$worldProjection;
	@Unique
	private boolean iSeeYourChunks$builtReversed;

	@Override
	public void iSeeYourChunks$markWorldProjection() {
		this.iSeeYourChunks$worldProjection = true;
	}

	/** Mirrors vanilla's {@code getMatrix} construction, without the reversal. */
	@Override
	public Matrix4f iSeeYourChunks$standardMatrix(Matrix4f dest) {
		boolean zZeroToOne = RenderSystem.getDevice().isZZeroToOne();
		if (this.projectionType == ProjectionType.PERSPECTIVE) {
			return dest.setPerspective(this.perspectiveFov * ((float) Math.PI / 180.0F), this.width / this.height,
				this.zNear, this.zFar, zZeroToOne);
		}
		return dest.setOrtho(0.0F, this.width, this.orthoInvertY ? this.height : 0.0F, this.orthoInvertY ? 0.0F : this.height,
			this.zNear, this.zFar, zZeroToOne);
	}

	@Inject(method = "getMatrix", at = @At("HEAD"))
	private void iSeeYourChunks$rebuildOnModeChange(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
		this.iSeeYourChunks$dirtyOnModeChange();
	}

	/**
	 * The projection buffer only calls {@code getMatrix} when this version changes, and a dirty projection reports the
	 * next version, so marking it dirty here is what makes a mode change reach the GPU.
	 */
	@Inject(method = "getMatrixVersion", at = @At("HEAD"))
	private void iSeeYourChunks$bumpVersionOnModeChange(CallbackInfoReturnable<Long> cir) {
		this.iSeeYourChunks$dirtyOnModeChange();
	}

	@Unique
	private boolean iSeeYourChunks$shouldReverse() {
		return ReverseZ.isActive() || (this.iSeeYourChunks$worldProjection && ReverseZ.worldReversed());
	}

	@Unique
	private void iSeeYourChunks$dirtyOnModeChange() {
		if (this.iSeeYourChunks$builtReversed != this.iSeeYourChunks$shouldReverse()) {
			this.isMatrixDirty = true;
		}
	}

	@WrapOperation(method = "getMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFFZ)Lorg/joml/Matrix4f;"))
	private Matrix4f iSeeYourChunks$reversePerspective(
		Matrix4f matrix, float fov, float aspect, float zNear, float zFar, boolean zZeroToOne, Operation<Matrix4f> original
	) {
		this.iSeeYourChunks$builtReversed = this.iSeeYourChunks$shouldReverse();
		if (this.iSeeYourChunks$builtReversed) {
			return original.call(matrix, fov, aspect, zFar, zNear, true);
		}
		return original.call(matrix, fov, aspect, zNear, zFar, zZeroToOne);
	}

	@WrapOperation(method = "getMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setOrtho(FFFFFFZ)Lorg/joml/Matrix4f;"))
	private Matrix4f iSeeYourChunks$reverseOrtho(
		Matrix4f matrix, float left, float right, float bottom, float top, float zNear, float zFar, boolean zZeroToOne,
		Operation<Matrix4f> original
	) {
		this.iSeeYourChunks$builtReversed = this.iSeeYourChunks$shouldReverse();
		if (this.iSeeYourChunks$builtReversed) {
			return original.call(matrix, left, right, bottom, top, zFar, zNear, true);
		}
		return original.call(matrix, left, right, bottom, top, zNear, zFar, zZeroToOne);
	}
}
