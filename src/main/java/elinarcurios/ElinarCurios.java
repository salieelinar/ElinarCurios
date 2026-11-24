package elinarcurios;

import static elinarcurios.common.Reference.CLIENT_PROXY;
import static elinarcurios.common.Reference.DEPENDENCIES;
import static elinarcurios.common.Reference.MOD_ID;
import static elinarcurios.common.Reference.MOD_NAME;
import static elinarcurios.common.Reference.MOD_VERSION;
import static elinarcurios.common.Reference.SERVER_PROXY;

import java.util.Map;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.item.ItemInfiniteBackpack;

@Mod(modid = MOD_ID, name = MOD_NAME, version = MOD_VERSION, dependencies = DEPENDENCIES)
public final class ElinarCurios {

    @Mod.Instance
    public static ElinarCurios instance;

    public static SimpleNetworkWrapper networkWrapper;

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = SERVER_PROXY)
    public static CommonProxy proxy;

    public static final int GUI_ID_BAG_HOLDING = 0;
    public static final int GUI_ID_BAG_WEARING = 1;
    public static final int GUI_ID_ETERNAL_CHEST = 2;
    public static final int GUI_ID_VOID_CHEST = 3;
    public static final int GUI_ID_LUNCH_BOX = 4;

    public static final CreativeTabs creativeTab = new CreativeTabs(MOD_ID) {

        @SideOnly(Side.CLIENT)
        @Override
        public Item getTabIconItem() {
            return ItemInfiniteBackpack.instance;
        }
    };

    @Mod.EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        networkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(final FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(final FMLPostInitializationEvent event) {
        proxy.postInit();
        elinarcurios.machine.ElinarMachines.registerRecipes();
    }

    @Mod.EventHandler
    public void serverStarting(final FMLServerStartingEvent event) {
        event.registerServerCommand(new elinarcurios.command.CommandElinar());
    }

    /** 停服前把天玄之杖池的脏数据落盘（此时世界目录仍然有效）。 */
    @Mod.EventHandler
    public void serverStopping(final FMLServerStoppingEvent event) {
        elinarcurios.player.WandPoolStorage.saveIfDirty();
        if (cpw.mods.fml.common.Loader.isModLoaded("Thaumcraft")) {
            elinarcurios.player.ElinarWandLightHandler.removeAll();
        }
        // 巫术软联动：清掉无尽背包注册的随身能量源（整合服 statics 会跨存档存活）。
        if (cpw.mods.fml.common.Loader.isModLoaded("witchery")) {
            elinarcurios.compat.WitcheryPowerCompat.clearAll();
        }
        // 矿灯是真实世界方块，停服前移除，避免残留在存档里。
        elinarcurios.player.BackpackBuffHandler.removeAllLightsForServerStop();
    }

    /** 退档 / 服务器停止：清空束流虚空的 UUID 注册表，避免跨存档留下死引用；清掉杖池缓存。 */
    @Mod.EventHandler
    public void serverStopped(final FMLServerStoppedEvent event) {
        elinarcurios.machine.BeamVoidStorageRegistry.clear();
        elinarcurios.player.WandPoolStorage.reset();
    }

    @NetworkCheckHandler
    public boolean matchModVersions(final Map<String, String> remoteVersions, final Side side) {
        return side == Side.CLIENT ? remoteVersions.containsKey(MOD_ID)
            : !remoteVersions.containsKey(MOD_ID) || remoteVersions.get(MOD_ID)
                .equals(MOD_VERSION);
    }
}
