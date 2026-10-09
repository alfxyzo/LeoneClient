package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.features.ItemCooldowns;
import dev.alfxyz.leoneclient.module.Modules;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Cooldowns: an item on cooldown is shaded from the bottom as time runs out, with the seconds left on it. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemDecorationsMixin {
	@Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
	private void leoneclient$cooldown(Font font, ItemStack stack, int x, int y, @Nullable String count, CallbackInfo ci) {
		ItemCooldowns.Tracker t = Modules.ITEM_COOLDOWNS.runningFor(stack);
		if (t == null) return;
		GuiGraphicsExtractor g = (GuiGraphicsExtractor) (Object) this;
		int filled = Math.max(1, Math.round(16 * t.remaining()));
		g.fill(x, y + 16 - filled, x + 16, y + 16, 0x80FFFFFF);
		long left = t.left();
		long secs = (left + 999) / 1000;
		String label = left < 10_000 ? String.format(Locale.ROOT, "%.1f", left / 1000.0)
			: secs < 60 ? String.valueOf(secs) : String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
		g.pose().pushMatrix();
		g.pose().translate(x + 8f, y + 4.5f);
		g.pose().scale(0.68f, 0.68f);
		g.text(font, label, -font.width(label) / 2, 0, 0xFFFFFFFF, true);
		g.pose().popMatrix();
	}
}
