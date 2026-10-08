package dev.alfxyz.leoneclient;

import dev.alfxyz.leoneclient.effect.CombatTagEffect;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/** Registers the content Leone Client adds to the game: the combat tag status effect. */
public class LeoneClientMod implements ModInitializer {
	public static final String MOD_ID = "leoneclient";
	public static Holder<MobEffect> combatTag;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		combatTag = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("combat_tag"), new CombatTagEffect());
	}
}
