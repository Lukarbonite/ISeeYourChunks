package com.lukarbonite.iseeyourchunks.client;

import com.lukarbonite.iseeyourchunks.EntityVisibilityRules;
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
