package dev.alfxyz.leoneclient.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Shown on the client while LeoneMC's combat tag is counting down. It has no effect of its own. */
public class CombatTagEffect extends MobEffect {
	public CombatTagEffect() {
		super(MobEffectCategory.HARMFUL, 0xCC3333);
	}
}
