package com.lukarbonite.iseeyourchunks.client;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfig;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfigManager;
import com.lukarbonite.iseeyourchunks.client.compat.VoxyIngestBridge;
import com.lukarbonite.iseeyourchunks.client.compat.VoxyFarNodeInjector;
import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ISeeYourChunksNetworking;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;

/**
 * Client entry point: announces this client's far-player streaming preferences to the server on join.
 * A server without the mod simply ignores the payload; a client without the mod never sends it, so a
 * vanilla client on a modded server (and vice-versa) both degrade to plain vanilla behaviour.
 */
public final class ISeeYourChunksFabricClient implements ClientModInitializer {
	/** Ceiling on the widened client chunk-storage radius, bounding the storage ring-buffer size. */
	private static final int MAX_FAR_CHUNK_RADIUS = 48;

	/** Sentinel for "the server has not told us its render distance yet". */
	private static final int UNKNOWN_RENDER_DISTANCE = -1;

	/** Set on join; cleared once the hello actually goes out (see the tick retry in onInitializeClient). */
	private static boolean helloPending;

	/** Server's render distance in chunks, from its ack; {@link #UNKNOWN_RENDER_DISTANCE} until it arrives. */
	private static volatile int serverRenderDistanceChunks = UNKNOWN_RENDER_DISTANCE;
	/** Whether the connected server has far-chunk streaming switched on, per its ack. */
	private static volatile boolean serverStreamingEnabled = true;

	@Override
	public void onInitializeClient() {
		ISeeYourChunksNetworking.registerPayloads();
		warnIfVoxyMissing();

		// The hello cannot simply be sent on JOIN: canSend() only becomes true once Fabric's channel
		// negotiation with the server completes, which may land after JOIN fires. Retry each tick until
		// it goes through, otherwise the server never learns this client wants far-chunk streaming.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> helloPending = true);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (helloPending && client.player != null) {
				sendHello();
			}
			VoxyFarNodeInjector.tick();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			helloPending = false;
			serverRenderDistanceChunks = UNKNOWN_RENDER_DISTANCE;
			serverStreamingEnabled = true;
			VoxyFarNodeInjector.reset();
		});

		ClientPlayNetworking.registerGlobalReceiver(ServerAckPayload.TYPE, (payload, context) -> {
			serverRenderDistanceChunks = payload.renderDistanceChunks() > 0
				? payload.renderDistanceChunks()
				: UNKNOWN_RENDER_DISTANCE;
			serverStreamingEnabled = payload.streamingEnabled();
			ISeeYourChunks.LOGGER.info(
				"Server ack: streaming={}, render distance={} chunks (terrain cap {} chunks).",
				serverStreamingEnabled, serverRenderDistanceChunks, serverChunkRenderCap());
		});
	}

	/**
	 * Voxy is what draws the streamed terrain, so it is required <em>on clients</em> - but not on
	 * dedicated servers, where it neither exists (it is a client-only mod) nor is needed. That rules out a
	 * hard {@code depends}, which would block the mod from loading server-side. Instead the requirement is
	 * enforced here: without Voxy the client still works, but degrades to bare distant players and mobs
	 * floating with no ground, so it is worth a loud, unmissable warning rather than silent breakage.
	 */
	private static void warnIfVoxyMissing() {
		if (VoxyIngestBridge.isAvailable()) {
			return;
		}
		ISeeYourChunks.LOGGER.warn(
			"Voxy is not installed. Distant players and mobs will still appear, but with NO terrain "
				+ "under them - they will float. Install Voxy on this client to render the streamed ground.");
	}

	/**
	 * Largest terrain-chunk count the connected server will honour, or the client hard maximum when the
	 * server has not said (single-player menu, pre-ack, or a server without the mod). Drives the slider's
	 * upper bound so it cannot promise chunks the server would only clamp away.
	 */
	/** The server's render distance in chunks from its ack, or a non-positive value if not yet known. */
	public static int knownServerRenderDistanceChunks() {
		return serverRenderDistanceChunks;
	}

	/**
	 * Re-applies the widened chunk-storage radius to the live world, so a mid-session config change takes
	 * effect without a reconnect.
	 *
	 * <p>The storage radius is otherwise only set at world join and when the server changes its view
	 * distance; raising the terrain count on the slider would otherwise not enlarge the buffer until the
	 * next reload, and the newly-requested outer chunks would be rejected as out of range. Driving
	 * {@code updateViewRadius} with the server's own view distance lets the storage mixin re-widen it to
	 * the new {@link #farChunkRadius()}; vanilla only rebuilds when the size actually changed, and it
	 * carries over every still-in-range chunk, so this is cheap and safe to call on each config commit.
	 */
	public static void reapplyStorageRadius() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !(client.level.getChunkSource() instanceof ClientChunkCache cache)) {
			return;
		}

		int baseViewDistance = serverRenderDistanceChunks > 0
			? serverRenderDistanceChunks
			: client.options.getEffectiveRenderDistance();
		cache.updateViewRadius(baseViewDistance);
	}

	public static int serverChunkRenderCap() {
		int renderDistance = serverRenderDistanceChunks;
		if (renderDistance <= 0) {
			return ISeeYourChunksConfig.MAX_CHUNK_RENDER_COUNT;
		}

		return Math.min(ISeeYourChunksConfig.MAX_CHUNK_RENDER_COUNT,
			ISeeYourChunksConfig.countChunksWithinRadius(renderDistance));
	}

	/** Re-announce preferences whenever the config changes so the server can adjust immediately. */
	public static void sendHello() {
		if (!ClientPlayNetworking.canSend(ClientHelloPayload.TYPE)) {
			// Server hasn't registered the channel (yet, or at all). Keep retrying from the client tick.
			return;
		}
		ISeeYourChunksConfig config = ISeeYourChunksConfigManager.getConfig();
		int desiredDistance = resolveDesiredDistanceBlocks(config);
		int chunkRenderCount = config.chunkRenderCount();
		ClientPlayNetworking.send(new ClientHelloPayload(
			ClientHelloPayload.PROTOCOL_VERSION,
			config.enabled() && config.renderRemotePlayers(),
			desiredDistance,
			chunkRenderCount
		));
		if (helloPending) {
			helloPending = false;
			ISeeYourChunks.LOGGER.info(
				"Sent far-visibility hello to server (enabled={}, distance={} blocks, chunks={}, farChunkRadius={} chunks).",
				config.enabled() && config.renderRemotePlayers(), desiredDistance, chunkRenderCount, farChunkRadius());
		}
	}

	/**
	 * How far this client wants distant players streamed. Defaults to the configured visibility
	 * distance; TODO: when Voxy is present, prefer its actual render distance so streaming tracks the
	 * terrain Voxy is already drawing.
	 */
	private static int resolveDesiredDistanceBlocks(ISeeYourChunksConfig config) {
		return config.visibilityDistanceBlocks();
	}

	/**
	 * Chunk-radius the client should widen its storage to so streamed far chunks are accepted.
	 *
	 * <p>Streamed chunks sit within the terrain disc around a distant player, whose centre may be as far
	 * as the visibility distance. The storage therefore has to reach that distance <em>plus</em> the disc
	 * radius, or the outer ring of every viewed player's terrain is rejected as out-of-range. The distance
	 * term is capped to bound the ring-buffer size; the disc term is added on top, since raising the
	 * chunk-render count is an explicit opt-in to a larger buffer.
	 */
	public static int farChunkRadius() {
		ISeeYourChunksConfig config = ISeeYourChunksConfigManager.getConfig();
		if (!config.enabled() || !config.renderRemotePlayers()) {
			return 0;
		}
		int distanceChunks = Math.min(MAX_FAR_CHUNK_RADIUS, config.visibilityDistanceBlocks() / 16);
		int discChunks = ISeeYourChunksConfig.impliedChunkRadius(config.chunkRenderCount());
		// The server never streams past its own render distance, so once it has told us, the disc cannot
		// reach further than that - no point widening storage for chunks that will never arrive.
		if (serverRenderDistanceChunks > 0) {
			discChunks = Math.min(discChunks, serverRenderDistanceChunks);
		}
		return distanceChunks + discChunks;
	}
}
