package io.github.developergr3y.ihtf.mixin;

import io.github.developergr3y.ihtf.features.Spotlight;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diamond Spotlight: while it's on, the HUD (and everything other mods add to it) is skipped and only the dimmed
 * roulette is drawn. The `//?` comments are Stonecutter switches: 26.2 moved the HUD out of Gui into its own class.
 */
//? if >=26.2 {
@Mixin(net.minecraft.client.gui.Hud.class)
//?} else {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?}
public class HudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void ihtf$spotlight(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Spotlight.getActive()) return;
        Spotlight.render(graphics, deltaTracker);
        ci.cancel();
    }
}
