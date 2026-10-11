package dev.alfxyz.leoneclient.mixin;

import java.util.OptionalInt;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The synced id of the entity a rocket is boosting, which the game only reads privately. */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketAccessor {
	@Accessor("DATA_ATTACHED_TO_TARGET")
	static EntityDataAccessor<OptionalInt> leone$attachedTo() {
		throw new AssertionError();
	}
}
