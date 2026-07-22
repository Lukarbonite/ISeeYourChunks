package com.lukarbonite.iseeyourchunks.mixin.server;

import com.lukarbonite.iseeyourchunks.EntityVisibilityRules;
import com.lukarbonite.iseeyourchunks.server.FarChunkStreamer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lifts the two conditions that stop vanilla from tracking a distant entity for a given player, but only
 * as far as this mod actually wants.
 *
 * <p>Vanilla gates tracking on a distance cap and on the player already holding the entity's chunk. Both
 * are correct for vanilla, where an entity outside your loaded chunks has nothing to stand on. The reveal
 * decision here answers them differently in exactly two cases:
 * <ul>
 *   <li>an <em>anchor</em> (a player) is revealed to every distant viewer, since the streamer builds
 *       terrain around it; and
 *   <li>an ordinary entity is revealed to a viewer only while it stands in a chunk already being streamed
 *       to <em>that</em> viewer - so mobs appear on ground shared with a viewed player and nowhere else.
 * </ul>
 *
 * <p>The per-viewer case needs the player. {@code updatePlayer} takes it as an argument, but the range
 * redirect fires before that argument is in reach of a redirect, so it is captured at HEAD into
 * {@link #iSeeYourChunks$viewer} and consumed by both redirects within the same call. The server tracking
 * loop is single-threaded, so the field cannot be clobbered mid-call.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class ChunkMapTrackedEntityMixin {
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
	@Redirect(
		method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I")
	)
	private int iSeeYourChunks$overrideTrackingRange(int typeRange, int viewDistance) {
		if (iSeeYourChunks$revealedTo(this.iSeeYourChunks$viewer)) {
			return EntityVisibilityRules.trackingDistanceBlocks(this.entity);
		}

		return Math.min(typeRange, viewDistance);
	}

	/** A revealed entity is tracked whether or not the player holds its chunk through vanilla. */
	@Redirect(
		method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"
		)
	)
	private boolean iSeeYourChunks$overrideChunkRequirement(ChunkMap chunkMap, ServerPlayer player, int chunkX, int chunkZ) {
		return iSeeYourChunks$revealedTo(player) || chunkMap.isChunkTracked(player, chunkX, chunkZ);
	}

	/**
	 * Whether {@link #entity} should be revealed to {@code viewer}: unconditionally for anchors and for
	 * whatever an anchor is riding (so a mount, and its mount, ride along with the player), and for any
	 * other entity only while it occupies a chunk that renders for that viewer.
	 */
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
