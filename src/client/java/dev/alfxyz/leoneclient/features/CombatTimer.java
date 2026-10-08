package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.LeoneClientMod;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** Mirrors LeoneMC's combat tag countdown as a status effect, so it sits with your other effects. */
public final class CombatTimer extends Module {
	public final Setting.Toggle safeSound = add(new Setting.Toggle("safe_sound", "Sound when safe", "ALERTS", false),
		"Plays a chime and shows a notification when your combat tag runs out.");
	private boolean wasTagged;

	public CombatTimer() {
		super("combat_timer", Category.COMBAT, "Combat Timer", Icons.TIMER, "Shows your combat tag countdown as a status effect.", true);
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.player == null) {
			wasTagged = false;
			return;
		}
		Holder<MobEffect> effect = LeoneClientMod.combatTag;
		boolean tagged = active() && ActionBars.tagged();
		if (!tagged) {
			if (mc.player.hasEffect(effect)) mc.player.removeEffect(effect);
			if (wasTagged && active() && safeSound.get() && ActionBars.remainingSeconds() <= 0.6f) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.7f));
				Notices.push("Out of combat", "Your combat tag has run out", 0x4ADE80, Icons.SHIELD);
			}
			wasTagged = false;
			return;
		}
		wasTagged = true;
		int ticks = Math.max(1, Math.round(ActionBars.remainingSeconds() * 20));
		MobEffectInstance current = mc.player.getEffect(effect);
		if (current == null || Math.abs(current.getDuration() - ticks) > 10) {
			mc.player.forceAddEffect(new MobEffectInstance(effect, ticks, 0, false, false, true), null);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.hasEffect(LeoneClientMod.combatTag)) mc.player.removeEffect(LeoneClientMod.combatTag);
	}

	@Override
	public String status() {
		return ActionBars.tagged() ? String.format(Locale.ROOT, "Tagged · %.1fs", ActionBars.remainingSeconds()) : null;
	}
}
