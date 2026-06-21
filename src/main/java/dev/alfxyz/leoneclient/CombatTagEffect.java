package dev.alfxyz.leoneclient;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class CombatTagEffect extends StatusEffect {
    protected CombatTagEffect() {
        super(StatusEffectCategory.HARMFUL, 0xCC3333);
    }
}
