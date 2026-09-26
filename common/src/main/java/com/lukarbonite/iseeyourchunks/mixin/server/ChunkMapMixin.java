package com.lukarbonite.iseeyourchunks.mixin.server;

import com.lukarbonite.iseeyourchunks.EntityVisibilityRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
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
 * Re-evaluates tracking for managed entities on a fixed cadence.
 *
 * <p>{@link ChunkMapTrackedEntityMixin} changes the <em>answer</em> vanilla gives, but vanilla only asks
 * when something it watches changes - a player crossing a chunk border, an entity moving between
 * sections. A distant player standing still trips none of those, so a viewer who joins or turns towards
 * them would never receive the tracking update. Sweeping the tracker map covers that gap.
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
	/**
	 * Sweep every 4 ticks (5/second). Managed entities are far away, where a fraction of a second of
	 * staleness is invisible, and the sweep touches every tracked entity in the level - running it each
	 * tick would multiply that cost for no perceptible gain.
	 */
	@Unique
	private static final long ISEEYOURCHUNKS$SWEEP_INTERVAL_TICKS = 4L;

	@Shadow
	@Final
	private Int2ObjectMap<?> entityMap;

	@Shadow
	@Final
	ServerLevel level;

	@Inject(method = "tick()V", at = @At("TAIL"))
	private void iSeeYourChunks$sweepManagedTrackers(CallbackInfo ci) {
		if (this.level.getGameTime() % ISEEYOURCHUNKS$SWEEP_INTERVAL_TICKS != 0L) {
			return;
		}

		java.util.List<ServerPlayer> players = this.level.players();
		if (players.isEmpty()) {
			return;
		}

		// Refresh every managed entity against every player, not just the anchors: an ordinary mob's
		// reveal status flips as the streamed region around a distant player slides over or off it, and
		// nothing in vanilla re-runs the decision when only the *streamed set* changed.
		for (Object tracked : this.entityMap.values()) {
			ChunkMapTrackedEntityAccessor tracker = (ChunkMapTrackedEntityAccessor) tracked;
			Entity entity = tracker.iSeeYourChunks$entity();
			if (!EntityVisibilityRules.appliesTo(entity)) {
				continue;
			}

			for (ServerPlayer player : players) {
				tracker.iSeeYourChunks$updatePlayer(player);
			}
		}
	}
}
