package net.killey.tornadophysics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.killey.tornadophysics.Config;
import weather2.weathersystem.wind.WindManager;

@Mixin(WindManager.class)
public class WindManagerMixin {

    @Shadow public float windAngleGust;
    @Shadow public float windAngleGlobal;

    @Inject(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lweather2/weathersystem/wind/WindManager;setWindTimeGust(I)V"
            ),
            remap = false
    )
    private void injectCustomGustAngle(CallbackInfo ci) {

        float minGust = Config.WIND_GUST_ANGLE_MIN.get().floatValue();
        float maxGust = Config.WIND_GUST_ANGLE_MAX.get().floatValue();

        float randomOffset = minGust + (float) Math.random() * (maxGust - minGust);
        this.windAngleGust = this.windAngleGlobal + randomOffset;
    }
}