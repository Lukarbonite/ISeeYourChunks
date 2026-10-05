package com.lukarbonite.iseeyourchunks.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.EntityVisibilityRules;
import com.lukarbonite.iseeyourchunks.server.FarChunkStreamer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 1.20.1 form of the shared {@code ChunkMapTrackedEntityMixin}, which this version's mixin config lists in its
 * place. 1.20.1's {@code TrackedEntity.updatePlayer} gates tracking on distance alone: there is no "player already
 * holds the entity's chunk" check (that arrived with 1.20.2's chunk-tracking views), so only the range override is
 * needed. The reveal decision is the shared one: anchors (players) and whatever they ride are revealed to every
 * distant viewer, and any other entity only while it stands in a chunk that renders for that viewer.
 *
 * <p>{@code updatePlayer} takes the viewer as an argument, but the range wrapper fires on {@code Math.min} where
 * that argument is out of reach, so it is captured at HEAD. The server tracking loop is single-threaded, so the
 * field cannot be clobbered mid-call.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class ChunkMapTrackedEntityRangeMixin {
	@Shadow
	@Final
	private Entity entity;

	@Unique
	private ServerPlayer iSeeYourChunks$viewer;

	@Inject(method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
	private void iSeeYourChunks$captureViewer(ServerPlayer player, CallbackInfo ci) {
		this.iSeeYourChunks$viewer = player;
	}

	/**
	 * Vanilla clamps the tracking range to {@code min(rangeFromEntityType, viewDistanceInBlocks)}.
	 * When this entity is revealed to the captured viewer, the configured reveal distance replaces it.
	 */
	@WrapOperation(
		method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I")
	)
	private int iSeeYourChunks$overrideTrackingRange(int typeRange, int viewDistance, Operation<Integer> original) {
		if (iSeeYourChunks$revealedTo(this.iSeeYourChunks$viewer)) {
			return EntityVisibilityRules.trackingDistanceBlocks(this.entity);
		}

		return original.call(typeRange, viewDistance);
	}

	@Unique
	private boolean iSeeYourChunks$revealedTo(ServerPlayer viewer) {
		if (EntityVisibilityRules.anchorsOwnTracking(this.entity)) {
			return true;
		}
		// A vehicle carrying a revealed anchor is shown with it, terrain or not - never a floating rider.
		if (EntityVisibilityRules.anyPassenger(this.entity, EntityVisibilityRules::anchorsOwnTracking)) {
			return true;
		}
		if (viewer == null || !EntityVisibilityRules.appliesTo(this.entity)) {
			return false;
		}

		return FarChunkStreamer.isVisibleChunkFor(viewer, this.entity.getBlockX() >> 4, this.entity.getBlockZ() >> 4);
	}
}
