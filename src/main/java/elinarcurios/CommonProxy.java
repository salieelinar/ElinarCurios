package elinarcurios;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import elinarcurios.block.BlockEternalChest;
import elinarcurios.block.BlockPeaceTotem;
import elinarcurios.block.BlockVoidChest;
import elinarcurios.compat.StructureLibCompat;
import elinarcurios.compat.ThaumcraftCompat;
import elinarcurios.core.GuiHandler;
import elinarcurios.item.ItemElinarFresnelLens;
import elinarcurios.item.ItemElinarReactorCore;
import elinarcurios.item.ItemElinarTianxuanCore;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.item.ItemLunchBox;
import elinarcurios.item.ItemWirelessBattery;
import elinarcurios.machine.ElinarChunkManager;
import elinarcurios.machine.ElinarMachines;
import elinarcurios.meout.MEOutputFlushHandler;
import elinarcurios.network.BuffStatePacket;
import elinarcurios.network.CurrentPagePacket;
import elinarcurios.network.DissociateVoidChestPacket;
import elinarcurios.network.EquipUnequipBagPacket;
import elinarcurios.network.InfiniteBagClick;
import elinarcurios.network.InfiniteBagConfirmation;
import elinarcurios.network.InfiniteBagSlotSync;
import elinarcurios.network.InfiniteBagSyncAllSlots;
import elinarcurios.network.LunchBoxEatSync;
import elinarcurios.network.OpenBagPacket;
import elinarcurios.network.OpenLunchBoxPacket;
import elinarcurios.network.RequestCurrentPagePacket;
import elinarcurios.network.RequestWornBagPacket;
import elinarcurios.network.SolidifyVoidChestPacket;
import elinarcurios.network.SwitchPagePacket;
import elinarcurios.network.SyncBagPacket;
import elinarcurios.network.ToggleBuffPacket;
import elinarcurios.network.WandPoolSyncPacket;
import elinarcurios.player.PlayerEventHandler;
import elinarcurios.recipe.FresnelLensRecipes;
import elinarcurios.recipe.HighTierAssemblyLineRecipes;
import elinarcurios.recipe.ReactorCoreRecipes;
import elinarcurios.recipe.WirelessBatteryRecipes;
import elinarcurios.util.PeaceTotemHandler;

public class CommonProxy {

    public void preInit() {
        final ItemInfiniteBackpack item = new ItemInfiniteBackpack();
        ItemInfiniteBackpack.instance = item;
        GameRegistry.registerItem(item, "InfiniteBackpack");

        // Thaumcraft soft-integration: rod/cap + wand item (both sides, see §5.13).
        // The guard MUST be at the call site, not inside ThaumcraftCompat: its class
        // fields reference TC types, so loading the class itself fails without TC
        // (NoClassDefFoundError before any method body runs). With the guard here,
        // the class is never even loaded on TC-less environments.
        if (Loader.isModLoaded("Thaumcraft")) {
            ThaumcraftCompat.init();
            ThaumcraftCompat.registerItem();
        }

        // Wireless battery (天玄·虚空电池): reads/writes the GT wireless energy
        // network (GregTech) through the IC2 electric-item API — both GTNH staples.
        // Same call-site guard pattern as ThaumcraftCompat: the item class references
        // GT/IC2 types and must not load without them. ItemWirelessBattery.instance
        // stays null when GregTech/IC2 are absent.
        if (Loader.isModLoaded("gregtech") && Loader.isModLoaded("IC2")) {
            ItemWirelessBattery.instance = new ItemWirelessBattery();
            GameRegistry.registerItem(ItemWirelessBattery.instance, "WirelessBattery");
        }

        // GT 机器注册(天玄虚空能量节点):走 GT5U 硬依赖 —— MTE 构造器自身要做 ID 占用检查,
        // 且必须在 GT 的 load 阶段调用(BetaMetaTileEntity 会校验 sPreloadStarted/sPostloadStarted)。
        // 依赖已提升为硬依赖,不再考虑无 GT 环境下的类加载隔离。
        if (Loader.isModLoaded("gregtech")) {
            ElinarMachines.register();
            // 号段迁移（v1.0.0c2 过渡版）：区块加载时迁移旧号机器，世界/玩家 tick 兜底修正物品。
            // ChunkEvent 是 Forge 事件；TickEvent 是 FML 事件，同一个类两个总线都要挂。
            MinecraftForge.EVENT_BUS.register(elinarcurios.machine.MachineIdMigration.class);
            FMLCommonHandler.instance()
                .bus()
                .register(elinarcurios.machine.MachineIdMigration.class);
            // 束流虚空存储器用 UUID 找"已加载实例"：世界卸载时把该世界的条目摘掉，防死引用。
            MinecraftForge.EVENT_BUS.register(new elinarcurios.machine.BeamVoidRegistryHandler());
            // 综合采掘工厂的 1×1 世界锚：Forge 票据由本 mod 自己持久化 / 重启后恢复，
            // 不走 GTChunkManager（其默认配置下机器票据是“被动锚”，重启后会被丢弃）。
            ElinarChunkManager.init();
        }

        // 天枢·恒温核心(反应堆隔板 + 冷却单元 + 热交换器三合一元件):只依赖 IC2 的
        // ic2.api.reactor.*,IC2 是 GTNH 必装。同一个调用点守卫模式 —— 无 IC2 时
        // ItemElinarReactorCore 整个类不会被加载。
        if (Loader.isModLoaded("IC2")) {
            ItemElinarReactorCore.instance = new ItemElinarReactorCore();
            GameRegistry.registerItem(ItemElinarReactorCore.instance, "ElinarReactorCore");
            // 天玄·恒温核心:天枢的升级版(99% 硬锁 + 全局产能增益),同样只依赖 ic2.api.reactor.*
            ItemElinarTianxuanCore.instance = new ItemElinarTianxuanCore();
            GameRegistry.registerItem(ItemElinarTianxuanCore.instance, "ElinarTianxuanCore");
            // 菲涅尔透镜:全堆均匀中子辐照(给每一格的增殖元件发脉冲,催熟阳光化合物棒 / 氚棒)
            ItemElinarFresnelLens.instance = new ItemElinarFresnelLens();
            GameRegistry.registerItem(ItemElinarFresnelLens.instance, "ElinarFresnelLens");
            // Config 的 static 块在这里被首次触发(物品 tooltip 要读脉冲数)。Config 的静态
            // 字段目前没有任何外部引用,不显式触碰一次的话,这一节配置会拖到第一个访问者
            // 出现时才写进 cfg —— 玩家会先看到"配置里没有这项"。
            Config.ensureLoaded();
        }

        BlockPeaceTotem.register();
        BlockEternalChest.register();
        BlockVoidChest.register();

        // 午餐盒:进食协议整套继承自 SpiceOfLife 的 ItemFoodContainer(盒盖状态、原版进食动画、
        // 智能选食、AppleCore 食物值),所以注册点必须用 isModLoaded 包住 —— 该物品类在类加载
        // 阶段就需要父类,守卫写在方法体里已经太晚(同 ThaumcraftCompat 的教训)。
        if (Loader.isModLoaded("SpiceOfLife")) {
            ItemLunchBox.instance = new ItemLunchBox();
            GameRegistry.registerItem(ItemLunchBox.instance, "LunchBox");
        }

        // The totem's anti-spawn logic listens to Forge spawn events.
        MinecraftForge.EVENT_BUS.register(new PeaceTotemHandler());

        // ME 输出快刷:玩家下单期间接手 GT 的 40 tick 定时器(见 MEOutputFlushHandler 的类注释)。
        // TickEvent 是 FML 事件(WorldEvent.Unload 是 Forge 事件),同一个实例两个总线都要挂。
        final MEOutputFlushHandler meFlushHandler = new MEOutputFlushHandler();
        FMLCommonHandler.instance()
            .bus()
            .register(meFlushHandler);
        MinecraftForge.EVENT_BUS.register(meFlushHandler);

        NetworkRegistry.INSTANCE.registerGuiHandler(ElinarCurios.instance, GuiHandler.instance);
        // The handler listens to both Forge events (EntityConstructing, LivingDeath)
        // and FML events (PlayerTickEvent buffs, PlayerLoggedInEvent sync).
        final PlayerEventHandler playerEventHandler = new PlayerEventHandler();
        MinecraftForge.EVENT_BUS.register(playerEventHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(playerEventHandler);

        // 天玄之杖的池子存在世界级 WandPoolData（UUID -> 要素表）里，随世界存档保存，
        // 主人离线时机器也能直接读写；不需要额外的落盘 tick handler。

        // 幽灵模式：服务端每 tick 刷新"幽灵玩家"集合，并每 5 tick 清一次已锁定的怪物仇恨。
        // 纯 FML 事件（TickEvent），只挂 FML 总线。
        FMLCommonHandler.instance()
            .bus()
            .register(new elinarcurios.player.GhostModeHandler());

        // GT 机器 ID 表导出：/Elinar gtid 触发，分 tick 扫描，CSV 写到 logs/。
        FMLCommonHandler.instance()
            .bus()
            .register(new elinarcurios.command.GtidExportHandler());

        final int[] ids = { 0 };
        final SimpleNetworkWrapper net = ElinarCurios.networkWrapper;
        net.registerMessage(InfiniteBagSlotSync.Handler.class, InfiniteBagSlotSync.class, ids[0]++, Side.SERVER);
        net.registerMessage(InfiniteBagSlotSync.Handler.class, InfiniteBagSlotSync.class, ids[0]++, Side.CLIENT);
        net.registerMessage(InfiniteBagClick.Handler.class, InfiniteBagClick.class, ids[0]++, Side.SERVER);
        net.registerMessage(InfiniteBagClick.Handler.class, InfiniteBagClick.class, ids[0]++, Side.CLIENT);
        net.registerMessage(
            InfiniteBagConfirmation.Handler.class,
            InfiniteBagConfirmation.class,
            ids[0]++,
            Side.SERVER);
        net.registerMessage(
            InfiniteBagConfirmation.Handler.class,
            InfiniteBagConfirmation.class,
            ids[0]++,
            Side.CLIENT);
        net.registerMessage(
            InfiniteBagSyncAllSlots.Handler.class,
            InfiniteBagSyncAllSlots.class,
            ids[0]++,
            Side.SERVER);
        net.registerMessage(
            InfiniteBagSyncAllSlots.Handler.class,
            InfiniteBagSyncAllSlots.class,
            ids[0]++,
            Side.CLIENT);
        net.registerMessage(OpenBagPacket.class, OpenBagPacket.Message.class, ids[0]++, Side.SERVER);
        net.registerMessage(EquipUnequipBagPacket.class, EquipUnequipBagPacket.Message.class, ids[0]++, Side.SERVER);
        net.registerMessage(SyncBagPacket.class, SyncBagPacket.Message.class, ids[0]++, Side.CLIENT);
        net.registerMessage(ToggleBuffPacket.class, ToggleBuffPacket.Message.class, ids[0]++, Side.SERVER);
        net.registerMessage(BuffStatePacket.Handler.class, BuffStatePacket.class, ids[0]++, Side.CLIENT);
        net.registerMessage(SwitchPagePacket.class, SwitchPagePacket.Message.class, ids[0]++, Side.SERVER);
        net.registerMessage(
            RequestCurrentPagePacket.class,
            RequestCurrentPagePacket.Message.class,
            ids[0]++,
            Side.SERVER);
        net.registerMessage(RequestWornBagPacket.class, RequestWornBagPacket.Message.class, ids[0]++, Side.SERVER);
        net.registerMessage(CurrentPagePacket.Handler.class, CurrentPagePacket.class, ids[0]++, Side.CLIENT);
        net.registerMessage(
            SolidifyVoidChestPacket.class,
            SolidifyVoidChestPacket.Message.class,
            ids[0]++,
            Side.SERVER);
        net.registerMessage(
            DissociateVoidChestPacket.class,
            DissociateVoidChestPacket.Message.class,
            ids[0]++,
            Side.SERVER);
        // 午餐盒:打开仓库界面(客户端 -> 服务端;GUI 必须由服务端 openGui)
        net.registerMessage(OpenLunchBoxPacket.class, OpenLunchBoxPacket.Message.class, ids[0]++, Side.SERVER);
        // 午餐盒:进食后只推变化的那一格(服务端 -> 客户端),避免每吃一口重发整个仓库 NBT
        net.registerMessage(LunchBoxEatSync.Handler.class, LunchBoxEatSync.class, ids[0]++, Side.CLIENT);
        // 天玄之杖:玩家共享魔力池快照(服务端 -> 杖主人客户端)。客户端缓存是 HUD/提示/
        // 工作台灵气判断的唯一显示来源,保证"工作台一把、手上一把"显示同一个数。
        net.registerMessage(WandPoolSyncPacket.class, WandPoolSyncPacket.Message.class, ids[0]++, Side.CLIENT);
    }

    public void init() {
        // 天玄·虚空电池合成表(2026-09-03 起可合成);其余 mod 物品仍无配方,
        // 通过任务书 / 奖励发放。守卫模式同 preInit 的电池注册。
        if (Loader.isModLoaded("gregtech") && Loader.isModLoaded("IC2")) {
            WirelessBatteryRecipes.register();
        }
        // 恒温核心配方(9 青金石块)只需要 IC2 存在即可。
        if (Loader.isModLoaded("IC2")) {
            ReactorCoreRecipes.register();
            // 菲涅尔透镜:8 玻璃板围 1 块 IC2 中子反射板。
            FresnelLensRecipes.register();
        }
        // 高级装配线:机器本体在 preInit 由 ElinarMachines 注册,这里只补配方
        // (原型配方,搬到 ElinarCurios 后保持与独立验证版一致;正式平衡配方另行替换)。
        if (Loader.isModLoaded("gregtech")) {
            HighTierAssemblyLineRecipes.register();
        }
    }

    public void postInit() {
        // StructureLib 的「多方块机器全息投影仪」在生存自动搭建时通过 InventoryUtility 的注册表
        // 寻找方块来源。把无尽背包注册进去（穿在身上 / 放在物品栏两种形态都覆盖）。
        // 守卫必须写在调用点：StructureLibCompat 在类加载阶段就引用 structurelib 的类型，
        // 类一旦被加载就会解析符号，方法体里的守卫来不及生效。
        if (Loader.isModLoaded("structurelib")) {
            StructureLibCompat.register();
        }
    }

    // ---- Client-only hooks (overridden in ClientProxy) ----
    // The packet handlers must not reference client-only classes directly:
    // FML instantiates every registered handler on BOTH sides during preInit,
    // and loading e.g. net.minecraft.client.Minecraft on a dedicated server
    // trips FML's SideTransformer ("Attempted to load class ... for invalid
    // side SERVER"). All client-side packet handling therefore delegates to
    // the sided proxy; these no-op bodies run on the server.

    /** Server -> client worn-bag sync (back rendering + wearing state). */
    public void handleSyncBagMessage(final int entityId, final NBTTagCompound data) {}

    /** Server -> client shared wand pool (display cache of the local player). */
    public void handleWandPoolSync(final java.util.Map<String, Long> values, final String ownerName) {}

    // ---- Wand pool client cache (overridden in ClientProxy) ----
    public boolean hasClientWandPool() {
        return false;
    }

    public long getClientWandPool(final String tag) {
        return 0L;
    }

    public java.util.Map<String, Long> getClientWandPoolMap() {
        return java.util.Collections.emptyMap();
    }

    public String getClientWandPoolOwner() {
        return null;
    }

    /** Server -> client buff-flag refresh for the open backpack GUI. */
    public void refreshBuffButtonsIfOpen() {}

    /** Server -> client page highlight refresh for the open backpack GUI. */
    public void refreshPageButtonsIfOpen(final int page) {}

    public EntityPlayer getPlayerEntity(final MessageContext context) {
        return context.getServerHandler().playerEntity;
    }

    public EntityPlayer getEntityPlayerFromContext(final MessageContext ctx) {
        return ctx.getServerHandler().playerEntity;
    }

    public static boolean isClient() {
        return FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient();
    }

    public static boolean isServer() {
        return FMLCommonHandler.instance()
            .getEffectiveSide()
            .isServer();
    }
}
