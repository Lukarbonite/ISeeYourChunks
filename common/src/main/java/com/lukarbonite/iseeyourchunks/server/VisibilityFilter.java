package com.lukarbonite.iseeyourchunks.server;

import net.minecraft.server.level.ServerPlayer;

/**
 * Categorical "may this player ever be revealed to that one" test, applied before the server spends
 * any bandwidth streaming a target's surroundings. Distance is deliberately not considered here:
 * {@link FarChunkStreamer} owns that, because the cutoff is per-viewer and changes as either player
 * moves, whereas everything below is a property of the target alone (plus the vanish relationship).
 */
public final class VisibilityFilter {
	private VisibilityFilter() {
	}

	public static boolean canReveal(ServerPlayer target, ServerPlayer viewer, boolean sendSpectators) {
		if (target == viewer) {
			return false;
		}

		return isRevealable(target, sendSpectators) && VanishSupport.isVisibleTo(target, viewer);
	}

	/**
	 * Target-side exclusions. A dead or removed player has no meaningful position to stream around, and
	 * revealing an invisible or spectating player would leak information vanilla deliberately withholds
	 * at close range - the whole point of this mod is to extend normal visibility, not to widen it.
	 */
	private static boolean isRevealable(ServerPlayer target, boolean sendSpectators) {
		if (target.isRemoved() || !target.isAlive()) {
			return false;
		}
		if (target.isInvisible()) {
			return false;
		}

		return sendSpectators || !target.isSpectator();
	}
}
