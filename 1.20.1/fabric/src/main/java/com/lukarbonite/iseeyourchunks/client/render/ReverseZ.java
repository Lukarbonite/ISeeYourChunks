package com.lukarbonite.iseeyourchunks.client.render;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.client.compat.IrisCompat;
import com.lukarbonite.iseeyourchunks.platform.PlatformHelper;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Renders 1.20.1's world pass with a reverse-Z depth buffer, the scheme MC 26.x and Voxy use natively, so distant
 * players and the LOD terrain around them depth-test against each other correctly.
 *
 * <p><b>Why.</b> 1.20.1 draws the world with standard depth: a near plane of 0.05 blocks mapped to 0 and the far plane
 * to 1. Depth at distance {@code d} is then about {@code 1 - 0.05 / d}, and a depth buffer only resolves steps of
 * about {@code 2^-24} near 1, so at 20,000 blocks one step spans roughly 480 blocks: a far player and all the terrain
 * within a few hundred blocks of it share one depth value, and the player wins every tie. Reversed, depth is about
 * {@code 0.05 / d}, close to 0, where a float buffer's steps are relative to the value: about a thousandth of a
 * block at 20,000.
 *
 * <p><b>How.</b> Only while {@code GameRenderer.renderLevel} runs (the GUI keeps standard depth): clipping is switched
 * to 0..1 ({@code glClipControl}), the projection is built with near and far swapped, and every depth comparison,
 * depth clear value and polygon offset vanilla (and Sodium, which goes through the same state) issues is mirrored at
 * the GL call. The main depth buffer is 32-bit float (see RenderTargetMixin). The Voxy fork already supports
 * reverse-Z, left over from upstream Voxy which runs this way on 26.x, so its render properties are built with it
 * enabled and its own passes follow suit.
 *
 * <p>Off with an Iris shader pack (packs assume standard depth), with Fabulous graphics (its transparency shader
 * sorts layers by raw depth), without clip-control support, or without Voxy. Voxy fixes its mode when it builds its
 * render system, so the world pass only reverses while Voxy's current render system was built reversed too.
 */
public final class ReverseZ {
	private static final int GL_LESS = 0x0201;
	private static final int GL_LEQUAL = 0x0203;
	private static final int GL_GREATER = 0x0204;
	private static final int GL_GEQUAL = 0x0206;
	private static final int GL_LOWER_LEFT = 0x8CA1;
	private static final int GL_NEGATIVE_ONE_TO_ONE = 0x935E;
	private static final int GL_ZERO_TO_ONE = 0x935F;
	private static final int GL_DEPTH = 0x1801;

	private static final boolean VOXY_LOADED = PlatformHelper.get().isModLoaded("voxy");

	private static boolean active;
	/** Whether Voxy's current render system was built reversed; {@code false} until one is built. */
	private static boolean voxyReversed;
	private static Boolean clipControlSupported;

	// The logical (standard-depth) state vanilla last set, tracked at the GL calls since GlStateManager only issues
	// a call when its cached value changes. GL defaults, which is also what vanilla initialises its cache to.
	private static int depthFunc = GL_LESS;
	private static double clearDepth = 1.0D;
	private static float polygonOffsetFactor;
	private static float polygonOffsetUnits;

	private ReverseZ() {
	}

	public static boolean isActive() {
		return active;
	}

	/** The reverse-Z decision for a Voxy render system being built now; remembered so the world pass matches it. */
	public static boolean decideForVoxy() {
		voxyReversed = wanted();
		ISeeYourChunks.LOGGER.info("Voxy render system built with {} depth.", voxyReversed ? "reverse-Z" : "standard");
		return voxyReversed;
	}

	/** Enters reverse-Z for the world pass; called at the head of {@code GameRenderer.renderLevel}. */
	public static void begin() {
		if (active || !voxyReversed || !wanted()) {
			return;
		}
		active = true;
		GL45C.glClipControl(GL_LOWER_LEFT, GL_ZERO_TO_ONE);
		GL11.glDepthFunc(mirror(depthFunc));
		GL11.glClearDepth(1.0D - clearDepth);
		GL11.glPolygonOffset(-polygonOffsetFactor, -polygonOffsetUnits);

		// The frame-start clear filled depth with standard "far" (1), which is "nearest" once reversed.
		RenderSystem.depthMask(true);
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		GL45C.glClearNamedFramebufferfv(main.frameBufferId, GL_DEPTH, 0, new float[] {0.0F});
	}

	/** Leaves reverse-Z, restoring vanilla's logical state; called when {@code GameRenderer.renderLevel} returns. */
	public static void end() {
		if (!active) {
			return;
		}
		active = false;
		GL45C.glClipControl(GL_LOWER_LEFT, GL_NEGATIVE_ONE_TO_ONE);
		GL11.glDepthFunc(depthFunc);
		GL11.glClearDepth(clearDepth);
		GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
	}

	/** Records vanilla's depth function and returns the one to issue. */
	public static int onDepthFunc(int func) {
		depthFunc = func;
		return active ? mirror(func) : func;
	}

	/** Records vanilla's depth clear value and returns the one to issue. */
	public static double onClearDepth(double depth) {
		clearDepth = depth;
		return active ? 1.0D - depth : depth;
	}

	public static void onPolygonOffset(float factor, float units) {
		polygonOffsetFactor = factor;
		polygonOffsetUnits = units;
	}

	/** A polygon offset towards the camera is negative in standard depth and positive when reversed. */
	public static float polygonOffsetSign() {
		return active ? -1.0F : 1.0F;
	}

	private static boolean wanted() {
		return VOXY_LOADED
			&& clipControlSupported()
			&& !Minecraft.useShaderTransparency()
			&& !IrisCompat.shaderPackInUse();
	}

	private static boolean clipControlSupported() {
		if (clipControlSupported == null) {
			GLCapabilities capabilities = GL.getCapabilities();
			clipControlSupported = capabilities.OpenGL45 || capabilities.GL_ARB_clip_control;
			if (!clipControlSupported) {
				ISeeYourChunks.LOGGER.warn("GL clip control is unavailable; distant players won't depth-test precisely against LOD terrain.");
			}
		}
		return clipControlSupported;
	}

	private static int mirror(int func) {
		return switch (func) {
			case GL_LESS -> GL_GREATER;
			case GL_LEQUAL -> GL_GEQUAL;
			case GL_GREATER -> GL_LESS;
			case GL_GEQUAL -> GL_LEQUAL;
			default -> func;
		};
	}
}
