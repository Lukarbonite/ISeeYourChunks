package com.lukarbonite.iseeyourchunks.config;

import com.lukarbonite.iseeyourchunks.EntityVisibilityRules;

/**
 * Mutable settings holder, serialised to JSON as-is by {@link ISeeYourChunksConfigManager}.
 *
 * <p>A plain bean rather than a record because Gson populates the fields reflectively and the config
 * screen edits a working copy in place before committing it. Values are clamped on the way out as well
 * as on the way in, so a hand-edited file with nonsense in it still yields a usable config rather than
 * an exception at some distant call site.
 */
public final class ISeeYourChunksConfig {
	public static final int MIN_VISIBILITY_DISTANCE_BLOCKS = 64;
	public static final int MAX_VISIBILITY_DISTANCE_BLOCKS = EntityVisibilityRules.INFINITE_TRACKING_DISTANCE_BLOCKS;

	public static final int MIN_UPDATE_INTERVAL_TICKS = 1;
	public static final int MAX_UPDATE_INTERVAL_TICKS = 40;

	/**
	 * How much terrain to stream around a viewed player, expressed as a count of chunks (not a square).
	 *
	 * <p>The count grows by adding whichever chunk is nearest to the viewed player's actual position, so
	 * low values track exactly where they stand: 0 is player-and-mobs only, 1 is the chunk they are in, 2
	 * adds the neighbour they are closest to, and so on, filling out to 3x3 at 9, 5x5 at 25, and beyond
	 * that a circular radius. The client's chosen count is capped server-side at the chunks within the
	 * server's own render distance.
	 */
	public static final int MIN_CHUNK_RENDER_COUNT = 0;
	/** Client-side ceiling radius (chunks) the count may imply; the server clamps further to its own. */
	public static final int MAX_CHUNK_RENDER_RADIUS_CHUNKS = 32;
	public static final int MAX_CHUNK_RENDER_COUNT = countChunksWithinRadius(MAX_CHUNK_RENDER_RADIUS_CHUNKS);
	/** 3x3 around the viewed player. */
	public static final int DEFAULT_CHUNK_RENDER_COUNT = 9;

	// ─── Client-side ─────────────────────────────────────────────────────────
	private boolean enabled = true;
	private boolean renderRemotePlayers = true;
	private boolean renderRemoteEntities = true;
	private int visibilityDistanceBlocks = MAX_VISIBILITY_DISTANCE_BLOCKS;
	private int chunkRenderCount = DEFAULT_CHUNK_RENDER_COUNT;

	// ─── Server-side (only read on the machine running server logic) ─────────
	private boolean streamFarChunks = true;
	private boolean sendSpectators = false;
	private int updateIntervalTicks = 5;

	public boolean enabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean renderRemotePlayers() {
		return this.renderRemotePlayers;
	}

	public void setRenderRemotePlayers(boolean renderRemotePlayers) {
		this.renderRemotePlayers = renderRemotePlayers;
	}

	public boolean renderRemoteEntities() {
		return this.renderRemoteEntities;
	}

	public void setRenderRemoteEntities(boolean renderRemoteEntities) {
		this.renderRemoteEntities = renderRemoteEntities;
	}

	public int visibilityDistanceBlocks() {
		return clampVisibilityDistanceBlocks(this.visibilityDistanceBlocks);
	}

	public void setVisibilityDistanceBlocks(int visibilityDistanceBlocks) {
		this.visibilityDistanceBlocks = clampVisibilityDistanceBlocks(visibilityDistanceBlocks);
	}

	public int chunkRenderCount() {
		return clampChunkRenderCount(this.chunkRenderCount);
	}

	public void setChunkRenderCount(int chunkRenderCount) {
		this.chunkRenderCount = clampChunkRenderCount(chunkRenderCount);
	}

	public boolean streamFarChunks() {
		return this.streamFarChunks;
	}


	public boolean sendSpectators() {
		return this.sendSpectators;
	}


	public int updateIntervalTicks() {
		return clampUpdateIntervalTicks(this.updateIntervalTicks);
	}


	/** Independent copy with every value already clamped; used for edit-then-commit and on load. */
	public ISeeYourChunksConfig copy() {
		ISeeYourChunksConfig copy = new ISeeYourChunksConfig();
		copy.enabled = this.enabled;
		copy.renderRemotePlayers = this.renderRemotePlayers;
		copy.renderRemoteEntities = this.renderRemoteEntities;
		copy.visibilityDistanceBlocks = this.visibilityDistanceBlocks();
		copy.chunkRenderCount = this.chunkRenderCount();
		copy.streamFarChunks = this.streamFarChunks;
		copy.sendSpectators = this.sendSpectators;
		copy.updateIntervalTicks = this.updateIntervalTicks();
		return copy;
	}

	/**
	 * Anything at or above the ceiling means "no limit" rather than a literal 30 million blocks, so it is
	 * pinned exactly to the sentinel; everything else is held above the floor. A distance below the floor
	 * is not useful - vanilla already covers that range on its own.
	 */
	public static int clampVisibilityDistanceBlocks(int blocks) {
		if (blocks >= MAX_VISIBILITY_DISTANCE_BLOCKS) {
			return MAX_VISIBILITY_DISTANCE_BLOCKS;
		}

		return Math.max(MIN_VISIBILITY_DISTANCE_BLOCKS, blocks);
	}

	public static int clampUpdateIntervalTicks(int ticks) {
		return Math.clamp(ticks, MIN_UPDATE_INTERVAL_TICKS, MAX_UPDATE_INTERVAL_TICKS);
	}

	public static int clampChunkRenderCount(int count) {
		return Math.clamp(count, MIN_CHUNK_RENDER_COUNT, MAX_CHUNK_RENDER_COUNT);
	}

	/**
	 * Approximate radius in chunks that a nearest-first selection of {@code count} chunks reaches.
	 *
	 * <p>Inverts the disc-area relation ({@code count ~= pi * r^2}) and rounds up with a chunk of slack,
	 * so the client can widen its chunk storage enough to actually accept every streamed chunk rather
	 * than dropping the outer ring as out-of-range.
	 */
	public static int impliedChunkRadius(int count) {
		if (count <= 1) {
			return count <= 0 ? 0 : 1;
		}

		int radius = (int) Math.ceil(Math.sqrt(count / Math.PI)) + 1;
		return Math.min(radius, MAX_CHUNK_RENDER_RADIUS_CHUNKS);
	}

	/** Number of chunks whose centre lies within {@code radius} chunks of the origin (a filled disc). */
	public static int countChunksWithinRadius(int radius) {
		if (radius <= 0) {
			return radius == 0 ? 1 : 0;
		}

		int radiusSq = radius * radius;
		int count = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radiusSq) {
					count++;
				}
			}
		}
		return count;
	}
}
