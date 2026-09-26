package com.lukarbonite.iseeyourchunks.client.render;

import com.lukarbonite.iseeyourchunks.client.compat.IrisCompat;
import com.lukarbonite.iseeyourchunks.platform.PlatformHelper;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Gives entities the same fog the 1.21.1 Voxy fork puts on its LOD terrain, so a distant player is hazed exactly like
 * the ground they stand on. Voxy's fog itself is left untouched: past its fog end both fade fully to fog colour, and
 * players who want to see farther tune Voxy's fog down or off, which then applies to both alike.
 *
 * <p><b>The mismatch.</b> While the camera is in air the fork switches vanilla fog off (start/end = 1e9), so vanilla
 * draws every entity unfogged, and fogs its LOD itself in its final composite: {@code smoothstep(start, end, dist)}
 * over the fog shape's distance (start = vanilla's fog start, end = {@code sectionRenderDistance * 512}), optionally
 * reshaped by {@code fogDensity}, times {@code fogIntensity}, towards the vanilla fog colour.
 *
 * <p><b>The fix.</b> An entity that fog reaches is rendered into its own buffer source and drawn immediately under
 * vanilla's own fog uniforms, set so that vanilla's shader lands on exactly the fork's amount at that entity's distance:
 * start 0 and an end chosen by inverting {@code smoothstep}. Using real fog parameters, rather than a colour-alpha
 * trick, keeps shaders that fade by fog instead of tinting (enchantment glint, eyes) consistent with the body.
 * Glowing entities keep vanilla's outline path; with an Iris shader pack the pack owns fog.
 */
public final class FarEntityFog {
	/** Vanilla fog at or past this is "off"; the fork writes 1e9 while it owns fog. */
	private static final float FOG_DISABLED_THRESHOLD = 1.0E8F;
	/** At or above this fog amount the entity is drawn fully fogged (the end is pulled in to its near edge). */
	private static final float FULL_FOG = 0.999F;
	private static final int BUFFER_BYTES = 262_144;

	private static final boolean VOXY_LOADED = PlatformHelper.get().isModLoaded("voxy");
	private static MultiBufferSource.BufferSource bufferSource;

	private FarEntityFog() {
	}

	/**
	 * The distance Voxy measures fog by, from the camera to {@code entity}'s centre, in the current fog shape (the
	 * fork's composite uses the same shape and camera-relative position).
	 */
	public static float fogDistance(Entity entity, double cameraX, double cameraY, double cameraZ, float partialTick) {
		Vec3 position = entity.getPosition(partialTick);
		double dx = position.x - cameraX;
		double dy = position.y + entity.getBbHeight() * 0.5D - cameraY;
		double dz = position.z - cameraZ;
		return (float) (RenderSystem.getShaderFogShape() == FogShape.CYLINDER
			? Math.max(Math.sqrt(dx * dx + dz * dz), Math.abs(dy))
			: Math.sqrt(dx * dx + dy * dy + dz * dz));
	}

	/**
	 * The fork's fog amount at {@code distance}, in [0, 1], or 0 when this pass does not apply: no Voxy, Voxy not
	 * owning fog this frame (e.g. camera in a fluid, where vanilla fog already applies to entities), fog off, or a
	 * shader pack active.
	 */
	public static float fogAmount(float distance) {
		if (!VOXY_LOADED || RenderSystem.getShaderFogStart() < FOG_DISABLED_THRESHOLD || IrisCompat.shaderPackInUse()) {
			return 0.0F;
		}
		return Voxy.fogAmount(distance);
	}

	/** The buffer source a fogged entity is rendered into, then drawn by {@link #draw}. */
	public static MultiBufferSource.BufferSource bufferSource() {
		if (bufferSource == null) {
			bufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(BUFFER_BYTES));
		}
		return bufferSource;
	}

	/**
	 * Runs {@code render} into {@link #bufferSource()} and draws it under fog that equals {@code amount} at
	 * {@code distance}, restoring vanilla's fog afterwards. The fog is set before rendering because the shared buffer
	 * flushes early whenever the render type changes.
	 */
	public static void renderFogged(float amount, float distance, Entity entity, Runnable render) {
		float savedStart = RenderSystem.getShaderFogStart();
		float savedEnd = RenderSystem.getShaderFogEnd();
		float[] savedColor = RenderSystem.getShaderFogColor().clone();
		float[] fogColor = Voxy.fogColor();

		RenderSystem.setShaderFogStart(0.0F);
		RenderSystem.setShaderFogEnd(fogEndFor(amount, distance, entity));
		RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], 1.0F);
		try {
			render.run();
			bufferSource().endBatch();
		} finally {
			RenderSystem.setShaderFogStart(savedStart);
			RenderSystem.setShaderFogEnd(savedEnd);
			RenderSystem.setShaderFogColor(savedColor[0], savedColor[1], savedColor[2], savedColor[3]);
		}
	}

	/**
	 * The fog end, with start 0, at which vanilla's {@code smoothstep(0, end, distance)} equals {@code amount}: the
	 * inverse of smoothstep, {@code t = 0.5 - sin(asin(1 - 2 * amount) / 3)}, gives {@code end = distance / t}.
	 */
	private static float fogEndFor(float amount, float distance, Entity entity) {
		if (amount >= FULL_FOG) {
			// Fully fogged: end inside the entity's near edge, so every part of it is past the end.
			return Math.max(0.0F, distance - entity.getBbWidth() - entity.getBbHeight());
		}
		double t = 0.5D - Math.sin(Math.asin(1.0D - 2.0D * amount) / 3.0D);
		return (float) (distance / t);
	}

	/** All Voxy access, in a nested class so Voxy's types are only resolved when Voxy is installed. */
	private static final class Voxy {
		private static float fogAmount(float distance) {
			VoxyRenderSystem system = IGetVoxyRenderSystem.getNullable();
			VoxyConfig config = VoxyConfig.CONFIG;
			if (system == null || !config.useEnvironmentalFog || config.fogIntensity <= 0.0F) {
				return 0.0F;
			}
			float start = system.getCapturedFogStart();
			float end = system.getCapturedFogEnd();
			// Same guards as the fork's composite: no LOD at all when its fog ends inside vanilla's render
			// distance, and no fog when the range is degenerate.
			if (end < Minecraft.getInstance().gameRenderer.getRenderDistance() || Math.abs(end - start) <= 1.0F) {
				return 0.0F;
			}
			float t = Mth.clamp((distance - start) / (end - start), 0.0F, 1.0F);
			float lerp = t * t * (3.0F - 2.0F * t);
			if (config.fogDensity > 0.0F) {
				lerp = (float) ((Math.exp(config.fogDensity * lerp) - 1.0D) / (Math.exp(config.fogDensity) - 1.0D));
			}
			return Mth.clamp(lerp * config.fogIntensity, 0.0F, 1.0F);
		}

		private static float[] fogColor() {
			VoxyRenderSystem system = IGetVoxyRenderSystem.getNullable();
			return system != null ? system.getCapturedFogColor() : RenderSystem.getShaderFogColor();
		}
	}
}
