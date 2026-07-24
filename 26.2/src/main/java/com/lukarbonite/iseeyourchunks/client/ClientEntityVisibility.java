package com.lukarbonite.iseeyourchunks.client;

import com.lukarbonite.iseeyourchunks.EntityVisibilityRules;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfigManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side view of {@link EntityVisibilityRules}: which distant entities draw, and where they are.
 *
 * <p>No manual-tick or occlusion machinery lives here. A revealed entity always stands in a streamed,
 * client-loaded chunk (the server only reveals mobs sitting in ground it is already sending, and always
 * reveals the anchor players it streams around), so vanilla ticks it normally and the depth buffer
 * resolves its occlusion against real geometry.
 */
public final class ClientEntityVisibility {
	/** Block positions of entities that passed {@link #isRenderable}, rebuilt at most once per tick. */
	private static final LongSet RENDERABLE_POSITIONS = new LongOpenHashSet();

	/** Slack past the farthest managed entity, so its far edge is not clipped the instant it appears. */
	private static final double FAR_PLANE_MARGIN_BLOCKS = 64.0D;

	/**
	 * Hard cap on the shared far-render bound, in blocks — the value used when the visibility-distance slider is
	 * set to Infinite. MC 26.2 (and Voxy) use a reverse-Z depth buffer, where precision is nearly independent of
	 * the far plane, so this can be large at essentially no cost to depth precision; it is finite only for sanity.
	 */
	public static final float MAX_FAR_RENDER_PLANE_BLOCKS = 1_024_000.0F;

	/**
	 * Floor on the shared far-render bound: never below Voxy's own default projection far plane (48,000), so
	 * ordinary Voxy terrain within its render sphere is never clipped even when the visibility slider is small.
	 */
	private static final float MIN_FAR_RENDER_PLANE_BLOCKS = 48_000.0F;

	/**
	 * Shared far-render bound, in blocks, for both streamed terrain and managed entities, computed live from the
	 * visibility-distance slider. Voxy's projection far plane is raised to this (see {@code VoxyRenderSystemMixin})
	 * and the managed-entity far plane is capped here, so a viewed player and the ground beneath them always clip
	 * together — and the reach grows or shrinks with the slider, up to {@link #MAX_FAR_RENDER_PLANE_BLOCKS} at
	 * Infinite. Read every frame (the Voxy projection is rebuilt per frame), so slider changes apply immediately.
	 */
	public static float farRenderPlaneBlocks() {
		double desired = (double) ISeeYourChunksConfigManager.getConfig().visibilityDistanceBlocks() + FAR_PLANE_MARGIN_BLOCKS;
		return (float) Math.max(MIN_FAR_RENDER_PLANE_BLOCKS, Math.min(MAX_FAR_RENDER_PLANE_BLOCKS, desired));
	}

	private static ClientLevel indexedLevel;
	private static long indexedTick = Long.MIN_VALUE;

	private ClientEntityVisibility() {
	}

	/** Whether the mod, rather than vanilla's range limits, decides this entity's visibility. */
	public static boolean isManaged(Entity entity) {
		return EntityVisibilityRules.appliesTo(entity) || carriesShownPlayer(entity);
	}

	/** Whether this entity should draw: managed, and inside the configured reveal range. */
	public static boolean isRenderable(Entity entity) {
		if (EntityVisibilityRules.appliesTo(entity) && isInRange(entity)) {
			return true;
		}
		// A vehicle ridden by a shown player draws with that player - regardless of the "render distant
		// mobs" toggle or whether any terrain surrounds it, so the player never rides an invisible mount.
		return carriesShownPlayer(entity);
	}

	/**
	 * Whether {@code entity} carries a shown player somewhere up its passenger stack. The player's own
	 * visibility (its distance, its {@code renderRemotePlayers} toggle) decides it; the mount inherits it,
	 * and so does the mount's mount.
	 */
	private static boolean carriesShownPlayer(Entity entity) {
		return EntityVisibilityRules.anyPassenger(entity, ClientEntityVisibility::isShownPlayer);
	}

	private static boolean isShownPlayer(Entity entity) {
		return entity instanceof Player && EntityVisibilityRules.appliesTo(entity) && isInRange(entity);
	}

	/** Whether a renderable managed entity occupies {@code pos}; see the LevelRenderer mixin. */
	public static boolean hasRenderableEntityAt(ClientLevel level, BlockPos pos) {
		reindexIfStale(level);
		return RENDERABLE_POSITIONS.contains(pos.asLong());
	}

	/** Drops the index, forcing a rebuild. Called when the config changes. */
	public static void invalidate() {
		indexedLevel = null;
		indexedTick = Long.MIN_VALUE;
		RENDERABLE_POSITIONS.clear();
	}

	/**
	 * The index is keyed on level plus game tick alone. Entity positions only advance on tick, so a
	 * finer key (camera movement, say) would rebuild repeatedly within a frame for an identical result.
	 */
	private static void reindexIfStale(ClientLevel level) {
		long tick = level.getGameTime();
		if (level == indexedLevel && tick == indexedTick) {
			return;
		}

		RENDERABLE_POSITIONS.clear();
		for (Entity entity : level.entitiesForRendering()) {
			if (!entity.isRemoved() && isRenderable(entity)) {
				RENDERABLE_POSITIONS.add(entity.blockPosition().asLong());
			}
		}

		indexedLevel = level;
		indexedTick = tick;
	}

	/**
	 * Far-clip distance, in blocks, needed so no managed entity is sliced by the camera's far plane, or
	 * {@code 0} when none are on screen. Vanilla builds the level projection with a far plane at roughly the
	 * render distance; a viewed player past it gets clipped, with the sky colour eating whichever limbs cross
	 * the plane first. Voxy draws its terrain through its own extended projection, so terrain persists while
	 * the vanilla entity pass keeps the short far plane - hence terrain-yes, player-clipped.
	 *
	 * <p>The result is bounded to exactly the farthest visible managed entity plus a margin, so the far plane
	 * is only pushed out while there is something out there to show. When nothing qualifies this returns
	 * {@code 0} and the caller keeps vanilla's far plane untouched, sparing the depth buffer any precision
	 * loss in ordinary play. A hard ceiling caps how far it can stretch, since depth precision degrades with
	 * the far/near ratio.
	 */
	public static float requiredFarPlaneBlocks() {
		double maxDistanceSq = farthestManagedEntityDistanceSq();
		if (maxDistanceSq <= 0.0D) {
			return 0.0F;
		}

		double needed = Math.sqrt(maxDistanceSq) + FAR_PLANE_MARGIN_BLOCKS;
		return (float) Math.min(needed, farRenderPlaneBlocks());
	}

	/**
	 * Managed, renderable entities at least {@code minDistanceSq} (blocks²) from the camera - the ones
	 * vanilla's entity collection culls because their chunk has left the client cache. The far-entity render
	 * pass draws exactly these, so a distant player keeps rendering past the storage radius. Near entities
	 * are excluded so vanilla keeps handling them and we do not double-submit.
	 */
	public static java.util.List<Entity> collectFarRenderables(double minDistanceSq) {
		java.util.List<Entity> out = new java.util.ArrayList<>();
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return out;
		}
		Vec3 camera = cameraPosition();
		if (camera == null) {
			return out;
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (!entity.isRemoved() && isRenderable(entity) && entity.distanceToSqr(camera) >= minDistanceSq) {
				out.add(entity);
			}
		}
		return out;
	}

	/** Squared distance from the camera to the farthest renderable managed entity, or {@code 0} if none. */
	private static double farthestManagedEntityDistanceSq() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return 0.0D;
		}
		Vec3 camera = cameraPosition();
		if (camera == null) {
			return 0.0D;
		}

		double maxDistanceSq = 0.0D;
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.isRemoved() || !isRenderable(entity)) {
				continue;
			}
			maxDistanceSq = Math.max(maxDistanceSq, entity.distanceToSqr(camera));
		}
		return maxDistanceSq;
	}

	private static boolean isInRange(Entity entity) {
		int limit = EntityVisibilityRules.trackingDistanceBlocks(entity);
		if (limit >= EntityVisibilityRules.INFINITE_TRACKING_DISTANCE_BLOCKS) {
			return true;
		}

		Vec3 camera = cameraPosition();
		if (camera == null) {
			// No camera yet (world still loading). Err towards drawing: a briefly visible entity beats
			// one that pops in late.
			return true;
		}

		return entity.distanceToSqr(camera) <= (double) limit * limit;
	}

	private static Vec3 cameraPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null || client.gameRenderer.mainCamera() == null) {
			return null;
		}

		return client.gameRenderer.mainCamera().position();
	}
}
