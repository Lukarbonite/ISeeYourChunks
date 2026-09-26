package com.lukarbonite.iseeyourchunks.client.render;

import com.lukarbonite.iseeyourchunks.client.compat.IrisCompat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;

/**
 * Renders far managed entities with the same fog Voxy gives its LOD terrain, so a distant player is hazed exactly
 * like the ground they stand on instead of being painted solid fog colour.
 *
 * <p><b>The mismatch.</b> Far entities go through vanilla's entity shaders, which apply vanilla <em>environmental</em>
 * fog linearly with distance and saturate to 100% well before the distances this mod draws at. Voxy composites its
 * LOD with the same fog but <em>caps</em> it at the value it has at {@code D_cap = max(vanillaRenderDistance, 320) * sqrt(3)}
 * blocks, so past {@code D_cap} terrain always keeps the same, partial fog.
 *
 * <p><b>The fix.</b> Entities past {@code D_cap} are submitted into a separate {@link SubmitNodeStorage}, prepared by a
 * second {@link FeatureRenderDispatcher} (the dispatcher owns a single reusable frame, so vanilla's cannot prepare
 * two), and executed inside vanilla's main pass right after each matching vanilla phase, under their own fog buffer.
 * That buffer reproduces Voxy's capped fog with one uniform for every such entity regardless of distance: the
 * environmental range is collapsed to {@code [0, 0]} so the shader's fog factor is 1 everywhere, and the fog colour's
 * alpha - which the shader multiplies that factor by - is set to Voxy's cap. Entities nearer than {@code D_cap} stay in
 * vanilla's pass, where vanilla's uncapped fog already equals Voxy's.
 *
 * <p>The fog inputs come from the frame's final {@link FogData} (after Voxy's own fog hook), so every Voxy fog mode
 * is followed automatically: when a mode removes vanilla environmental fog the cap evaluates to 0 and far entities are
 * unfogged. With an Iris shader pack active the pack owns fog, so far entities stay in vanilla's pass untouched.
 */
public final class FarEntityFogPass {
	/** Fog ranges at or past this are treated as "no fog" (Voxy writes 1e8 when it removes environmental fog). */
	private static final float FOG_DISABLED_THRESHOLD = 1.0E7F;
	/** Voxy's cap reference: {@code max(vanillaRenderDistance, 320) * sqrt(3)}, see NormalRenderPipeline.finish. */
	private static final double VOXY_FOG_CAP_MIN_BLOCKS = 320.0D;
	private static final double SQRT_3 = Math.sqrt(3.0D);

	/** Execution points, matching vanilla's main-pass phases the far frame is run after. */
	public enum Phase { SOLID, TRANSLUCENT, OUTLINE, TRANSLUCENT_AFTER_TERRAIN }

	/** Initial staging size for our own vertex buffer; it grows on demand, and far entities are few. */
	private static final int STAGING_BUFFER_BYTES = 262_144;

	private static FeatureRenderDispatcher dispatcher;
	/** Our dispatcher's own vertex buffer; vanilla's shared one is already uploaded when we prepare. */
	private static StagedVertexBuffer stagedBuffer;
	private static boolean constructingDispatcher;
	private static final SubmitNodeStorage STORAGE = new SubmitNodeStorage();
	private static boolean hasSubmissions;
	private static FeatureRenderDispatcher.PreparedFrame preparedFrame;

	private static MappableRingBuffer fogBuffer;
	private static GpuBufferSlice fogSlice;

	private static final Vector4f FOG_COLOR = new Vector4f();
	private static float environmentalStart = Float.MAX_VALUE;
	private static float environmentalEnd = Float.MAX_VALUE;
	private static float skyEnd = Float.MAX_VALUE;
	private static float cloudEnd = Float.MAX_VALUE;

	private FarEntityFogPass() {
	}

	/** Marks that the next dispatcher constructed is ours, so it is handed {@link #stagedBufferForDispatcherUnderConstruction}. */
	public static void beginDispatcherConstruction() {
		constructingDispatcher = true;
	}

	public static void endDispatcherConstruction() {
		constructingDispatcher = false;
	}

	/** Our own staged vertex buffer while our dispatcher is being built, otherwise {@code null} (vanilla's shared one). */
	public static StagedVertexBuffer stagedBufferForDispatcherUnderConstruction() {
		if (!constructingDispatcher) {
			return null;
		}
		if (stagedBuffer == null) {
			stagedBuffer = new StagedVertexBuffer(() -> "ISYC far entities", STAGING_BUFFER_BYTES);
		}
		return stagedBuffer;
	}

	/** Ends our staged buffer's frame; called right after vanilla's {@code RenderBuffers.endFrame}. */
	public static void endStagedFrame() {
		if (stagedBuffer != null) {
			stagedBuffer.endFrame();
		}
	}

	/** Our own dispatcher, built alongside vanilla's (see GameRendererMixin). */
	public static void setDispatcher(FeatureRenderDispatcher farDispatcher) {
		dispatcher = farDispatcher;
	}

	/** Records the frame's final fog; called from {@code FogRenderer.updateBuffer}. */
	public static void captureFog(FogData fogData) {
		if (fogData.color != null) {
			FOG_COLOR.set(fogData.color);
		}
		environmentalStart = fogData.environmentalStart;
		environmentalEnd = fogData.environmentalEnd;
		skyEnd = fogData.skyEnd;
		cloudEnd = fogData.cloudEnd;
	}

	/** Distance past which Voxy's LOD fog stops increasing; far entities beyond it get that same capped fog. */
	public static double fogCapDistance() {
		double vanillaRenderDistanceBlocks = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0D;
		return Math.max(vanillaRenderDistanceBlocks, VOXY_FOG_CAP_MIN_BLOCKS) * SQRT_3;
	}

	/**
	 * The collector a far entity at {@code distanceBlocks} from the camera should be submitted into, or {@code null}
	 * to keep vanilla's (nearer than the fog cap, no second dispatcher, or a shader pack owns fog).
	 */
	public static SubmitNodeCollector collectorFor(double distanceBlocks) {
		if (dispatcher == null || distanceBlocks < fogCapDistance() || IrisCompat.shaderPackInUse()) {
			return null;
		}
		hasSubmissions = true;
		return STORAGE;
	}

	/** Prepares this frame's far entities alongside vanilla's frame; drains our storage like vanilla's. */
	public static void prepare() {
		if (!hasSubmissions || dispatcher == null) {
			return;
		}
		hasSubmissions = false;
		preparedFrame = dispatcher.prepareFrame(STORAGE);
		writeFogBuffer();
	}

	/** Whether our frame has glowing entities, so vanilla sets up its outline pass even if it has none itself. */
	public static boolean hasAnyOutline() {
		return preparedFrame != null && preparedFrame.hasAnyOutline();
	}

	/** Runs our frame's {@code phase} under the far-entity fog, then restores vanilla's world fog. */
	public static void execute(Phase phase, GpuBufferSlice worldFog) {
		if (preparedFrame == null || fogSlice == null) {
			return;
		}
		RenderSystem.setShaderFog(fogSlice);
		try {
			switch (phase) {
				case SOLID -> preparedFrame.executeSolid();
				case TRANSLUCENT -> preparedFrame.executeTranslucent();
				case OUTLINE -> preparedFrame.executeOutline();
				case TRANSLUCENT_AFTER_TERRAIN -> preparedFrame.executeTranslucentAfterTerrain();
			}
		} finally {
			RenderSystem.setShaderFog(worldFog);
		}
	}

	/** Releases this frame's prepared data and advances the fog ring buffer, after vanilla closes its frame. */
	public static void endFrame() {
		if (preparedFrame != null) {
			preparedFrame.close();
			preparedFrame = null;
		}
		if (fogSlice != null) {
			fogBuffer.rotate();
			fogSlice = null;
		}
	}

	/** Frees GPU resources; called when the owning GameRenderer closes. */
	public static void close() {
		endFrame();
		if (fogBuffer != null) {
			fogBuffer.close();
			fogBuffer = null;
		}
		if (dispatcher != null) {
			dispatcher.close();
			dispatcher = null;
		}
		if (stagedBuffer != null) {
			stagedBuffer.close();
			stagedBuffer = null;
		}
	}

	/**
	 * Voxy's fog cap for the current frame: the linear environmental fog value at {@link #fogCapDistance()}, clamped
	 * to [0, 1], or 0 when environmental fog is off (Voxy's own guard is {@code |end - start| > 1}).
	 */
	private static float voxyFogCap() {
		float start = environmentalStart;
		float end = environmentalEnd;
		if (end >= FOG_DISABLED_THRESHOLD || Math.abs(end - start) <= 1.0F) {
			return 0.0F;
		}
		float scale = 1.0F / (end - start);
		float cap = (float) fogCapDistance() * scale - start * scale;
		return Math.clamp(cap, 0.0F, 1.0F);
	}

	private static void writeFogBuffer() {
		if (fogBuffer == null) {
			fogBuffer = new MappableRingBuffer(() -> "ISYC far entity fog UBO",
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, FogRenderer.FOG_UBO_SIZE);
		}
		GpuBuffer buffer = fogBuffer.currentBuffer();
		try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
			// Same std140 layout as FogRenderer: colour, env start/end, render-distance start/end, sky end, cloud end.
			// env [0, 0] makes the fog factor 1 at any distance; alpha = cap then applies Voxy's capped fog.
			Std140Builder.intoBuffer(view.data())
				.putVec4(new Vector4f(FOG_COLOR.x, FOG_COLOR.y, FOG_COLOR.z, FOG_COLOR.w * voxyFogCap()))
				.putFloat(0.0F)
				.putFloat(0.0F)
				.putFloat(Float.MAX_VALUE)
				.putFloat(Float.MAX_VALUE)
				.putFloat(skyEnd)
				.putFloat(cloudEnd);
		}
		fogSlice = buffer.slice(0, FogRenderer.FOG_UBO_SIZE);
	}
}
