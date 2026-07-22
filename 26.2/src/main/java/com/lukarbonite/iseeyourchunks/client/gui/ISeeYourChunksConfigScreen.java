package com.lukarbonite.iseeyourchunks.client.gui;

import com.lukarbonite.iseeyourchunks.client.ClientEntityVisibility;
import com.lukarbonite.iseeyourchunks.client.ISeeYourChunksFabricClient;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfig;
import com.lukarbonite.iseeyourchunks.config.ISeeYourChunksConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Client settings screen. Edits a detached copy and only commits it on Done, so backing out with Cancel
 * or Escape leaves the live config untouched.
 */
public final class ISeeYourChunksConfigScreen extends Screen {
	private static final Component TITLE = Component.translatable("screen.i_see_your_chunks.title");
	private static final Component SUBTITLE = Component.translatable("screen.i_see_your_chunks.subtitle");

	private static final int ROW_WIDTH = 240;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_SPACING = 24;
	private static final int FIRST_ROW_Y = 68;
	private static final int FOOTER_BUTTON_WIDTH = 115;
	private static final int FOOTER_GAP = 5;

	private final Screen parent;
	private final ISeeYourChunksConfig draft;

	public ISeeYourChunksConfigScreen(Screen parent) {
		super(TITLE);
		this.parent = parent;
		this.draft = ISeeYourChunksConfigManager.getConfig().copy();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int left = centerX - ROW_WIDTH / 2;
		int y = FIRST_ROW_Y;

		this.addRenderableWidget(toggle(left, y, "enabled", this.draft.enabled(), this.draft::setEnabled));
		y += ROW_SPACING;
		this.addRenderableWidget(toggle(left, y, "remote_players",
			this.draft.renderRemotePlayers(), this.draft::setRenderRemotePlayers));
		y += ROW_SPACING;
		this.addRenderableWidget(toggle(left, y, "remote_entities",
			this.draft.renderRemoteEntities(), this.draft::setRenderRemoteEntities));

		y += ROW_SPACING + 4;
		this.addRenderableWidget(new VisibilityDistanceSlider(left, y, this.draft));
		y += ROW_SPACING;
		this.addRenderableWidget(new ChunkRenderCountSlider(left, y, this.draft,
			ISeeYourChunksFabricClient.serverChunkRenderCap()));

		int footerY = this.height - 28;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.commit())
			.bounds(centerX - FOOTER_BUTTON_WIDTH - FOOTER_GAP, footerY, FOOTER_BUTTON_WIDTH, ROW_HEIGHT).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
			.bounds(centerX + FOOTER_GAP, footerY, FOOTER_BUTTON_WIDTH, ROW_HEIGHT).build());
	}

	private CycleButton<Boolean> toggle(int x, int y, String key, boolean initial, java.util.function.Consumer<Boolean> sink) {
		return CycleButton.onOffBuilder(initial)
			.create(x, y, ROW_WIDTH, ROW_HEIGHT,
				Component.translatable("option.i_see_your_chunks." + key),
				(button, value) -> sink.accept(value));
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		this.extractMenuBackground(context);
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
		context.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
		context.centeredText(this.font, SUBTITLE, this.width / 2, 34, 0xAAAAAA);
	}

	private void commit() {
		ISeeYourChunksConfigManager.setConfig(this.draft);
		// The index caches per-entity render decisions that the new settings may invalidate.
		ClientEntityVisibility.invalidate();
		// Re-announce, so the server adjusts what it streams without waiting for a reconnect.
		ISeeYourChunksFabricClient.sendHello();
		// Resize the client chunk storage to match: a raised terrain count needs a wider buffer now, not
		// only after the next world reload, or its outer chunks would be rejected as out of range.
		ISeeYourChunksFabricClient.reapplyStorageRadius();
		this.onClose();
	}

	/**
	 * Distance slider over a logarithmic scale.
	 *
	 * <p>Linear travel would waste most of the bar on distances nobody picks - the useful range spans
	 * two orders of magnitude, and a player choosing between 64 and 128 blocks cares about the same
	 * <em>proportional</em> step as one choosing between 1024 and 2048. The top of the track snaps to
	 * the unlimited sentinel, since the raw ceiling is a coordinate limit rather than a real choice.
	 */
	private static final class VisibilityDistanceSlider extends AbstractSliderButton {
		/** Fraction of the track, at the very top, reserved for the unlimited setting. */
		private static final double UNLIMITED_THRESHOLD = 0.999D;

		private static final double MIN_LOG = Math.log(ISeeYourChunksConfig.MIN_VISIBILITY_DISTANCE_BLOCKS);
		private static final double MAX_LOG = Math.log(ISeeYourChunksConfig.MAX_VISIBILITY_DISTANCE_BLOCKS);

		private final ISeeYourChunksConfig target;
		private int blocks;

		private VisibilityDistanceSlider(int x, int y, ISeeYourChunksConfig target) {
			super(x, y, ROW_WIDTH, ROW_HEIGHT, CommonComponents.EMPTY, trackPositionOf(target.visibilityDistanceBlocks()));
			this.target = target;
			this.blocks = ISeeYourChunksConfig.clampVisibilityDistanceBlocks(target.visibilityDistanceBlocks());
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable(
				"option.i_see_your_chunks.visibility_distance", describe(this.blocks)));
		}

		@Override
		protected void applyValue() {
			this.blocks = blocksAt(this.value);
			this.target.setVisibilityDistanceBlocks(this.blocks);
		}

		/** Where a given distance sits on the track, in 0..1. Inverse of {@link #blocksAt}. */
		private static double trackPositionOf(int blocks) {
			if (blocks >= ISeeYourChunksConfig.MAX_VISIBILITY_DISTANCE_BLOCKS) {
				return 1.0D;
			}

			double log = Math.log(ISeeYourChunksConfig.clampVisibilityDistanceBlocks(blocks));
			return (log - MIN_LOG) / (MAX_LOG - MIN_LOG);
		}

		/** Distance for a track position in 0..1. Inverse of {@link #trackPositionOf}. */
		private static int blocksAt(double position) {
			if (position >= UNLIMITED_THRESHOLD) {
				return ISeeYourChunksConfig.MAX_VISIBILITY_DISTANCE_BLOCKS;
			}

			double blocks = Math.exp(Mth.lerp(position, MIN_LOG, MAX_LOG));
			return ISeeYourChunksConfig.clampVisibilityDistanceBlocks((int) Math.round(blocks));
		}

		private static Component describe(int blocks) {
			if (blocks >= ISeeYourChunksConfig.MAX_VISIBILITY_DISTANCE_BLOCKS) {
				return Component.translatable("option.i_see_your_chunks.visibility_distance.infinite");
			}

			int chunks = Math.max(1, Mth.ceil(blocks / 16.0D));
			return Component.translatable("option.i_see_your_chunks.visibility_distance.value",
				String.format(Locale.ROOT, "%,d", chunks),
				String.format(Locale.ROOT, "%,d", blocks));
		}
	}

	/**
	 * Selects how many chunks of terrain surround each viewed player.
	 *
	 * <p>The track is logarithmic so the low end - where a single chunk is a meaningful change - gets most
	 * of the travel, while the high hundreds compress. The bottom of the track is a small reserved band
	 * for the "none" setting, so 0 is a deliberate stop rather than something you skip past.
	 */
	private static final class ChunkRenderCountSlider extends AbstractSliderButton {
		/** Fraction of the track, at the very bottom, that maps to a count of 0. */
		private static final double NONE_BAND = 0.05D;

		private static final double MIN_LOG = Math.log(1.0D);

		private final ISeeYourChunksConfig target;
		/** Upper bound for this slider: the connected server's cap, or the client maximum when unknown. */
		private final int maxCount;
		private final double maxLog;
		private int count;

		private ChunkRenderCountSlider(int x, int y, ISeeYourChunksConfig target, int maxCount) {
			super(x, y, ROW_WIDTH, ROW_HEIGHT, CommonComponents.EMPTY,
				trackPositionOf(Math.min(target.chunkRenderCount(), maxCount), maxCount));
			this.target = target;
			this.maxCount = Math.max(1, maxCount);
			this.maxLog = Math.log(this.maxCount);
			this.count = Math.min(ISeeYourChunksConfig.clampChunkRenderCount(target.chunkRenderCount()), this.maxCount);
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("option.i_see_your_chunks.chunk_render", describe(this.count)));
		}

		@Override
		protected void applyValue() {
			this.count = countAt(this.value);
			this.target.setChunkRenderCount(this.count);
		}

		private static double trackPositionOf(int count, int maxCount) {
			if (count <= 0) {
				return 0.0D;
			}

			double fraction = (Math.log(count) - MIN_LOG) / (Math.log(Math.max(1, maxCount)) - MIN_LOG);
			return NONE_BAND + fraction * (1.0D - NONE_BAND);
		}

		private int countAt(double position) {
			if (position < NONE_BAND) {
				return 0;
			}

			double fraction = (position - NONE_BAND) / (1.0D - NONE_BAND);
			double raw = Math.exp(Mth.lerp(fraction, MIN_LOG, this.maxLog));
			return Math.min(this.maxCount, ISeeYourChunksConfig.clampChunkRenderCount((int) Math.round(raw)));
		}

		private static Component describe(int count) {
			if (count <= 0) {
				return Component.translatable("option.i_see_your_chunks.chunk_render.none");
			}

			// A rounded side length, purely descriptive: it tells the player roughly how wide the patch is
			// without implying the selection is a literal square (it is a nearest-first disc).
			int side = Math.max(1, (int) Math.round(Math.sqrt(count)));
			return Component.translatable("option.i_see_your_chunks.chunk_render.value",
				String.format(Locale.ROOT, "%,d", count), side, side);
		}
	}
}
