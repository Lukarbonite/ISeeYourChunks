package com.lukarbonite.iseeyourchunks;

import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfig;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfigManager;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

/**
 * The single place that answers "should this entity ignore vanilla's tracking and rendering range?".
 * Shared by both sides on purpose: the server mixins and the client renderer must agree exactly, or an
 * entity gets tracked but never drawn (wasted bandwidth) or drawn from stale data (a frozen ghost).
 */
public final class EntityVisibilityRules {
	/** Stand-in for "no limit", far past any reachable coordinate. */
	public static final int INFINITE_TRACKING_DISTANCE_BLOCKS = 30_000_000;

	private EntityVisibilityRules() {
	}

	/**
	 * Whether the mod takes over visibility for this entity at all.
	 *
	 * <p>Players and other always-ticking entities follow {@code renderRemotePlayers}; everything else
	 * follows {@code renderRemoteEntities}. {@code isAlwaysTicking} is the discriminator rather than an
	 * {@code instanceof Player} check because it captures exactly the property that matters - the server
	 * keeps simulating these regardless of chunk state, so their streamed position stays truthful.
	 */
	public static boolean appliesTo(Entity entity) {
		if (entity.isRemoved()) {
			return false;
		}

		ISeeYourChunksConfig config = ISeeYourChunksConfigManager.getConfig();
		if (!config.enabled()) {
			return false;
		}

		return entity.isAlwaysTicking() ? config.renderRemotePlayers() : config.renderRemoteEntities();
	}

	/** Configured reveal range in blocks, or {@code 0} when this entity is not covered by the mod. */
	public static int trackingDistanceBlocks(Entity entity) {
		if (!appliesTo(entity)) {
			return 0;
		}

		return ISeeYourChunksConfigManager.getConfig().visibilityDistanceBlocks();
	}

	/**
	 * Whether this entity anchors its own reveal, independent of any viewer.
	 *
	 * <p>True only for always-ticking entities - players, in practice. They are the anchors the streamer
	 * builds terrain around, so they are force-tracked to every distant viewer unconditionally.
	 *
	 * <p>Ordinary mobs deliberately do <em>not</em> qualify. They are revealed per-viewer, and only when
	 * they stand in a chunk already being streamed for some nearby anchor - so a distant mob is visible
	 * because it shares ground with a viewed player, never because it exists somewhere on the server. That
	 * scoping lives in the tracker mixin, which is the only place the viewer is known.
	 */
	public static boolean anchorsOwnTracking(Entity entity) {
		return appliesTo(entity) && entity.isAlwaysTicking();
	}

	/**
	 * Whether any transitive passenger of {@code entity} matches {@code test} - i.e. whether {@code test}
	 * holds anywhere up the stack of things riding this one. Used to carry a shown player's visibility
	 * down onto whatever it is riding, and onto that mount's mount, so a ridden vehicle chain is shown
	 * with the player rather than leaving them seated on thin air.
	 */
	public static boolean anyPassenger(Entity entity, Predicate<Entity> test) {
		for (Entity passenger : entity.getPassengers()) {
			if (test.test(passenger) || anyPassenger(passenger, test)) {
				return true;
			}
		}
		return false;
	}
}
