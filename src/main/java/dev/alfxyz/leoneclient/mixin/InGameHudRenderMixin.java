package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import dev.alfxyz.leoneclient.LeoneClientConfig;
import dev.alfxyz.leoneclient.utils.ActionBarStore;
import net.minecraft.client.render.RenderTickCounter;

@Mixin(InGameHud.class)
public class InGameHudRenderMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void renderCombatActionBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!dev.alfxyz.leoneclient.LeoneClientConfig.isActionBarEnabled()) return;
        boolean merge = dev.alfxyz.leoneclient.LeoneClientConfig.getMergeActionBars();
        if (merge) {
            Text regular = ActionBarStore.getRegularActionBar();
            Text combat = ActionBarStore.getCombatActionBar();
            Text merged = null;
            if (regular != null && combat != null) {
                merged = Text.literal(regular.getString() + " | " + combat.getString());
            } else if (regular != null) {
                merged = regular;
            } else if (combat != null) {
                merged = combat;
            }
            if (merged != null) {
                int width = context.getScaledWindowWidth();
                int y = LeoneClientConfig.getRegularActionBarY();
                int x = width / 2;
                // Use a unique color for merged bar (e.g., teal)
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, merged, x, y, 0x00AAAA);
            }
        } else {
            Text combat = ActionBarStore.getCombatActionBar();
            if (combat != null) {
                int width = context.getScaledWindowWidth();
                int y = LeoneClientConfig.getCombatActionBarY();
                int x = width / 2;
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, combat, x, y, 0xFF5555);
            }
            Text regular = ActionBarStore.getRegularActionBar();
            // Always show regular bar if present, even during combat
            if (regular != null) {
                int width = context.getScaledWindowWidth();
                int y = LeoneClientConfig.getRegularActionBarY();
                int x = width / 2;
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, regular, x, y, 0xFFAA00);
            }
        }
    }
} 