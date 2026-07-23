package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws managed entities that vanilla's entity collection culls once their chunk leaves the client cache.
 *
 * <p>The diagnosis that led here: a distant player renders on real terrain until it passes the client's
 * chunk-storage radius (~816 blocks), then the <em>whole</em> entity vanishes at once. It is not depth,
 * fog, or clipping - proven by testing every one of those. It is culling: vanilla only builds render states
 * for entities whose section is loaded/compiled, and past the storage radius the chunk is gone (streamed to
 * Voxy instead), so the entity is dropped before it is ever drawn.
 *
 * <p>Rather than fight where that cull lives in the deferred pipeline, this re-adds the dropped entities at
 * the point they would have been drawn. At the tail of {@code submitEntities} we already hold the
 * {@link SubmitNodeCollector} and the {@link CameraRenderState}; we extract a render state for each far
 * managed entity and submit it exactly as vanilla does. Only entities beyond the client render distance are
 * handled, so vanilla keeps drawing near ones and nothing is submitted twice in a way that matters.
 */
@Mixin(LevelRenderer.class)
abstract class FarEntityRenderMixin {
	@Shadow
	@Final
	private EntityRenderDispatcher entityRenderDispatcher;

	@org.spongepowered.asm.mixin.Unique
	private static long iSeeYourChunks$lastPassLogMs;

	@Inject(
		method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
		at = @At("TAIL")
	)
	private void iSeeYourChunks$submitFarEntities(
		PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector collector, CallbackInfo ci
	) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}

		// Beyond the client's own render distance is where vanilla stops drawing entities on its own; inside
		// it, vanilla already has them covered, so we start past it to avoid redundant work.
		double renderDistanceBlocks = client.options.getEffectiveRenderDistance() * 16.0D;
		double thresholdSq = renderDistanceBlocks * renderDistanceBlocks;

		java.util.List<Entity> farEntities = ClientEntityVisibility.collectFarRenderables(thresholdSq);
		if (farEntities.isEmpty()) {
			return;
		}

		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		CameraRenderState cameraRenderState = levelRenderState.cameraRenderState;
		double cameraX = cameraRenderState.pos.x();
		double cameraY = cameraRenderState.pos.y();
		double cameraZ = cameraRenderState.pos.z();
		for (Entity entity : farEntities) {
			try {
				EntityRenderState renderState = this.entityRenderDispatcher.extractEntity(entity, partialTick);
				// extractEntity leaves position to the caller (vanilla sets it during collection). submit
				// translates a camera-relative pose stack by these, so they must be world-minus-camera, not
				// world - passing raw world coords draws the entity that many blocks from the camera instead.
				renderState.x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - cameraX;
				renderState.y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - cameraY;
				renderState.z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cameraZ;
				renderState.distanceToCameraSq =
					renderState.x * renderState.x + renderState.y * renderState.y + renderState.z * renderState.z;
				this.entityRenderDispatcher.submit(
					renderState, cameraRenderState, renderState.x, renderState.y, renderState.z, poseStack, collector);
			} catch (RuntimeException exception) {
				// A single entity's renderer misbehaving must not take down the frame; surface it throttled.
				long now = System.currentTimeMillis();
				if (now - iSeeYourChunks$lastPassLogMs > 1000L) {
					iSeeYourChunks$lastPassLogMs = now;
					ISeeYourChunks.LOGGER.warn("Far-entity pass failed for {}.", entity, exception);
				}
			}
		}
	}
}
