package com.lukarbonite.iseeyourchunks.client.compat;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Makes Voxy draw our streamed far terrain <em>past its own render sphere</em> without paying for the whole
 * disc in between.
 *
 * <p>Background. Voxy renders a single camera-centred sphere of LOD terrain: a {@code RenderDistanceTracker}
 * inserts a top-level node for every 512-block column within its render distance, and a hierarchical
 * occlusion traverser walks those roots. Everything we stream into the client cache sits well inside that
 * sphere, so Voxy already draws it. The terrain that goes missing is the streamed columns <em>beyond</em>
 * the sphere - a viewed player thousands of blocks out, past where Voxy creates any roots.
 *
 * <p>The naive fix is to enlarge the sphere, but its cost grows with the square of the radius because the
 * ring populates the entire disc. Instead we leave the ring alone and hand Voxy only the columns we actually
 * stream, as extra top-level roots:
 * <ol>
 *   <li>{@link me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager#addTopLevel(long)} - the
 *       exact, lock-guarded entry point the ring itself feeds - injects a 512-block column as a render root.
 *   <li>{@code VoxyConfig.sectionRenderDistance} widens only the traverser's <em>cull uniform</em> (a scalar
 *       distance threshold, decoupled from the ring) so those far roots are not GPU-culled. Voxy's own
 *       projection far plane is already a fixed 48000, so nothing else clips them.
 * </ol>
 * Occlusion stays exactly as accurate as normal Voxy - it is the same traverser, just given more roots.
 *
 * <p>Only columns past the ring are injected ({@link #noteFarChunk} early-returns otherwise): inside the
 * ring Voxy already owns the node and double-managing it would risk removing a root the ring still wants.
 * All state is dropped and re-captured whenever Voxy swaps its render system (dimension change), detected by
 * the node-manager instance changing under us.
 *
 * <p>Voxy is an optional, compile-time-only dependency, so every reference to its classes is confined to
 * {@link Backend}, only ever touched once {@link #VOXY_PRESENT} is confirmed.
 */
public final class VoxyFarNodeInjector {
	private static final boolean VOXY_PRESENT = FabricLoader.getInstance().isModLoaded("voxy");

	/** Top-level render nodes are level-4 sections, i.e. 512-block ({@code 1 << 9}) columns. */
	private static final int TOP_LEVEL = 4;
	private static final int TOP_LEVEL_SHIFT = 9;
	/** Blocks per top-level section; used to size Voxy's ring against a distance. */
	private static final double BLOCKS_PER_TOP_SECTION = 1 << TOP_LEVEL_SHIFT;
	/**
	 * The value we force Voxy's cull uniform ({@code sectionRenderDistance}) to whenever we inject a far root.
	 * The traversal shader distance-culls any node past {@code (sectionRenderDistance * 512)} blocks; tracking
	 * it to the farthest chunk left the cull sphere sitting right at the terrain edge, so the outermost nodes
	 * popped out. Pushing it well past the streaming cap (30M blocks) disables that cull for our roots without
	 * side effects: it is a bare scalar threshold, so it neither enlarges Voxy's ring nor adds any nodes, and
	 * LOD detail is chosen separately by screen-space error. {@code 100000 * 512} reaches ~51M blocks.
	 */
	private static final float UNLIMITED_CULL_SECTIONS = 100_000.0f;

	/**
	 * Delay before forcing a re-mesh of a freshly injected node. {@code addTopLevel} is drained on Voxy's
	 * tick, so the node is not live the instant we inject it; waiting a beat lets it exist (and any streaming
	 * data settle) before {@code markDirty} clears its stale/empty cached geometry and rebuilds it.
	 */
	private static final long REMESH_DELAY_MS = 750L;

	private VoxyFarNodeInjector() {
	}

	public static boolean isAvailable() {
		return VOXY_PRESENT;
	}

	/**
	 * Offers a just-streamed far chunk to Voxy as a render root. Injects it only if it lies beyond Voxy's
	 * own render ring (otherwise Voxy already draws it); widens the cull uniform to reach it when it does.
	 *
	 * @param minBlockY / maxBlockY the world's vertical bounds, so every column the chunk spans is rooted
	 * @param distanceBlocks horizontal distance from the viewer, used both to gate and to size the uniform
	 */
	public static void noteFarChunk(int chunkX, int chunkZ, int minBlockY, int maxBlockY, double distanceBlocks) {
		if (VOXY_PRESENT) {
			Backend.noteFarChunk(chunkX, chunkZ, minBlockY, maxBlockY, distanceBlocks);
		}
	}

	/** Drops every injected root and restores the user's cull distance. Call on disconnect. */
	public static void reset() {
		if (VOXY_PRESENT) {
			Backend.reset();
		}
	}

	/** Drives deferred re-meshes of injected nodes. Call once per client tick. */
	public static void tick() {
		if (VOXY_PRESENT) {
			Backend.tick();
		}
	}

	private static final class Backend {
		private static final Object LOCK = new Object();

		/** Cached reflective handle to {@code VoxyRenderSystem.nodeManager} (no public getter exists). */
		private static java.lang.reflect.Field nodeManagerField;
		/** The manager our tracked roots belong to; a change means Voxy rebuilt and the old roots are gone. */
		private static me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager boundManager;
		/** Section ids we have injected, so we inject each once and can remove them on reset. */
		private static final java.util.HashSet<Long> inserted = new java.util.HashSet<>();
		/** Injected top-level ids awaiting a deferred re-mesh, mapped to the earliest time to fire it. */
		private static final java.util.HashMap<Long, Long> pendingRemesh = new java.util.HashMap<>();
		/** The user's own cull distance, captured before we ever raise it, so reset can put it back. */
		private static float baseCullSections = Float.NaN;

		/** Throttle for the (debug-level) re-mesh log so a busy stream cannot flood it. */
		private static long lastRemeshLogMs;

		private Backend() {
		}

		private static me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager currentManager() {
			me.cortex.voxy.client.core.VoxyRenderSystem system =
				me.cortex.voxy.client.core.IVoxyRenderSystemHolder.getNullable();
			if (system == null) {
				return null;
			}
			try {
				if (nodeManagerField == null) {
					nodeManagerField =
						me.cortex.voxy.client.core.VoxyRenderSystem.class.getDeclaredField("nodeManager");
					nodeManagerField.setAccessible(true);
				}
				return (me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager) nodeManagerField.get(system);
			} catch (ReflectiveOperationException exception) {
				return null;
			}
		}

		static void noteFarChunk(int chunkX, int chunkZ, int minBlockY, int maxBlockY, double distanceBlocks) {
			try {
				synchronized (LOCK) {
					me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager manager = currentManager();
					if (manager == null) {
						return;
					}
					if (manager != boundManager) {
						// Voxy rebuilt its render system (dimension change). The old roots died with it; drop
						// our bookkeeping. Keep the captured base cull distance across the swap so a session's
						// widening persists - reset() (disconnect) is what restores it.
						boundManager = manager;
						inserted.clear();
					}
					if (Float.isNaN(baseCullSections)) {
						baseCullSections = me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance;
					}

					// Inside Voxy's own ring it already owns this column; leave it alone to avoid fighting the
					// ring over the same root. The ring's radius tracks the user's cull distance.
					double ringBlocks = baseCullSections * BLOCKS_PER_TOP_SECTION;
					if (distanceBlocks <= ringBlocks) {
						return;
					}

					// Disable the traverser's distance cull for our roots by pushing the cull uniform past any
					// streamable distance (see UNLIMITED_CULL_SECTIONS). A bare scalar threshold: no disc, no
					// extra nodes, LOD unaffected.
					if (me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance < UNLIMITED_CULL_SECTIONS) {
						me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance = UNLIMITED_CULL_SECTIONS;
					}

					// One chunk maps to one horizontal 512-block column (chunk<<4 >> 9 == chunk >> 5); root
					// every vertical section the world spans so the whole height is drawn.
					int topX = chunkX >> (TOP_LEVEL_SHIFT - 4);
					int topZ = chunkZ >> (TOP_LEVEL_SHIFT - 4);
					int topYMin = minBlockY >> TOP_LEVEL_SHIFT;
					int topYMax = maxBlockY >> TOP_LEVEL_SHIFT;
					int added = 0;
					for (int topY = topYMin; topY <= topYMax; topY++) {
						long id = me.cortex.voxy.common.world.WorldEngine.getWorldSectionId(TOP_LEVEL, topX, topY, topZ);
						if (inserted.add(id)) {
							manager.addTopLevel(id);
							added++;
						}
						// Voxy will not re-mesh a node live on its own (its known "needs a rejoin to refresh far
						// chunks" behaviour), so every time fresh data for this column arrives we schedule a
						// deferred markDirty to rebuild it. putIfAbsent debounces: at most one pending re-mesh per
						// node, firing REMESH_DELAY_MS after it was scheduled, re-armed by the next arriving chunk.
						pendingRemesh.putIfAbsent(id, System.currentTimeMillis() + REMESH_DELAY_MS);
					}
					if (added > 0 && ISeeYourChunks.LOGGER.isDebugEnabled()) {
						ISeeYourChunks.LOGGER.debug(
							"Voxy far-node: injected {} root(s) at top {},{} (chunk {},{} at {}b).",
							added, topX, topZ, chunkX, chunkZ, (int) distanceBlocks);
					}
				}
			} catch (Throwable throwable) {
				// Far-node injection is a rendering nicety; it must never break chunk receipt. Surface once.
				ISeeYourChunks.LOGGER.warn("Voxy far-node injection failed for chunk {},{}.", chunkX, chunkZ, throwable);
			}
		}

		/**
		 * Fires due deferred re-meshes: for each injected node whose delay has elapsed, clears its cached
		 * geometry and rebuilds it via {@link me.cortex.voxy.common.world.WorldEngine#markDirty}. This is what
		 * makes far terrain actually appear - the node and its data are already present, but Voxy will not
		 * re-mesh it live without this nudge.
		 */
		static void tick() {
			synchronized (LOCK) {
				if (pendingRemesh.isEmpty()) {
					return;
				}
				me.cortex.voxy.client.core.VoxyRenderSystem system =
					me.cortex.voxy.client.core.IVoxyRenderSystemHolder.getNullable();
				me.cortex.voxy.common.world.WorldEngine engine = system == null ? null : system.getEngine();
				if (engine == null) {
					// No live engine (loading / disconnected). Drop the backlog; a fresh session re-injects.
					pendingRemesh.clear();
					return;
				}
				long now = System.currentTimeMillis();
				int remeshed = 0;
				java.util.Iterator<java.util.Map.Entry<Long, Long>> it = pendingRemesh.entrySet().iterator();
				while (it.hasNext()) {
					java.util.Map.Entry<Long, Long> entry = it.next();
					if (entry.getValue() > now) {
						continue;
					}
					it.remove();
					try {
						me.cortex.voxy.common.world.WorldSection section = engine.acquireIfExists(entry.getKey());
						if (section != null) {
							engine.markDirty(section);
							section.release();
							remeshed++;
						}
					} catch (Throwable ignored) {
						// A single section failing to re-mesh must not stall the rest.
					}
				}
				if (remeshed > 0 && ISeeYourChunks.LOGGER.isDebugEnabled() && now - lastRemeshLogMs > 1000L) {
					lastRemeshLogMs = now;
					ISeeYourChunks.LOGGER.debug(
						"Voxy far-node: forced re-mesh of {} node(s), {} still pending.", remeshed, pendingRemesh.size());
				}
			}
		}

		static void reset() {
			synchronized (LOCK) {
				pendingRemesh.clear();
				// Deliberately do NOT removeTopLevel here. On disconnect/dimension change Voxy tears down its
				// whole node manager, and the ring may already have removed nodes we also injected - calling
				// removeTopLevel then throws "Position not in top level map" on Voxy's async thread and corrupts
				// its processor. Dropping our bookkeeping is enough; Voxy reclaims the roots on teardown.
				inserted.clear();
				if (!Float.isNaN(baseCullSections)) {
					me.cortex.voxy.client.config.VoxyConfig.CONFIG.sectionRenderDistance = baseCullSections;
					baseCullSections = Float.NaN;
				}
				boundManager = null;
			}
		}
	}
}
