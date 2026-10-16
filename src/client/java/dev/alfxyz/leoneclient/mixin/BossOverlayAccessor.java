package dev.alfxyz.leoneclient.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** HUD Tailor: how many boss bars are showing, to size their box in the editor. */
@Mixin(BossHealthOverlay.class)
public interface BossOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> leone$events();
}
