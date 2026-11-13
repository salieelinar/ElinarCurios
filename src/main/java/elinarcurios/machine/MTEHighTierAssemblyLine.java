package elinarcurios.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.GTLoggers.GT_FML_LOGGER;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;
import static gregtech.api.util.GTStructureUtility.ofHatchAdder;
import static gregtech.api.util.GTUtility.validMTEList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoidingMode;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDataAccess;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.AssemblyLineUtils;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipe.RecipeAssemblyLine;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.api.util.VoidProtectionHelper;
import gregtech.common.misc.GTStructureChannels;

/**
 * 高级装配线（High-Tier Assembly Line）。
 *
 * <p>
 * 结构完全复制 GTNH 原版装配线（{@code MTEAssemblyLine}），差异只在：
 * <ol>
 * <li>{@code 'e'} 能源仓位接受 {@link HatchElement#Energy} + {@link HatchElement#ExoticEnergy} +
 * GTNL 的并行控制仓（软依赖）；</li>
 * <li>并行上限来自 GTNL 的并行控制仓（有仓时用仓的 GUI 值），否则 GTNL 已安装=8、未安装=64；</li>
 * <li>{@code checkProcessing} 在保留原版"每片一个总线、按 NEI 顺序取料"的前提下，按
 * {@code min(输入允许份数, 并行上限, 功率允许份数)} 做真并行。</li>
 * </ol>
 *
 * <p>
 * 等级门保持原版装配线的 {@code avg × 4}——两个 LuV 能源仓直接解锁 ZPM 配方。
 */
public class MTEHighTierAssemblyLine extends MTEExtendedPowerMultiBlockBase<MTEHighTierAssemblyLine>
    implements ISurvivalConstructable, ICasingTextureProvider {

    /** GTNL 并行控制仓的类名（软依赖，不编译期引用）。 */
    private static final String GTNL_PARALLEL_HATCH_CLASS = "com.science.gtnl.common.machine.hatch.ParallelControllerHatch";
    private static final Class<?> GTNL_PARALLEL_HATCH;
    private static final boolean GTNL_LOADED;

    static {
        Class<?> clazz = null;
        boolean loaded = false;
        try {
            if (Loader.isModLoaded("sciencenotleisure")) {
                clazz = Class.forName(GTNL_PARALLEL_HATCH_CLASS);
                loaded = true;
            }
        } catch (Throwable ignored) {
            // GTNL 不在或类名变了：降级为无并行控制仓
        }
        GTNL_PARALLEL_HATCH = clazz;
        GTNL_LOADED = loaded;
    }

    public ArrayList<MTEHatchDataAccess> mDataAccessHatches = new ArrayList<>();
    /** 并行控制仓（GTNL 的 ParallelControllerHatch），只存 MTEHatch 以便反射调用。 */
    public final ArrayList<MTEHatch> mParallelControllerHatches = new ArrayList<>();

    private static final String STRUCTURE_PIECE_FIRST = "first";
    private static final String STRUCTURE_PIECE_LATER = "later";
    private static final String STRUCTURE_PIECE_LAST = "last";
    private static final IStructureDefinition<MTEHighTierAssemblyLine> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEHighTierAssemblyLine>builder()
        .addShape(
            STRUCTURE_PIECE_FIRST,
            transpose(new String[][] { { " ", "e", " " }, { "~", "l", "G" }, { "g", "m", "g" }, { "b", "i", "b" }, }))
        .addShape(
            STRUCTURE_PIECE_LATER,
            transpose(new String[][] { { " ", "e", " " }, { "d", "l", "d" }, { "g", "m", "g" }, { "b", "I", "b" }, }))
        .addShape(
            STRUCTURE_PIECE_LAST,
            transpose(new String[][] { { " ", "e", " " }, { "d", "l", "d" }, { "g", "m", "g" }, { "o", "i", "b" }, }))
        .addElement('G', ofBlock(GregTechAPI.sBlockCasings3, 10)) // grate machine casing
        .addElement('l', ofBlock(GregTechAPI.sBlockCasings2, 9)) // assembler machine casing
        .addElement('m', ofBlock(GregTechAPI.sBlockCasings2, 5)) // assembling line casing
        .addElement('g', chainAllGlasses())
        .addElement(
            'e',
            ofChain(
                buildHatchAdder(MTEHighTierAssemblyLine.class)
                    .anyOf(Energy, ExoticEnergy, ParallelControllerElement.INSTANCE)
                    .casingIndex(16)
                    .hint(4)
                    .allowOnly(ForgeDirection.UP, ForgeDirection.NORTH, ForgeDirection.SOUTH)
                    .continueIfSuccess()
                    .build(),
                ofBlock(GregTechAPI.sBlockCasings2, 0)))
        .addElement(
            'd',
            buildHatchAdder(MTEHighTierAssemblyLine.class).atLeast(DataHatchElement.DataAccess)
                .hint(3)
                .casingIndex(42)
                .allowOnly(ForgeDirection.NORTH)
                .buildAndChain(GregTechAPI.sBlockCasings3, 10))
        .addElement(
            'b',
            buildHatchAdder(MTEHighTierAssemblyLine.class)
                .atLeast(InputHatch, InputHatch, InputHatch, InputHatch, Maintenance)
                .casingIndex(16)
                .hint(1)
                .allowOnly(ForgeDirection.DOWN)
                .buildAndChain(
                    ofBlock(GregTechAPI.sBlockCasings2, 0),
                    ofHatchAdder(MTEHighTierAssemblyLine::addOutputToMachineList, 16, 4)))
        .addElement(
            'I',
            ofChain(
                InputBus.newAny(16, 2, ForgeDirection.DOWN),
                ofHatchAdder(MTEHighTierAssemblyLine::addOutputToMachineList, 16, 3)))
        .addElement('i', InputBus.newAny(16, 2, ForgeDirection.DOWN))
        .addElement('o', OutputBus.newAny(16, 1, ForgeDirection.DOWN))
        .build();

    public MTEHighTierAssemblyLine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEHighTierAssemblyLine(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHighTierAssemblyLine(this.mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("High-Tier Assembly Line, HTAL")
            .addInfo("An Assembly Line clone that accepts Multi-Amp / Laser energy hatches")
            .addInfo("Parallel: 8 when GTNL is loaded, 64 when it is not")
            .addInfo("A GTNL Parallel Controller Hatch overrides the parallel cap")
            .addInfo("Two LuV energy hatches unlock ZPM recipes (voltage boost)")
            .addSupportAny()
            .addMaxTierSkips(1)
            .beginVariableStructureBlock(5, 16, 4, 4, 3, 3, false)
            .addController("First slice, 3rd layer")
            .addMiscHatch(
                "1",
                StatCollector.translateToLocal("GT5U.tooltip.structure.data_access_hatch"),
                "Any grate machine casing NOT on first slice",
                3)
            .addEnergyHatch("1+", "Any layer 4 casing", 4)
            .addMaintenanceHatch("1", "Any layer 1 side casing", 1)
            .addInputBus("5-16", "Bottom center of each slice", 2)
            .addInputHatch("1-4", "Any layer 1 side casing", 1)
            .addOutputBus("1", "Any layer 1 casing on last slice", 1)
            .addStructureInfo("")
            .addStructureInfo(StatCollector.translateToLocal("GT5U.MBTT.Structure.Base"))
            .addCasing("10", "Any Tiered Glass", false)
            .addCasing("8", "Grate Machine Casing", false)
            .addCasing("4-8", "Solid Steel Machine Casing", false)
            .addCasing("5", "Assembly Line Casing", false)
            .addCasing("5", "Assembler Machine Casing", false)
            .addStructureInfo("")
            .addStructureInfo(StatCollector.translateToLocal("GT5U.MBTT.Structure.Slice"))
            .addStructureInfo(
                EnumChatFormatting.WHITE + "Layer 4: "
                    + EnumChatFormatting.GRAY
                    + "Empty, Solid Steel Machine Casing / Energy / Parallel Controller, Empty")
            .addStructureInfo(
                EnumChatFormatting.WHITE + "Layer 3: "
                    + EnumChatFormatting.GRAY
                    + "Grate Machine Casing, Assembler Machine Casing, Grate Machine Casing")
            .addStructureInfo(
                EnumChatFormatting.WHITE + "Layer 2: "
                    + EnumChatFormatting.GRAY
                    + "Any Tiered Glass, Assembly Line Casing, Any Tiered Glass")
            .addStructureInfo(
                EnumChatFormatting.WHITE + "Layer 1: "
                    + EnumChatFormatting.GRAY
                    + "Solid Steel Machine Casing, Input Bus, Solid Steel Machine Casing")
            .addStructureInfo("")
            .addStructureFooter("Up to 16 total slices, each one allows for 1 more item in recipes")
            .addSubChannel(GTStructureChannels.STRUCTURE_LENGTH)
            .addSubChannel(GTStructureChannels.BOROGLASS)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_ASSEMBLY_LINE,
            OVERLAY_FRONT_ASSEMBLY_LINE_GLOW,
            OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE,
            OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return Textures.BlockIcons.casingTexturePages[0][16];
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.assemblylineVisualRecipes;
    }

    @Override
    public @Nonnull CheckRecipeResult checkProcessing() {
        if (GTValues.D1) {
            GT_FML_LOGGER.info("Start HTAL recipe check");
        }
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_DATA_STICKS;
        ArrayList<RecipeAssemblyLine> availableRecipes = new ArrayList<>();

        if (AssemblyLineUtils.isItemDataStick(mInventory[1])) {
            availableRecipes.addAll(AssemblyLineUtils.findALRecipeFromDataStick(mInventory[1]));
        }
        for (MTEHatchDataAccess dataAccess : validMTEList(mDataAccessHatches)) {
            availableRecipes.addAll(dataAccess.getAssemblyLineRecipes());
        }
        if (availableRecipes.isEmpty()) {
            return result;
        }

        int[] tStacks = GTValues.emptyIntArray;
        FluidStack[] tFluids = GTValues.emptyFluidStackArray;
        long averageVoltage = getAverageInputVoltage();
        long maxAmp = getMaxInputAmps();
        // 只有"恰好 1 个普通 2A 仓"时才关闭安培超频；其它组合（多安/激光/混合）都启用。
        boolean amperageOC = !(mEnergyHatches.size() == 1 && mExoticEnergyHatches.isEmpty());
        Map<GTUtility.ItemId, ItemStack> inputsFromME = getStoredInputsFromME();
        Map<Fluid, FluidStack> fluidsFromME = getStoredFluidsFromME();
        int maxParallel = 1;

        for (RecipeAssemblyLine tRecipe : availableRecipes) {
            if (mInputBusses.size() < tRecipe.mInputs.length || mInputHatches.size() < tRecipe.mFluidInputs.length) {
                if (result == CheckRecipeResultRegistry.NO_DATA_STICKS) result = CheckRecipeResultRegistry.NO_RECIPE;
                continue;
            }

            int[] itemConsumptions = GTRecipe.RecipeAssemblyLine.getItemConsumptionAmountArray(mInputBusses, tRecipe);
            if (itemConsumptions == null || itemConsumptions.length == 0) {
                if (result == CheckRecipeResultRegistry.NO_DATA_STICKS) result = CheckRecipeResultRegistry.NO_RECIPE;
                continue;
            }

            // 1) 输入能支持的并行份数（原版语义：每个输入总线各支持 N 份）
            int inputLimited = (int) GTRecipe.RecipeAssemblyLine
                .maxParallelCalculatedByInputItems(mInputBusses, Integer.MAX_VALUE, itemConsumptions, inputsFromME);
            if (inputLimited <= 0) {
                if (result == CheckRecipeResultRegistry.NO_DATA_STICKS) result = CheckRecipeResultRegistry.NO_RECIPE;
                continue;
            }
            if (tRecipe.mFluidInputs.length > 0) {
                inputLimited = (int) GTRecipe.RecipeAssemblyLine.maxParallelCalculatedByInputFluids(
                    mInputHatches,
                    inputLimited,
                    tRecipe.mFluidInputs,
                    fluidsFromME);
                if (inputLimited <= 0) {
                    if (result == CheckRecipeResultRegistry.NO_DATA_STICKS)
                        result = CheckRecipeResultRegistry.NO_RECIPE;
                    continue;
                }
            }

            // 2) 等级门：保留原版装配线的 avg×4（两个 LuV 仓 → ZPM 配方）
            if (tRecipe.mEUt > averageVoltage * 4) {
                result = CheckRecipeResultRegistry.insufficientPower(tRecipe.mEUt);
                continue;
            }
            // 3) 单份功率门
            if (tRecipe.mEUt > maxAmp * averageVoltage) {
                result = CheckRecipeResultRegistry.insufficientPower(tRecipe.mEUt);
                continue;
            }

            // 4) 并行上限 = min(输入, GTNL/默认并行上限, 功率)
            long powerPool = averageVoltage * maxAmp;
            int powerLimited = (int) Math.max(1, Math.min(Integer.MAX_VALUE, powerPool / Math.max(1L, tRecipe.mEUt)));
            int parallelCap = getMaxParallelRecipes();
            int originalMaxParallel = Math.max(1, Math.min(inputLimited, Math.min(parallelCap, powerLimited)));
            maxParallel = originalMaxParallel;

            OverclockCalculator calculator = new OverclockCalculator().setRecipeEUt(tRecipe.mEUt)
                .setEUt(averageVoltage)
                .setAmperage(maxAmp)
                .setAmperageOC(amperageOC)
                .setDuration(tRecipe.mDuration)
                .setParallel(originalMaxParallel);

            maxParallel = GTUtility.safeInt((long) (maxParallel * calculator.calculateMultiplierUnderOneTick()), 0);
            int maxParallelBeforeBatchMode = maxParallel;
            if (isBatchModeEnabled()) {
                maxParallel = GTUtility.safeInt((long) maxParallel * getMaxBatchSize(), 0);
            }

            if (protectsExcessItem()) {
                VoidProtectionHelper voidProtectionHelper = new VoidProtectionHelper();
                voidProtectionHelper.setMachine(this)
                    .setItemOutputs(new ItemStack[] { tRecipe.mOutput })
                    .setMaxParallel(maxParallel)
                    .build();
                maxParallel = Math.min(voidProtectionHelper.getMaxParallel(), maxParallel);
                if (voidProtectionHelper.isItemFull()) {
                    result = CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
                    continue;
                }
            }

            int currentParallel = Math.min(inputLimited, maxParallel);
            int currentParallelBeforeBatchMode = Math.min(currentParallel, maxParallelBeforeBatchMode);
            calculator.setCurrentParallel(currentParallelBeforeBatchMode)
                .calculate();

            double batchMultiplierMax = 1;
            if (currentParallel > maxParallelBeforeBatchMode && calculator.getDuration() < getMaxBatchSize()) {
                batchMultiplierMax = (double) getMaxBatchSize() / calculator.getDuration();
                batchMultiplierMax = Math
                    .min(batchMultiplierMax, (double) currentParallel / maxParallelBeforeBatchMode);
            }
            int finalParallel = (int) (batchMultiplierMax * currentParallelBeforeBatchMode);
            lEUt = calculator.getConsumption();
            mMaxProgresstime = (int) (calculator.getDuration() * batchMultiplierMax);
            maxParallel = finalParallel;
            tStacks = itemConsumptions;
            tFluids = tRecipe.mFluidInputs;
            result = CheckRecipeResultRegistry.SUCCESSFUL;
            ArrayList<ItemStack> outputs = new ArrayList<>();
            ParallelHelper.addItemsLong(outputs, tRecipe.mOutput, (long) tRecipe.mOutput.stackSize * maxParallel);
            mOutputItems = outputs.toArray(new ItemStack[0]);
            break;
        }

        if (!result.wasSuccessful()) {
            return result;
        }
        if (tStacks.length == 0 || maxParallel <= 0) {
            return CheckRecipeResultRegistry.INTERNAL_ERROR;
        }

        GTRecipe.RecipeAssemblyLine.consumeInputItems(mInputBusses, maxParallel, tStacks, inputsFromME);
        GTRecipe.RecipeAssemblyLine.consumeInputFluids(mInputHatches, maxParallel, tFluids, fluidsFromME);

        if (this.lEUt > 0) {
            this.lEUt = -this.lEUt;
        }
        this.mEfficiency = (10000 - (getIdealStatus() - getRepairStatus()) * 1000);
        this.mEfficiencyIncrease = 10000;
        updateSlots();
        return result;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        for (MTEHatchDataAccess hatchDataAccess : mDataAccessHatches) {
            hatchDataAccess.setActive(true);
        }
        return super.onRunningTick(aStack);
    }

    @Override
    public IStructureDefinition<MTEHighTierAssemblyLine> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mDataAccessHatches.clear();
        mParallelControllerHatches.clear();
        if (!checkPiece(STRUCTURE_PIECE_FIRST, 0, 1, 0, errors)) return;
        int recognizedLayers = checkMachine(true, errors);
        if (errors.isEmpty()) return;

        List<StructureError> errors2 = new ArrayList<>();
        int recognizedLayers2 = checkMachine(false, errors2);
        if (errors2.isEmpty()) {
            errors.clear();
            return;
        }
        if (recognizedLayers < recognizedLayers2) {
            errors.clear();
            errors.addAll(errors2);
        }
    }

    private int checkMachine(boolean leftToRight, List<StructureError> errors) {
        for (int i = 1; i < 16; i++) {
            if (!checkPiece(STRUCTURE_PIECE_LATER, leftToRight ? -i : i, 1, 0, errors)) return i;
            if (!mOutputBusses.isEmpty()) {
                if (mDataAccessHatches.size() > 1) {
                    errors.add(StructureErrors.of("GT5U.gui.text.structure_error.al_too_many_data_access_hatch"));
                }
                checkHasAnyEnergy(errors);
                checkHasMaintenanceHatch(errors);
                return i;
            }
        }
        errors.add(StructureErrors.of("GT5U.gui.text.structure_error.al_missing_output_bus"));
        return 16;
    }

    public boolean addDataAccessToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchDataAccess) {
            ((MTEHatch) aMetaTileEntity).updateTexture(aBaseCasingIndex);
            addIfSmartInput(aMetaTileEntity);
            return mDataAccessHatches.add((MTEHatchDataAccess) aMetaTileEntity);
        }
        return false;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_FIRST, stackSize, hintsOnly, 0, 1, 0);
        int tLength = GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(stackSize, 5, 16);
        for (int i = 1; i < tLength; i++) {
            buildPiece(STRUCTURE_PIECE_LATER, stackSize, hintsOnly, -i, 1, 0);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int build = survivalBuildPiece(STRUCTURE_PIECE_FIRST, stackSize, 0, 1, 0, elementBudget, env, false, true);
        if (build >= 0) return build;
        int tLength = GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(stackSize, 5, 16);
        for (int i = 1; i < tLength; i++) {
            build = survivalBuildPiece(STRUCTURE_PIECE_LATER, stackSize, -i, 1, 0, elementBudget, env, false, true);
            if (build >= 0) return build;
        }
        return survivalBuildPiece(STRUCTURE_PIECE_LAST, stackSize, 1 - tLength, 1, 0, elementBudget, env, false, true);
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public Set<VoidingMode> getAllowedVoidingModes() {
        return VoidingMode.ITEM_ONLY_MODES;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    public int getSizeInventory() {
        return mInventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < mInventory.length ? mInventory[index] : null;
    }

    @Override
    public ItemStack decrStackSize(int index, int amount) {
        ItemStack stack = getStackInSlot(index);
        if (stack == null) return null;
        if (stack.stackSize <= amount) {
            setInventorySlotContents(index, null);
            return stack;
        }
        ItemStack split = stack.splitStack(amount);
        if (stack.stackSize <= 0) setInventorySlotContents(index, null);
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index >= 0 && index < mInventory.length) mInventory[index] = stack;
    }

    @Override
    public String getInventoryName() {
        return getMetaName();
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    // GT5U 的 multi-release jar 里 Java17 变体没有把这两个方法带出来，独立 addon 显式实现一遍。
    @Override
    public int[] getAccessibleSlotsFromSide(int ordinalSide) {
        int[] slots = new int[mInventory.length];
        int count = 0;
        for (int i = 0; i < mInventory.length; i++) {
            if (isValidSlot(i)) slots[count++] = i;
        }
        int[] result = new int[count];
        System.arraycopy(slots, 0, result, 0, count);
        return result;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        return getBaseMetaTileEntity().isValidSlot(index);
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStack, int ordinalSide) {
        return isValidSlot(index) && itemStack != null
            && index < mInventory.length
            && (mInventory[index] == null || GTUtility.areStacksEqual(itemStack, mInventory[index]))
            && allowPutStack(getBaseMetaTileEntity(), index, ForgeDirection.getOrientation(ordinalSide), itemStack);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack itemStack, int ordinalSide) {
        return isValidSlot(index) && itemStack != null
            && index < mInventory.length
            && allowPullStack(getBaseMetaTileEntity(), index, ForgeDirection.getOrientation(ordinalSide), itemStack);
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        mParallelControllerHatches.clear();
    }

    /**
     * 并行上限：GTNL 并行控制仓存在时用它的 GUI 值；否则 GTNL 已安装=8、未安装=64。
     */
    @Override
    public int getMaxParallelRecipes() {
        if (!mParallelControllerHatches.isEmpty()) {
            MTEHatch hatch = mParallelControllerHatches.get(0);
            try {
                Method getParallel = hatch.getClass()
                    .getMethod("getParallel");
                Object value = getParallel.invoke(hatch);
                if (value instanceof Integer) {
                    return Math.max(1, (Integer) value);
                }
            } catch (Throwable ignored) {
                // 反射失败时按默认值
            }
        }
        return GTNL_LOADED ? 8 : 64;
    }

    private static boolean isGtnlParallelController(IMetaTileEntity mte) {
        return mte != null && GTNL_PARALLEL_HATCH != null && GTNL_PARALLEL_HATCH.isInstance(mte);
    }

    /** 并行控制仓的 hatch 元素：GTNL 不在时永远匹配失败，结构仍可正常放能源仓。 */
    private static final class ParallelControllerElement implements IHatchElement<MTEHighTierAssemblyLine> {

        static final ParallelControllerElement INSTANCE = new ParallelControllerElement();

        @SuppressWarnings("unchecked")
        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            if (GTNL_PARALLEL_HATCH == null) return Collections.emptyList();
            return Collections.singletonList((Class<? extends IMetaTileEntity>) GTNL_PARALLEL_HATCH);
        }

        @Override
        public IGTHatchAdder<? super MTEHighTierAssemblyLine> adder() {
            return (t, te, casing) -> {
                if (te == null) return false;
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (!(mte instanceof MTEHatch) || !isGtnlParallelController(mte)) return false;
                ((MTEHatch) mte).updateTexture(casing);
                return t.mParallelControllerHatches.add((MTEHatch) mte);
            };
        }

        @Override
        public long count(MTEHighTierAssemblyLine t) {
            return t.mParallelControllerHatches.size();
        }

        @Override
        public String name() {
            return "ParallelController";
        }

        @Override
        public boolean matchesHatch(IMetaTileEntity mte) {
            return isGtnlParallelController(mte);
        }
    }

    private enum DataHatchElement implements IHatchElement<MTEHighTierAssemblyLine> {

        DataAccess;

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchDataAccess.class);
        }

        @Override
        public IGTHatchAdder<MTEHighTierAssemblyLine> adder() {
            return MTEHighTierAssemblyLine::addDataAccessToMachineList;
        }

        @Override
        public long count(MTEHighTierAssemblyLine t) {
            return t.mDataAccessHatches.size();
        }
    }
}
