package elinarcurios.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.Config;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBaryonicPerfection;

/**
 * 彩蛋（默认关闭）：让净水厂八级净化单元「绝对重子完美净化单元」最终成功率恒为 100%。
 *
 * <p>
 * 原版 {@code calculateFinalSuccessChance()} 只有在输入总线里插入了随机生成的那一对夸克
 * 催化剂（每根还另耗熔融无尽）时才返回 100，否则返回 0。这里在方法头直接短路成 100，
 * 使该单元只靠上一级水就能稳定产出八级净化水。
 *
 * <p>
 * 注意：配方自带的产出照旧（每周期 500 L 稳定重子物质 + 2 个未对齐催化剂），
 * 那不是这个彩蛋加的，是配方本身的输出。
 *
 * <p>
 * 只影响这一台单元；开关在 {@code config/ElinarCurios.cfg} 的 {@code purificationTier78AlwaysSucceed}，
 * 默认 false（不改任何行为）。扫描器显示的成功率走同一个方法，因此也会一起变成 100%。
 */
@Mixin(value = MTEPurificationUnitBaryonicPerfection.class, remap = false)
public abstract class MixinMTEPurificationUnitBaryonicPerfection {

    @Inject(method = "calculateFinalSuccessChance", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$forceSuccess(CallbackInfoReturnable<Float> cir) {
        if (Config.purificationTier78AlwaysSucceed) {
            cir.setReturnValue(100.0f);
        }
    }
}
