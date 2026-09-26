package com.lukarbonite.iseeyourchunks.client.compat;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.platform.PlatformHelper;
import com.lukarbonite.iseeyourchunks.platform.VersionHelper;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Pushes a streamed far chunk into Voxy's LOD model right when it arrives.
 *
 * <p>Voxy normally ingests a chunk only when it <em>unloads</em> from the vanilla client cache
 * ({@code MixinClientChunkCache} → {@code VoxelIngestService.tryAutoIngestChunk}), on the assumption that
 * anything still resident is being drawn live by Sodium. Our streamed chunks break that assumption: they
 * sit permanently in the widened cache (or bypass it entirely), so they never unload and Voxy would never
 * see them. We cannot reuse {@code tryAutoIngestChunk} either: it sources light from the client light
 * engine, which never lights a chunk past render distance, so it silently writes nothing. Instead
 * {@link Backend} runs Voxy's own conversion pipeline directly with a light source we supply.
 *
 * <p>Voxy is an optional, compile-time-only dependency. Every reference to its classes is confined to
 * {@link Backend}, which is only ever touched once {@link #VOXY_PRESENT} is confirmed, so Voxy's classes
 * never resolve when the mod is absent.
 */
public final class VoxyIngestBridge {
	private static final boolean VOXY_PRESENT = PlatformHelper.get().isModLoaded("voxy");

	/** Total far chunks handed to Voxy; the first, and each power-of-two-ish milestone, is logged. */
	private static final AtomicLong INGEST_COUNT = new AtomicLong();

	private VoxyIngestBridge() {
	}

	public static boolean isAvailable() {
		return VOXY_PRESENT;
	}

	/** Hands {@code chunk} to Voxy's auto-ingest, if Voxy is installed. Fails open on any error. */
	public static void ingest(LevelChunk chunk) {
		if (!VOXY_PRESENT || chunk == null) {
			return;
		}
		Backend.ingest(chunk);

		long count = INGEST_COUNT.incrementAndGet();
		if (count == 1 || count % 64 == 0) {
			ISeeYourChunks.LOGGER.info("Handed {} streamed far chunk(s) to Voxy for LOD rendering.", count);
		}
	}

	private static final class Backend {
		/**
		 * Heightmap used to approximate sky exposure. {@code MOTION_BLOCKING} is the closest occluder height
		 * the server sends in the chunk packet; if it is absent {@code getHeight} returns the world bottom, so
		 * every voxel reads as sky-exposed (the old flat-lit behaviour) rather than pitch black - a safe fallback.
		 */
		private static final net.minecraft.world.level.levelgen.Heightmap.Types LIGHT_HEIGHTMAP =
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING;

		private Backend() {
		}

		/**
		 * Builds a 16^3 packed-light array for one section, indexed exactly as a vanilla {@code DataLayer}
		 * ({@code (y<<8)|(z<<4)|x}, byte = {@code (blockLight<<4)|skyLight}) so the supplier reading it behaves
		 * identically to Voxy's own light source.
		 *
		 * <p>Prefers the chunk packet's <em>real</em> light (block + sky, so torches and lava glow and sky
		 * exposure is exact), captured by {@code ClientPacketListenerMixin}. Only when no packet light is
		 * available for this chunk does it fall back to a heightmap sky approximation (full above the surface,
		 * attenuating one level per block of depth).
		 */
		private static byte[] computeLight(
			LevelChunk chunk, int chunkX, int chunkZ, int sectionY,
			net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData packetLight) {
			byte[] light = new byte[4096];
			net.minecraft.world.level.chunk.DataLayer sky = packetLight == null ? null
				: layerFor(packetLight.getSkyYMask(), packetLight.getSkyUpdates(), chunk, sectionY);
			net.minecraft.world.level.chunk.DataLayer block = packetLight == null ? null
				: layerFor(packetLight.getBlockYMask(), packetLight.getBlockUpdates(), chunk, sectionY);

			if (packetLight == null) {
				// No real light for this chunk: approximate sky from the heightmap, no block light.
				int baseY = sectionY << 4;
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int topLitY = chunk.getHeight(LIGHT_HEIGHTMAP, x, z) - 1;
						for (int y = 0; y < 16; y++) {
							int skyLight = 15 - Math.max(0, topLitY - (baseY + y));
							light[(y << 8) | (z << 4) | x] = (byte) Math.max(0, skyLight);
						}
					}
				}
				return light;
			}

			// Real light. Block light a section didn't send is 0. Sky light a section didn't send follows the
			// vanilla client's rule (SkyLightSectionStorage.getLightValue): the server omits sky data for every
			// section above the chunk's highest non-empty one, and the client reads such a section as the bottom
			// layer of the nearest section above it that has data, or full sky when none does. Reading it as 0
			// darkened every face whose air in front fell in such a section - typically across a chunk border
			// into a lower neighbour - and coarse LOD cells, which average that air, went dark with distance.
			boolean hasSkyLight = chunk.getLevel().dimensionType().hasSkyLight();
			net.minecraft.world.level.chunk.DataLayer skyAbove = null;
			boolean fullSkyAbove = false;
			if (hasSkyLight && sky == null && !packetLight.getEmptySkyYMask().get(bitFor(chunk, sectionY))) {
				int above = nextSkySectionAbove(packetLight, chunk, sectionY);
				if (above == Integer.MIN_VALUE) {
					fullSkyAbove = true;
				} else {
					skyAbove = layerFor(packetLight.getSkyYMask(), packetLight.getSkyUpdates(), chunk, above);
				}
			}
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						int skyLight;
						if (!hasSkyLight) {
							skyLight = 0;
						} else if (sky != null) {
							skyLight = sky.get(x, y, z);
						} else if (fullSkyAbove) {
							skyLight = 15;
						} else {
							skyLight = skyAbove == null ? 0 : skyAbove.get(x, 0, z);
						}
						int blockLight = block == null ? 0 : block.get(x, y, z);
						light[(y << 8) | (z << 4) | x] = (byte) ((blockLight << 4) | skyLight);
					}
				}
			}
			return light;
		}

		/** Light masks span one section below the world to one above, so bit 0 is {@code minSectionY - 1}. */
		private static int bitFor(LevelChunk chunk, int sectionY) {
			return sectionY - (VersionHelper.INSTANCE.minSectionY(chunk) - 1);
		}

		/**
		 * The nearest section above {@code sectionY} that the packet carries sky light for (data or all-zero), or
		 * {@link Integer#MIN_VALUE} if none does, which the vanilla client reads as open sky.
		 */
		private static int nextSkySectionAbove(
			net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData packetLight, LevelChunk chunk, int sectionY) {
			java.util.BitSet present = (java.util.BitSet) packetLight.getSkyYMask().clone();
			present.or(packetLight.getEmptySkyYMask());
			int next = present.nextSetBit(bitFor(chunk, sectionY) + 1);
			return next < 0 ? Integer.MIN_VALUE : sectionY + (next - bitFor(chunk, sectionY));
		}

		/**
		 * Reconstructs the {@code DataLayer} for {@code sectionY} from a light packet's mask + updates list, or
		 * {@code null} if that section carries no data. Light data spans one section below the world to one
		 * above; the updates list holds a {@code byte[]} only for each set mask bit, in bit order, so the list
		 * index is the number of set bits before this section's bit.
		 */
		private static net.minecraft.world.level.chunk.DataLayer layerFor(
			java.util.BitSet mask, java.util.List<byte[]> updates, LevelChunk chunk, int sectionY) {
			int bitIndex = bitFor(chunk, sectionY);
			if (bitIndex < 0 || !mask.get(bitIndex)) {
				return null;
			}
			int listIndex = mask.get(0, bitIndex).cardinality();
			if (listIndex >= updates.size()) {
				return null;
			}
			return new net.minecraft.world.level.chunk.DataLayer(updates.get(listIndex));
		}

		private static void ingest(LevelChunk chunk) {
			try {
				me.cortex.voxy.client.core.VoxyRenderSystem system =
					(me.cortex.voxy.client.core.VoxyRenderSystem) VersionHelper.INSTANCE.voxyRenderSystemNullable();
				me.cortex.voxy.common.world.WorldEngine engine = system == null ? null : system.getEngine();
				if (engine == null) {
					return;
				}
				me.cortex.voxy.common.world.other.Mapper mapper = engine.getMapper();
				long packedPos = VersionHelper.INSTANCE.packChunkPos(chunk.getPos());
				int chunkX = net.minecraft.world.level.ChunkPos.getX(packedPos);
				int chunkZ = net.minecraft.world.level.ChunkPos.getZ(packedPos);
				int minSectionY = VersionHelper.INSTANCE.minSectionY(chunk);
				net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
				net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData packetLight =
					FarChunkLightCapture.lightFor(chunkX, chunkZ);

				// Replicate Voxy's own per-section ingest (convert -> mip -> insertUpdate) at the exact section
				// coordinates it uses (chunkX, sectionY, chunkZ), but feed our own light so the write actually
				// lands. insertUpdate propagates the change up the LOD mip pyramid, same as a live edit.
				for (int i = 0; i < sections.length; i++) {
					net.minecraft.world.level.chunk.LevelChunkSection section = sections[i];
					int sectionY = minSectionY + i;
					me.cortex.voxy.common.voxelization.VoxelizedSection voxelized =
						me.cortex.voxy.common.voxelization.VoxelizedSection.createEmpty().setPosition(chunkX, sectionY, chunkZ);
					// All-air sections go through the same conversion with their light: they hold the sky light that
					// the faces of the blocks below and beside them are lit by. (zero() would store it as 0.)
					byte[] sectionLight = computeLight(chunk, chunkX, chunkZ, sectionY, packetLight);
					me.cortex.voxy.common.voxelization.ILightingSupplier lighting =
						(x, y, z) -> sectionLight[(y << 8) | (z << 4) | x];
					me.cortex.voxy.common.voxelization.VoxelizedSection converted =
						me.cortex.voxy.common.voxelization.WorldConversionFactory.convert(
							voxelized, mapper, section.getStates(), section.getBiomes(), lighting);
					me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper.mipSection(converted, mapper);
					me.cortex.voxy.common.world.WorldUpdater.insertUpdate(engine, converted);
				}
			} catch (Throwable throwable) {
				// Voxy not initialised yet, or its internals moved - never let ingestion break chunk receipt.
			}
		}
	}
}
