package net.killey.tornadophysics.mixin;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import weather2.util.WindReader;
import weather2.blockentity.WindVaneBlockEntity;

import dev.ryanhcode.sable.companion.SableCompanion;

@Mixin(WindVaneBlockEntity.class)
public abstract class WindVaneBlockEntityMixin {

    @Shadow private boolean isOutsideCached;
    @Shadow private float smoothAngle;
    @Shadow private float smoothAnglePrev;
    @Shadow private float smoothAngleRotationalVelAccel;

    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void injectAirshipWindVane(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, CallbackInfo ci) {

        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
        if (subLevel == null) return;

        if (level.isClientSide) {
            org.joml.Vector3dc globalPos = subLevel.logicalPose().position();
            BlockPos globalBlockPos = BlockPos.containing(globalPos.x(), globalPos.y(), globalPos.z());
            Vec3 globalVec = new Vec3(globalPos.x(), globalPos.y(), globalPos.z());

            if (level.getGameTime() % 40L == 0L) {
                this.isOutsideCached = true;
            }

            if (this.isOutsideCached) {
                float globalWindAngle = WindReader.getWindAngle(level, globalVec);
                float windSpeed = WindReader.getWindSpeed(level, globalBlockPos, 1.0F);

                org.joml.Vector3d forward = new org.joml.Vector3d(0, 0, 1);
                forward.rotate(subLevel.logicalPose().orientation());
                float shipYaw = (float) Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
                float targetAngle = globalWindAngle - shipYaw;

                if (this.smoothAngle > 180.0F) this.smoothAngle -= 360.0F;
                if (this.smoothAngle < -180.0F) this.smoothAngle += 360.0F;
                if (this.smoothAnglePrev > 180.0F) this.smoothAnglePrev -= 360.0F;
                if (this.smoothAnglePrev < -180.0F) this.smoothAnglePrev += 360.0F;

                this.smoothAnglePrev = this.smoothAngle;
                float bestMove = Mth.wrapDegrees(targetAngle - this.smoothAngle);

                if (Math.abs(bestMove) < 180.0F) {
                    if (bestMove > 0.0F) {
                        this.smoothAngleRotationalVelAccel = (float)((double)this.smoothAngleRotationalVelAccel - (double)windSpeed * 0.4);
                    }
                    if (bestMove < 0.0F) {
                        this.smoothAngleRotationalVelAccel = (float)((double)this.smoothAngleRotationalVelAccel + (double)windSpeed * 0.4);
                    }
                    if ((double)this.smoothAngleRotationalVelAccel > 0.3 || (double)this.smoothAngleRotationalVelAccel < -0.3) {
                        this.smoothAngle += this.smoothAngleRotationalVelAccel;
                    }
                    this.smoothAngleRotationalVelAccel *= 0.96F;
                }
            }
        }
        ci.cancel();
    }
}