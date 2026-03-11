package org.embeddedt.modernfix.common.mixin.perf.dynamic_dfu;

import net.minecraft.util.datafix.DataFixers;
import org.embeddedt.modernfix.dfu.DFUBlaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DataFixers.class)
public class DataFixersMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void setupMapBlasting(CallbackInfo ci) {
        DFUBlaster.blastMaps();
    }
}
