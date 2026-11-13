package elinarcurios.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.gui.MTEHatchInputAdjustableGui;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import io.netty.buffer.ByteBuf;

/**
 * 可调容量流体输入仓（ElinarCurios 独立验证版）。
 *
 * <p>
 * 需求（芷若 2026-10-20 原话）：「我想为 ElinarCurios 增加一个 gtnh 的可自定义的流体输入仓，从 1L 至 2147483647L
 * 上限均可以设置。GUI 采用原版的输入仓，但是增加一个设置的位置，可以输入这个输入仓能存放流体的上限。」
 *
 * <p>
 * 实现要点：GT 的输入仓容量只由 {@link MTEHatchInput#getCapacity()} 一个 int 方法决定，
 * {@code MTEBasicTank.fluidTank} 通过 {@code getRealCapacity()} 动态取它（{@code MetaTileEntity#getRealCapacity()}
 * 默认返回 {@code getCapacity()}）。所以本类只覆盖 {@code getCapacity()}，fill/drain/管道/多方块结构/GUI
 * 的容量就会全部跟随配置值，不需要改 GT、不需要 Mixin。
 *
 * <p>
 * 拍板记录（2026-10-20）：电压等级按低压（LV，tier 1）；允许调低容量，超出的流体直接销毁；
 * 初始容量 1,028,000 L；上限 2,147,483,647 L（Forge/GT 流体 API 的 int 上限）。
 */
public class MTEHatchInputAdjustable extends MTEHatchInput {

    /** 允许设置的下限（需求从 1L 起）。 */
    public static final int MIN_CAPACITY = 1;

    /** Forge/GT 流体 API 的硬上限（FluidStack.amount 是 int）。 */
    public static final int MAX_CAPACITY = Integer.MAX_VALUE;

    /** 芷若拍板的初始容量。 */
    public static final int DEFAULT_CAPACITY = 1_028_000;

    private static final String NBT_CAPACITY = "elinarConfiguredCapacity";

    private int configuredCapacity = DEFAULT_CAPACITY;

    public MTEHatchInputAdjustable(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTEHatchInputAdjustable(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public int getCapacity() {
        return getConfiguredCapacity();
    }

    public int getConfiguredCapacity() {
        return configuredCapacity > 0 ? configuredCapacity : DEFAULT_CAPACITY;
    }

    /**
     * 设置容量上限。调低到低于当前存量时，超出的流体直接销毁（2026-10-20 拍板）。
     * 只在服务端写盘/发方块更新，客户端仅更新字段供 GUI 即时显示。
     */
    public void setConfiguredCapacity(int aCapacity) {
        final int newCapacity = Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY, aCapacity));
        if (newCapacity == getConfiguredCapacity()) return;
        configuredCapacity = newCapacity;
        if (mFluid != null && mFluid.amount > newCapacity) {
            mFluid.amount = newCapacity;
        }
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            base.markDirty();
            base.issueTileUpdate();
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger(NBT_CAPACITY, configuredCapacity);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey(NBT_CAPACITY)) {
            configuredCapacity = Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY, aNBT.getInteger(NBT_CAPACITY)));
            if (mFluid != null && mFluid.amount > configuredCapacity) {
                mFluid.amount = configuredCapacity;
            }
        }
    }

    @Override
    public void writeToStream(ByteBuf buffer) {
        super.writeToStream(buffer);
        buffer.writeInt(getConfiguredCapacity());
    }

    @Override
    public void readFromStream(ByteBuf buffer) {
        super.readFromStream(buffer);
        configuredCapacity = Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY, buffer.readInt()));
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal(getLocalNameKey());
    }

    @Override
    public String[] getDescription() {
        return new String[] { EnumChatFormatting.GRAY + "容量可在 1 ~ 2,147,483,647 L 之间设置",
            EnumChatFormatting.GRAY + "点击 GUI 左下角的螺丝刀按钮设置容量上限", EnumChatFormatting.RED + "调低容量会销毁超出的流体",
            EnumChatFormatting.DARK_PURPLE + "初始容量 1,028,000 L" };
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchInputAdjustable(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEHatchInputAdjustableGui(this).build(guiData, syncManager, uiSettings);
    }
}
