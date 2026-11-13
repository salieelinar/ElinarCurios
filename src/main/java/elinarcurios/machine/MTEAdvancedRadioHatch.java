package elinarcurios.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import bartworks.common.tileentities.tiered.MTERadioHatch;
import elinarcurios.Config;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 高级放射仓（Advanced Radio Hatch）—— 原版放射仓（BartWorks {@link MTERadioHatch}）的省料版。
 *
 * <p>
 * 芷若 2026-10-31 需求原话：「gtnh原版有个仓室叫放射仓，消耗材料太厉害了……做一个新的“高级放射仓”，
 * 保持原来的材料表不变，但是消耗材料速度是原来的万分之一。例如镅杆时间是59.9秒，在“高级放射仓”内
 * 就是599000秒。」合成口径：「9个原版放射仓合成一个」，外观沿用原版材质。
 *
 * <p>
 * <b>实现方式（不碰 GT5U、不用 Mixin）</b>：原版放射仓的消耗模型是
 * 「每 {@code decayTime} tick 消耗 1 点 mass」，而 {@code decayTime = calcDecayTicks(SV)}。
 * 本类只做一件事：父类每次装入新材料、把 {@code decayTime} 重置成原版值时，立刻把它乘上倍率
 * （默认 10000）。于是材料清单、SV 值、mass 点数、覆盖度（辐射闸门）调节、NBT 格式、GUI
 * 全部继承父类 —— 「材料表不变、只是每根材料熬的时间变长 10000 倍」。
 *
 * <p>
 * <b>为什么是“父类重设时”而不是“每 tick 都乘”</b>：父类只在放入新放射性物品时重设
 * {@code decayTime}，其余时候它是不变的；记录“上一次的原始值”即可判断是否被重设，
 * 避免 NBT 读档后把已经放大过的值再乘一次。客户端不参与缩放（父类逻辑本来就只在服务端跑）。
 */
public class MTEAdvancedRadioHatch extends MTERadioHatch {

    /** 默认倍率：原版消耗周期 × 10000（= 消耗速度万分之一）。 */
    public static final long DEFAULT_MULTIPLIER = 10000L;

    /** 存档里保存“未缩放的 decayTime”，读档后据此还原，避免二次放大。 */
    private static final String NBT_RAW_DECAY = "elinar.radhatch.rawDecayTime";

    /** 父类最后一次设置的原始 decayTime（未乘倍率）。 */
    private long rawDecayTime = -1L;

    private boolean scaleInitialized;

    public MTEAdvancedRadioHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    /** 读档 / 客户端重建用。 */
    public MTEAdvancedRadioHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAdvancedRadioHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    /**
     * 先让父类跑完它的原生消耗逻辑（它可能在这一次调用里把 {@code decayTime} 重设成原版值），
     * 再检查是否需要放大。放在 super 之后而不是之前，是为了让“刚放入材料”的那一 tick
     * 界面上的剩余时间就已经是放大后的值。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTimer) {
        super.onPostTick(aBaseMetaTileEntity, aTimer);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        final long multiplier = configuredMultiplier();
        final long current = getDecayTime();
        if (!scaleInitialized || current != rawDecayTime * multiplier) {
            rawDecayTime = current;
            setDecayTime(current * multiplier);
            scaleInitialized = true;
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (scaleInitialized) {
            aNBT.setLong(NBT_RAW_DECAY, rawDecayTime);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey(NBT_RAW_DECAY)) {
            rawDecayTime = aNBT.getLong(NBT_RAW_DECAY);
            scaleInitialized = true;
            final long expected = rawDecayTime * configuredMultiplier();
            if (getDecayTime() != expected) setDecayTime(expected);
        }
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.radhatch.adv.desc.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.radhatch.adv.desc.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.radhatch.adv.desc.2"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.radhatch.adv.desc.3"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("elinarcurios.radhatch.adv.desc.4") };
    }

    private static long configuredMultiplier() {
        final int configured = Config.advancedRadioHatchMultiplier;
        return configured > 0 ? configured : DEFAULT_MULTIPLIER;
    }
}
