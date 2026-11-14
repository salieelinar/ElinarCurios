package elinarcurios.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.Config;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitDegasser;

/**
 * 彩蛋（默认关闭）：让净水厂七级净化单元「残留净化剂除气机净化单元」最终成功率恒为 100%。
 *
 * <p>
 * 原版 {@code calculateFinalSuccessChance()} 只有在当周期随机出来的 4 位控制信号所要求的
 * 惰性气体 / 超导熔液 / 中子素熔液 / 超冷却液全部按精确量投入时才返回 100，否则返回 0。
 * 这里在方法头直接短路成 100，使该单元只靠上一级水就能稳定产出七级净化水。
 *
 * <p>
 * 只影响这一台单元；开关在 {@code config/ElinarCurios.cfg} 的 {@code purificationTier78AlwaysSucceed}，
 * 默认 false（不改任何行为）。扫描器显示的成功率走同一个方法，因此也会一起变成 100%。
 */
@Mixin(value = MTEPurificationUnitDegasser.class, remap = false)
public abstract class MixinMTEPurificationUnitDegasser {

    @Inject(method = "calculateFinalSuccessChance", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$forceSuccess(CallbackInfoReturnable<Float> cir) {
        if (Config.purificationTier78AlwaysSucceed) {
            cir.setReturnValue(100.0f);
        }
    }
}
