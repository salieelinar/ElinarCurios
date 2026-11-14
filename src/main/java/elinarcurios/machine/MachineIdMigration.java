package elinarcurios.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * MTE 号段迁移器（v1.0.0c2 过渡版专用）。
 *
 * <p>
 * 2026-11-14 与 TST 0.8.1 撞号（19082/19083）后，芷若拍板把整段机器搬到新号段
 * {@code 23100-23112}。这个类负责把存档里“旧号”的机器与物品就地改成新号：
 *
 * <ul>
 * <li>方块：TE NBT 的 {@code mID} 是唯一身份。用 {@code writeToNBT} +
 * {@code setInitialValuesAsNBT(nbt, newId)} 做一次“带状态的重建”（库存 / 能量 / 覆盖板 /
 * 自定义 NBT 全在同一个 compound 里往返）。</li>
 * <li>物品：{@code gt.blockmachines} 的 damage 就是 mID（{@code ItemMachines} 拿 damage
 * 查表），背包 / 箱子 / 机器内部库存 / 掉落物 / 物品 NBT 里嵌的机器（无尽背包等）都要改。</li>
 * <li>未加载区块：{@link #onChunkLoad} 在区块加载时补刀；{@link #onWorldTick} 每 5 秒兜底扫一遍。</li>
 * </ul>
 *
 * <p>
 * <b>为什么一定要“新旧号双注册”</b>：旧号一旦没人注册，GT 只会把 TE 锁死
 * （{@code MetaID X not loadable => locking TileEntity!}），而锁定状态的 TE 再存盘时
 * {@code saveMetaTileNBT} 不会写 Inventory —— 库存与机器数据就真丢了。
 *
 * <p>
 * <b>★ 换号只改 TE 的 {@code mID}，绝不重建 MTE 实例（2026-11-14 实测教训）</b>：
 * 早期实现用 {@code setInitialValuesAsNBT} 重建实例，重建时 GT 会把旧实例的
 * {@code baseMetaTileEntity} 置空，而 AE2 的 {@code GridNode}（持有 {@code IGridHost} =
 * MTE 实例）、GTNL 无线频道缓存等外部引用还指着旧实例 —— 网络一变更就
 * {@code NullPointerException: ... "base" is null}（实际崩溃现场：综合采掘工厂
 * {@code getLocation} → {@code AENetworkProxy.getLocation} → {@code Grid.add}）。
 * 现在改为 {@code BaseMetaTileEntity.setMetaTileID(short)}：实例、AE 节点、各种静态表引用
 * 全部原地保留；而 {@code getStackForm()} / {@code getDrops()} / 存盘读的都是
 * {@code getMetaTileID()}，所以立刻就是新号。
 *
 * <p>
 * 只有已经锁死（{@code getMetaTileEntity() == null}）的 TE 才走重建路径 —— 那种机器
 * 本来就没有实例可保留，AE 侧也不存在有效引用。
 *
 * <p>
 * 不处理的范围：AE2 存储单元（{@code AEItemStack} 是二进制序列化，改不了 NBT），
 * 迁移前请把 AE 里的机器方块取出来放在箱子/背包里，或者迁移后再取出来（取出时会被
 * {@link #onPlayerTick} 自动改正）。
 */
public final class MachineIdMigration {

    /** GT 机器方块的注册名；{@code ItemStack} NBT 里的 {@code id} 就是它。 */
    private static final String MACHINE_BLOCK_NAME = "gt.blockmachines";

    /** 递归修正物品 NBT 的最大深度（防止异常数据把服务器卡住）。 */
    private static final int MAX_NBT_DEPTH = 8;

    /** 兜底扫描间隔（tick）。 */
    private static final int WORLD_SWEEP_INTERVAL = 100;

    /** 玩家背包修正间隔（tick）。 */
    private static final int PLAYER_SWEEP_INTERVAL = 20;

    private static int worldTickCounter;
    private static int playerTickCounter;

    private MachineIdMigration() {}

    /** 迁移统计，供命令输出。 */
    public static final class Stats {

        public int worldsScanned;
        public int tilesMigrated;
        public int tilesLocked;
        public int stacksFixed;
        public int playersScanned;
    }

    // ------------------------------------------------------------------
    // 入口：命令 / 事件
    // ------------------------------------------------------------------

    /** 扫描服务端所有已加载世界：机器方块、机器物品、玩家背包、掉落物。 */
    public static Stats migrateAll() {
        final Stats stats = new Stats();
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return stats;
        for (World world : server.worldServers) {
            if (world == null || world.isRemote) continue;
            stats.worldsScanned++;
            migrateWorld(world, stats);
        }
        return stats;
    }

    /** 检查还有多少“旧号”残留（机器方块 + 物品），供 {@code /Elinar idcheck} 使用。 */
    public static List<String> checkAll() {
        final List<String> issues = new ArrayList<>();
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return issues;
        for (World world : server.worldServers) {
            if (world == null || world.isRemote) continue;
            for (Object o : world.loadedTileEntityList) {
                if (!(o instanceof IGregTechTileEntity)) continue;
                final IGregTechTileEntity gt = (IGregTechTileEntity) o;
                final int id = gt.getMetaTileID();
                if (!ElinarMachines.ID_MAP.containsKey(id)) continue;
                issues.add(
                    "维度 " + world.provider.dimensionId
                        + " @ "
                        + gt.getXCoord()
                        + " "
                        + gt.getYCoord()
                        + " "
                        + gt.getZCoord()
                        + "  mID="
                        + id);
            }
        }
        return issues;
    }

    /** 区块加载时补刀：把这块区块里的旧号机器就地进行迁移。 */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        final Chunk chunk = event.getChunk();
        if (chunk == null || chunk.worldObj == null || chunk.worldObj.isRemote) return;
        for (Object o : chunk.chunkTileEntityMap.values()) {
            if (o instanceof TileEntity) migrateTile((TileEntity) o, null);
        }
    }

    /** 每 5 秒兜底扫一遍已加载的机器（区块事件有时序边界，这里再兜一次）。 */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote) return;
        if ((++worldTickCounter % WORLD_SWEEP_INTERVAL) != 0) return;
        for (Object o : event.world.loadedTileEntityList) {
            if (o instanceof TileEntity) migrateTile((TileEntity) o, null);
        }
    }

    /** 玩家背包每 20 tick 修正一次：从 AE / 其它容器里取出来的旧号物品，落进背包就被改正。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        final EntityPlayer player = event.player;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if ((++playerTickCounter % PLAYER_SWEEP_INTERVAL) != 0) return;
        fixPlayer(player);
    }

    // ------------------------------------------------------------------
    // 世界 / 方块
    // ------------------------------------------------------------------

    private static void migrateWorld(World world, Stats stats) {
        for (Object o : world.loadedTileEntityList) {
            if (!(o instanceof TileEntity)) continue;
            final TileEntity te = (TileEntity) o;
            if (migrateTile(te, stats)) continue;
            fixTileInventories(te, stats);
        }
        for (Object o : world.loadedEntityList) {
            if (!(o instanceof EntityItem)) continue;
            stats.stacksFixed += fixStack(((EntityItem) o).getEntityItem());
        }
        for (Object o : world.playerEntities) {
            if (!(o instanceof EntityPlayer)) continue;
            stats.playersScanned++;
            stats.stacksFixed += fixPlayer((EntityPlayer) o);
        }
    }

    /**
     * 把一台旧号机器就地迁到新号；返回是否真的迁移了。
     *
     * <p>
     * 正常路径只调用 {@code setMetaTileID(short)} 改 TE 的号：<b>不重建 MTE 实例</b>，
     * 所以 AE2 的 GridNode / 合成任务 / 第三方静态缓存里对 MTE 实例的引用全部继续有效
     * （重建会让旧实例的 base 置空，之后 AE 网络一变动就 NPE，2026-11-14 实测）。
     * 只有已经锁死（没有 MTE 实例）的 TE 才走 {@code writeToNBT} + {@code setInitialValuesAsNBT}
     * 重建一次，把它救回来。
     */
    public static boolean migrateTile(TileEntity te, Stats stats) {
        if (!(te instanceof IGregTechTileEntity) || !(te instanceof BaseMetaTileEntity)) return false;
        final IGregTechTileEntity gt = (IGregTechTileEntity) te;
        final Integer newId = ElinarMachines.ID_MAP.get(gt.getMetaTileID());
        if (newId == null) return false;
        final BaseMetaTileEntity base = (BaseMetaTileEntity) te;
        final boolean wasLocked = base.getMetaTileEntity() == null;
        try {
            if (wasLocked) {
                // 锁死机器没有实例可保留，只能按新号重建（它的库存很可能在之前存盘时已经丢了）。
                final NBTTagCompound nbt = new NBTTagCompound();
                base.writeToNBT(nbt);
                nbt.setInteger("mID", newId.intValue());
                base.setInitialValuesAsNBT(nbt, (short) newId.intValue());
            } else {
                // 只改号、不换实例：AE 节点 / 静态注册表引用全部保持有效。
                base.setMetaTileID((short) newId.intValue());
            }
            base.markDirty();
        } catch (Throwable t) {
            // 单台失败不能拖垮整个迁移：记进日志，继续下一台。
            cpw.mods.fml.common.FMLLog
                .warning("[ElinarCurios] MTE 迁移失败 @" + base.xCoord + "/" + base.yCoord + "/" + base.zCoord + ": " + t);
            return false;
        }
        final World world = base.getWorld();
        if (world != null) world.markBlockForUpdate(base.xCoord, base.yCoord, base.zCoord);
        if (stats != null) {
            stats.tilesMigrated++;
            if (wasLocked) stats.tilesLocked++;
        }
        reRegisterLoadedInstance(base);
        fixTileInventories(te, stats);
        return true;
    }

    /** 锁死机器走重建路径时换了实例；按 UUID/坐标登记的静态表要换成新实例。 */
    private static void reRegisterLoadedInstance(BaseMetaTileEntity base) {
        final IMetaTileEntity mte = base.getMetaTileEntity();
        if (mte instanceof MTEComprehensiveMiningPlant) {
            MinerRegistry.register((MTEComprehensiveMiningPlant) mte);
        } else if (mte instanceof MTEBeamVoidStorage) {
            BeamVoidStorageRegistry.register((MTEBeamVoidStorage) mte);
        }
    }

    private static void fixTileInventories(TileEntity te, Stats stats) {
        if (te instanceof IGregTechTileEntity) {
            final IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            final ItemStack[] inv = mte == null ? null : mte.getRealInventory();
            if (inv != null) {
                for (ItemStack stack : inv) {
                    final int fixed = fixStack(stack);
                    if (stats != null) stats.stacksFixed += fixed;
                }
            }
        }
        if (!(te instanceof IInventory)) return;
        // AE 的容器有自己的库存一致性约束，不直接改；AE 里的物品走“取出时自动修正”这条路。
        if (te.getClass()
            .getName()
            .startsWith("appeng.")) return;
        final IInventory inv = (IInventory) te;
        try {
            final int size = inv.getSizeInventory();
            for (int i = 0; i < size; i++) {
                final ItemStack stack = inv.getStackInSlot(i);
                if (stack == null) continue;
                final int fixed = fixStack(stack);
                if (fixed <= 0) continue;
                inv.setInventorySlotContents(i, stack);
                if (stats != null) stats.stacksFixed += fixed;
            }
        } catch (Throwable ignored) {
            // 某些容器的读写有额外约束，跳过即可（物品会由玩家背包兜底）。
        }
    }

    // ------------------------------------------------------------------
    // 物品
    // ------------------------------------------------------------------

    /** 修正一个物品：自身是机器方块改 damage；NBT 里嵌着的机器（背包/子容器）递归改。 */
    public static int fixStack(ItemStack stack) {
        if (stack == null) return 0;
        int fixed = 0;
        final Integer newId = ElinarMachines.ID_MAP.get(stack.getItemDamage());
        if (newId != null && isMachineItem(stack.getItem())) {
            stack.setItemDamage(newId.intValue());
            fixed++;
        }
        fixed += fixTag(stack.getTagCompound(), 0);
        return fixed;
    }

    /** 修正玩家背包（主背包 + 护甲 + 光标上拿着的那个）。 */
    public static int fixPlayer(EntityPlayer player) {
        if (player == null || player.inventory == null) return 0;
        int fixed = 0;
        for (ItemStack stack : player.inventory.mainInventory) fixed += fixStack(stack);
        for (ItemStack stack : player.inventory.armorInventory) fixed += fixStack(stack);
        fixed += fixStack(player.inventory.getItemStack());
        return fixed;
    }

    private static boolean isMachineItem(Item item) {
        return item != null && item == Item.getItemFromBlock(GregTechAPI.sBlockMachines);
    }

    private static int fixTag(NBTTagCompound tag, int depth) {
        if (tag == null || depth > MAX_NBT_DEPTH) return 0;
        int fixed = 0;
        if (isMachineTag(tag)) {
            final Integer newId = ElinarMachines.ID_MAP.get(tag.getShort("Damage") & 0xFFFF);
            if (newId != null) {
                tag.setShort("Damage", (short) newId.intValue());
                fixed++;
            }
        }
        for (Object keyObj : tag.func_150296_c()) {
            fixed += fixBase(tag.getTag((String) keyObj), depth + 1);
        }
        return fixed;
    }

    private static int fixBase(NBTBase base, int depth) {
        if (base == null) return 0;
        if (base instanceof NBTTagCompound) return fixTag((NBTTagCompound) base, depth);
        if (base instanceof NBTTagList) {
            final NBTTagList list = (NBTTagList) base;
            int fixed = 0;
            for (int i = 0; i < list.tagCount(); i++) fixed += fixBase(list.getCompoundTagAt(i), depth + 1);
            return fixed;
        }
        return 0;
    }

    private static boolean isMachineTag(NBTTagCompound tag) {
        if (!tag.hasKey("id", 8) || !tag.hasKey("Damage")) return false;
        final String id = tag.getString("id");
        return MACHINE_BLOCK_NAME.equals(id) || id.endsWith(":" + MACHINE_BLOCK_NAME);
    }
}
