package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.LeoneClient;
import net.minecraft.scoreboard.Scoreboard;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Scoreboard.class)
public class ScoreboardMixin {
    @Redirect(
        method = "addTeam",
        at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"),
        require = 0
    )
    private void suppressExistingTeamWarning(Logger logger, String msg, Object arg) {
        if (!LeoneClient.onLeoneMC) {
            logger.warn(msg, arg);
        }
    }
}
