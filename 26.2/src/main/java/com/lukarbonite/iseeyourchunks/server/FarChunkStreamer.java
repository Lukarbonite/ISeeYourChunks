package com.lukarbonite.iseeyourchunks.server;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfig;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfigManager;
import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative streaming of the terrain immediately around distant players, so a subscribing
 * client can render them embedded in real geometry (and get correct occlusion) instead of floating in
 * the void. Distant player entities themselves are already tracked past view distance by the
 * {@code ChunkMap$TrackedEntity} mixins; this only adds their surrounding chunks.
 *
 * <p>Never loads or ticks anything new: it only re-sends chunks that are <em>already resident</em>
 * because the target player is standing in them ({@link ServerChunkCache#getChunkNow}). A vanilla
 * client that never sends a hello receives nothing.
 *
 * <p>Block changes inside streamed far chunks are forwarded incrementally: {@code ChunkHolderMixin}
 * hands every per-chunk broadcast packet to {@link #forwardChunkPacket}, which relays it to the
 * viewers currently streaming that chunk (tracked by {@link #CHUNK_VIEWERS}).
 */
public final class FarChunkStreamer {
	/** Hard cap on streamed chunks per viewer, bounding worst-case bandwidth. */
	private static final int MAX_STREAMED_CHUNKS_PER_VIEWER = 1024;

	private static final Map<UUID, Settings> SUBSCRIBERS = new ConcurrentHashMap<>();
	/** Last logged streamed-chunk count per viewer, so the diagnostic log only fires on change. */
	private static final Map<UUID, Integer> LAST_LOGGED_CHUNK_COUNT = new java.util.HashMap<>();
	/** Every chunk streamed to a viewer, including the invisible neighbour halo (for sending and forgetting). */
	private static final Map<UUID, LongSet> STREAMED_CHUNKS = new java.util.HashMap<>();
	/**
	 * The visible core disc per viewer - the chunks that actually render, without the halo. Distant mobs
	 * are revealed only when standing in one of these, so they never appear on the invisible halo ring
	 * beyond the terrain the viewer configured.
	 */
	private static final Map<UUID, LongSet> VISIBLE_CHUNKS = new java.util.HashMap<>();
	/** Reverse index: packed chunk position -> viewers currently streaming it (for block-update relay). */
	private static final Long2ObjectMap<Set<ServerPlayer>> CHUNK_VIEWERS = new Long2ObjectOpenHashMap<>();
	/**
	 * Streamed far chunks that changed since the last re-send. A client cannot apply incremental block-update
	 * packets to a chunk it never cached (out-of-range far chunks are decoded straight to Voxy, not stored),
	 * and even cached ones do not re-ingest into Voxy on a block change. So instead of relaying deltas we mark
	 * the chunk dirty here and re-send the whole chunk on the update interval; the client re-decodes and
	 * re-ingests it, which is what makes distant terrain edits actually update. Scoped to {@link #CHUNK_VIEWERS}
	 * so only our own streamed chunks (those around a viewed player) are ever tracked - never vanilla's.
	 */
	private static final LongSet DIRTY_CHUNKS = new LongOpenHashSet();

	private static int tickCounter;

	private FarChunkStreamer() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeViewer(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(FarChunkStreamer::onServerTick);
	}

	/** Records a client's opt-in and desired distance; ignores mismatched protocol versions. */
	public static void handleHello(ServerPlayer player, ClientHelloPayload payload) {
		if (payload.protocolVersion() != ClientHelloPayload.PROTOCOL_VERSION) {
			ISeeYourChunks.LOGGER.warn(
				"Ignoring far-visibility hello from {}: protocol {} != {}.",
				player.getScoreboardName(), payload.protocolVersion(), ClientHelloPayload.PROTOCOL_VERSION);
			SUBSCRIBERS.remove(player.getUUID());
			return;
		}
		SUBSCRIBERS.put(player.getUUID(), new Settings(
			payload.enabled(),
			Math.max(0, payload.desiredDistanceBlocks()),
			ISeeYourChunksConfig.clampChunkRenderCount(payload.chunkRenderCount())));
		ISeeYourChunks.LOGGER.info(
			"Far-visibility hello from {} (enabled={}, distance={} blocks, chunks={}). Subscribers: {}.",
			player.getScoreboardName(), payload.enabled(), payload.desiredDistanceBlocks(),
			payload.chunkRenderCount(), SUBSCRIBERS.size());

		sendAck(player);
	}

	/** Tells the client what this server can actually deliver, so its slider and storage match reality. */
	private static void sendAck(ServerPlayer player) {
		ISeeYourChunksConfig config = ISeeYourChunksConfigManager.getConfig();
		boolean streamingEnabled = config.enabled() && config.streamFarChunks();
		int renderDistanceChunks = player.level().getServer() != null
			? player.level().getServer().getPlayerList().getViewDistance()
			: 0;
		ServerPlayNetworking.send(player,
			new ServerAckPayload(ClientHelloPayload.PROTOCOL_VERSION, streamingEnabled, renderDistanceChunks));
	}

	/**
	 * Notes that a streamed far chunk changed, so it is re-sent (whole) on the next update interval. Called
	 * from {@code ChunkHolderMixin} for every per-chunk broadcast (block change, block entity, light). Only
	 * chunks we are actively streaming to someone ({@link #CHUNK_VIEWERS}) are tracked; every other chunk -
	 * all of vanilla's normally-tracked terrain - is ignored, so this never touches chunks that are not ours.
	 */
	public static void markStreamedChunkChanged(ServerLevel level, long chunkPos) {
		if (CHUNK_VIEWERS.containsKey(chunkPos)) {
			DIRTY_CHUNKS.add(chunkPos);
		}
	}

	/**
	 * Re-sends every dirty streamed chunk (whole) to the viewers still streaming it, then clears the set.
	 * Runs on the update interval, so a chunk edited many times between intervals costs a single re-send.
	 * Chunks a viewer now tracks through vanilla (they moved close) are skipped - vanilla owns those updates.
	 */
	private static void resendDirtyChunks() {
		if (DIRTY_CHUNKS.isEmpty()) {
			return;
		}
		int resent = 0;
		LongIterator iterator = DIRTY_CHUNKS.iterator();
		while (iterator.hasNext()) {
			long packed = iterator.nextLong();
			Set<ServerPlayer> viewers = CHUNK_VIEWERS.get(packed);
			if (viewers == null || viewers.isEmpty()) {
				continue;
			}
			int chunkX = ChunkPos.getX(packed);
			int chunkZ = ChunkPos.getZ(packed);
			for (ServerPlayer viewer : viewers) {
				ServerChunkCache chunkSource = viewer.level().getChunkSource();
				if (chunkSource.chunkMap.isChunkTracked(viewer, chunkX, chunkZ)) {
					continue;
				}
				if (sendChunk(viewer, chunkSource, chunkX, chunkZ)) {
					resent++;
				}
			}
		}
		DIRTY_CHUNKS.clear();
		if (resent > 0) {
			ISeeYourChunks.LOGGER.debug("Re-sent {} changed far chunk(s) to streaming viewers.", resent);
		}
	}

	private static void onServerTick(MinecraftServer server) {
		ISeeYourChunksConfig config = ISeeYourChunksConfigManager.getConfig();
		if (!config.enabled() || !config.streamFarChunks() || SUBSCRIBERS.isEmpty()) {
			return;
		}

		if (++tickCounter < config.updateIntervalTicks()) {
			return;
		}
		tickCounter = 0;

		int viewDistanceChunks = server.getPlayerList().getViewDistance();
		int maxDistanceCap = config.visibilityDistanceBlocks();
		boolean sendSpectators = config.sendSpectators();

		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			Settings settings = SUBSCRIBERS.get(viewer.getUUID());
			if (settings == null || !settings.enabled()) {
				forgetAll(viewer);
				continue;
			}
			updateViewer(viewer, settings, viewDistanceChunks, maxDistanceCap, sendSpectators);
		}

		// After (re)building each viewer's streamed set, re-send any of those chunks that changed since the
		// last interval, so distant terrain edits appear instead of freezing at first sight.
		resendDirtyChunks();
	}

	private static void updateViewer(ServerPlayer viewer, Settings settings, int viewDistanceChunks,
									 int maxDistanceCap, boolean sendSpectators) {
		ServerLevel level = viewer.level();
		int maxDistance = settings.desiredDistanceBlocks() > 0
			? Math.min(maxDistanceCap, settings.desiredDistanceBlocks())
			: maxDistanceCap;
		double maxDistanceSq = (double) maxDistance * maxDistance;

		// The viewed player's terrain footprint: how many chunks the viewer asked for, but never more
		// than the server is willing to send around a single player.
		int chunkCount = Math.min(settings.chunkRenderCount(), MAX_STREAMED_CHUNKS_PER_VIEWER);

		LongSet desired = new LongOpenHashSet();
		int revealedTargets = 0;
		double nearestTargetBlocks = Double.MAX_VALUE;
		for (ServerPlayer target : level.players()) {
			if (!shouldReveal(target, viewer, maxDistanceSq, sendSpectators)) {
				continue;
			}
			revealedTargets++;
			nearestTargetBlocks = Math.min(nearestTargetBlocks, Math.sqrt(viewer.distanceToSqr(target)));
			collectChunksAround(target, chunkCount, viewDistanceChunks, desired);
			if (desired.size() >= MAX_STREAMED_CHUNKS_PER_VIEWER) {
				break;
			}
		}
		int selectedChunks = desired.size();

		// The selection so far is the visible core - what actually renders, and the only place mobs are
		// revealed. Record it before padding so mob visibility tracks the terrain the viewer configured.
		VISIBLE_CHUNKS.put(viewer.getUUID(), desired);

		// Voxy's LOD mesher reads each chunk's neighbours to build it, so a chunk whose neighbours were
		// never streamed cannot mesh and stays invisible. Add a one-chunk halo around the selection: the
		// halo provides that neighbour data (it is ingested but never itself rendered), so every chunk the
		// viewer actually asked for is fully surrounded and meshes. Without this only the inner (n-2)^2 of
		// an n-wide patch would ever appear. The halo is streamed but never counted as visible above.
		if (!desired.isEmpty()) {
			desired = withNeighborHalo(desired);
		}

		LongSet current = STREAMED_CHUNKS.computeIfAbsent(viewer.getUUID(), key -> new LongOpenHashSet());
		ServerChunkCache chunkSource = level.getChunkSource();

		// Send newly-desired chunks that are actually loaded and that vanilla has not already sent.
		LongIterator desiredIterator = desired.iterator();
		while (desiredIterator.hasNext()) {
			long packed = desiredIterator.nextLong();
			if (current.contains(packed)) {
				continue;
			}
			int chunkX = ChunkPos.getX(packed);
			int chunkZ = ChunkPos.getZ(packed);
			// Vanilla already tracks this chunk for the viewer: it owns sending and updating it, so
			// re-sending would be wasted bandwidth (and would fight vanilla's own unload handling).
			if (chunkSource.chunkMap.isChunkTracked(viewer, chunkX, chunkZ)) {
				continue;
			}
			if (sendChunk(viewer, chunkSource, chunkX, chunkZ)) {
				current.add(packed);
				addChunkViewer(packed, viewer);
			}
		}

		// Forget chunks we streamed that are no longer desired.
		LongIterator currentIterator = current.iterator();
		while (currentIterator.hasNext()) {
			long packed = currentIterator.nextLong();
			if (desired.contains(packed)) {
				continue;
			}
			int chunkX = ChunkPos.getX(packed);
			int chunkZ = ChunkPos.getZ(packed);
			// Leave chunks vanilla now owns (viewer moved close) for vanilla to manage.
			if (!isWithinViewDistance(viewer, chunkX, chunkZ, viewDistanceChunks)) {
				viewer.connection.send(new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkX, chunkZ)));
			}
			removeChunkViewer(packed, viewer);
			currentIterator.remove();
		}

		// Log only on change so this stays quiet once the stream is steady. Reports the whole pipeline -
		// requested vs selected vs actually sent - plus how far the nearest revealed player is, so a
		// shortfall can be traced to the right stage (selection cap, unloaded chunks, or client-side
		// rejection when what the server sent is larger than what appears in game).
		Integer previous = LAST_LOGGED_CHUNK_COUNT.get(viewer.getUUID());
		if (previous == null || previous != current.size()) {
			LAST_LOGGED_CHUNK_COUNT.put(viewer.getUUID(), current.size());
			ISeeYourChunks.LOGGER.info(
				"Streaming to {}: requested={}, selected={}, sent={} chunks; {} player(s), nearest {} blocks, server view distance {} chunks.",
				viewer.getScoreboardName(), chunkCount, selectedChunks, current.size(), revealedTargets,
				revealedTargets == 0 ? "n/a" : String.format("%.0f", nearestTargetBlocks), viewDistanceChunks);
		}
	}

	/**
	 * Note there is deliberately no "target is inside view distance" shortcut here. Vanilla's view
	 * distance covers chunks around the <em>viewer</em>, which says nothing about whether the
	 * <em>target's</em> surroundings were sent; gating on it left a gap between the edge of the viewer's
	 * terrain and the target. Nearby targets cost nothing anyway, because every chunk around them is
	 * already vanilla-tracked and skipped per-chunk in {@link #updateViewer}.
	 */
	private static boolean shouldReveal(ServerPlayer target, ServerPlayer viewer,
										double maxDistanceSq, boolean sendSpectators) {
		if (!VisibilityFilter.canReveal(target, viewer, sendSpectators)) {
			return false;
		}
		return viewer.distanceToSqr(target) <= maxDistanceSq;
	}

	/**
	 * Adds the {@code chunkCount} chunks nearest to {@code target}'s position, closest first, bounded to a
	 * circular {@code maxRadiusChunks} disc (the server's render distance).
	 *
	 * <p>"Nearest" is measured from the player's real position to the closest point of each candidate
	 * chunk, so the growth order follows exactly where they stand: the chunk they occupy first, then the
	 * neighbour across whichever border they are nearest, and so on. That is what lets the setting behave
	 * as single-chunk steps at the low end yet fill out to a radius at the high end. Ties (a player dead-
	 * centre in their chunk) fall back to distance-to-centre, then a fixed coordinate order, so the result
	 * is deterministic and does not flicker between passes.
	 */
	private static void collectChunksAround(ServerPlayer target, int chunkCount, int maxRadiusChunks, LongSet out) {
		if (chunkCount <= 0) {
			return;
		}

		int homeX = target.getBlockX() >> 4;
		int homeZ = target.getBlockZ() >> 4;
		if (chunkCount == 1) {
			out.add(ChunkPos.pack(homeX, homeZ));
			return;
		}

		double px = target.getX();
		double pz = target.getZ();
		// A square of this radius holds far more than chunkCount chunks, so it is guaranteed to contain
		// the chunkCount nearest ones; capping at the render distance keeps the far edge circular.
		int searchRadius = Math.min(maxRadiusChunks, (int) Math.ceil(Math.sqrt(chunkCount)) + 1);
		double maxEdgeDistSq = (double) (maxRadiusChunks * 16) * (maxRadiusChunks * 16);

		List<Candidate> candidates = new java.util.ArrayList<>();
		for (int dx = -searchRadius; dx <= searchRadius; dx++) {
			for (int dz = -searchRadius; dz <= searchRadius; dz++) {
				int chunkX = homeX + dx;
				int chunkZ = homeZ + dz;
				double edgeDistSq = distanceToChunkEdgeSq(px, pz, chunkX, chunkZ);
				if (edgeDistSq > maxEdgeDistSq) {
					continue;
				}
				double centerDistSq = distanceToChunkCenterSq(px, pz, chunkX, chunkZ);
				candidates.add(new Candidate(ChunkPos.pack(chunkX, chunkZ), edgeDistSq, centerDistSq));
			}
		}

		candidates.sort(CANDIDATE_ORDER);
		int take = Math.min(chunkCount, candidates.size());
		for (int i = 0; i < take; i++) {
			out.add(candidates.get(i).packed());
		}
	}

	/**
	 * Returns {@code core} plus every chunk within one (Moore, 3x3) step of a core chunk. Vertical
	 * neighbours are ignored because full columns are streamed, so a chunk's up/down section neighbours
	 * are always present already; only horizontal neighbours can be missing.
	 */
	private static LongSet withNeighborHalo(LongSet core) {
		LongSet out = new LongOpenHashSet(core);
		LongIterator iterator = core.iterator();
		while (iterator.hasNext()) {
			long packed = iterator.nextLong();
			int cx = ChunkPos.getX(packed);
			int cz = ChunkPos.getZ(packed);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					out.add(ChunkPos.pack(cx + dx, cz + dz));
				}
			}
		}
		return out;
	}

	/** Squared distance from a point to the nearest point of a chunk's XZ footprint (0 if inside it). */
	private static double distanceToChunkEdgeSq(double px, double pz, int chunkX, int chunkZ) {
		double minX = chunkX << 4;
		double minZ = chunkZ << 4;
		double dx = Math.max(Math.max(minX - px, px - (minX + 16.0)), 0.0);
		double dz = Math.max(Math.max(minZ - pz, pz - (minZ + 16.0)), 0.0);
		return dx * dx + dz * dz;
	}

	/** Squared distance from a point to a chunk's centre; the tie-breaker when edge distances match. */
	private static double distanceToChunkCenterSq(double px, double pz, int chunkX, int chunkZ) {
		double dx = ((chunkX << 4) + 8.0) - px;
		double dz = ((chunkZ << 4) + 8.0) - pz;
		return dx * dx + dz * dz;
	}

	private record Candidate(long packed, double edgeDistSq, double centerDistSq) {
	}

	private static final java.util.Comparator<Candidate> CANDIDATE_ORDER =
		java.util.Comparator.comparingDouble(Candidate::edgeDistSq)
			.thenComparingDouble(Candidate::centerDistSq)
			.thenComparingLong(Candidate::packed);

	/**
	 * Whether the chunk at these coordinates is part of {@code viewer}'s <em>visible</em> terrain.
	 *
	 * <p>This is what scopes distant-entity visibility: a mob is revealed to a viewer precisely when it
	 * stands in ground the viewer can actually see, so it always appears on real terrain and never as a
	 * ghost. It is checked against the visible core, not the streamed set, so mobs never show up on the
	 * invisible neighbour halo that exists only to let Voxy mesh the edge of the disc.
	 */
	public static boolean isVisibleChunkFor(ServerPlayer viewer, int chunkX, int chunkZ) {
		LongSet visible = VISIBLE_CHUNKS.get(viewer.getUUID());
		return visible != null && visible.contains(ChunkPos.pack(chunkX, chunkZ));
	}

	private static boolean sendChunk(ServerPlayer viewer, ServerChunkCache chunkSource, int chunkX, int chunkZ) {
		LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
		if (chunk == null) {
			return false;
		}
		viewer.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, chunkSource.getLightEngine(), null, null));
		return true;
	}

	private static void forgetAll(ServerPlayer viewer) {
		VISIBLE_CHUNKS.remove(viewer.getUUID());
		LongSet current = STREAMED_CHUNKS.remove(viewer.getUUID());
		if (current == null || current.isEmpty()) {
			return;
		}
		LongIterator iterator = current.iterator();
		while (iterator.hasNext()) {
			long packed = iterator.nextLong();
			removeChunkViewer(packed, viewer);
			viewer.connection.send(new ClientboundForgetLevelChunkPacket(new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed))));
		}
	}

	/** Full cleanup on disconnect: drop the viewer from the reverse index and subscriber/streamed maps. */
	private static void removeViewer(ServerPlayer viewer) {
		UUID uuid = viewer.getUUID();
		SUBSCRIBERS.remove(uuid);
		LAST_LOGGED_CHUNK_COUNT.remove(uuid);
		VISIBLE_CHUNKS.remove(uuid);
		LongSet current = STREAMED_CHUNKS.remove(uuid);
		if (current != null) {
			LongIterator iterator = current.iterator();
			while (iterator.hasNext()) {
				removeChunkViewer(iterator.nextLong(), viewer);
			}
		}
	}

	private static void addChunkViewer(long packed, ServerPlayer viewer) {
		CHUNK_VIEWERS.computeIfAbsent(packed, key -> new HashSet<>()).add(viewer);
	}

	private static void removeChunkViewer(long packed, ServerPlayer viewer) {
		Set<ServerPlayer> viewers = CHUNK_VIEWERS.get(packed);
		if (viewers == null) {
			return;
		}
		viewers.remove(viewer);
		if (viewers.isEmpty()) {
			CHUNK_VIEWERS.remove(packed);
		}
	}

	private static boolean isWithinViewDistance(ServerPlayer viewer, int chunkX, int chunkZ, int viewDistanceChunks) {
		int viewerChunkX = viewer.getBlockX() >> 4;
		int viewerChunkZ = viewer.getBlockZ() >> 4;
		int chebyshev = Math.max(Math.abs(chunkX - viewerChunkX), Math.abs(chunkZ - viewerChunkZ));
		return chebyshev <= viewDistanceChunks;
	}

	private record Settings(boolean enabled, int desiredDistanceBlocks, int chunkRenderCount) {
	}
}
