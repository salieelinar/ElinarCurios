package elinarcurios;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    /** Horizontal radius (blocks) of the Peace Totem's anti-spawn ellipsoid. */
    public static int peaceTotemRangeHorizontal;

    /** Vertical radius (blocks) of the Peace Totem's anti-spawn ellipsoid. */
    public static int peaceTotemRangeVertical;

    /**
     * GT++ ExxonMobil Chemical Plant: when true, the hatch tier cap imposed by the tier of the ten
     * internal machine casings is released, so a plant built with HV casings can accept an UEV
     * Crafting Input Buffer/Bus (ME). Recipe tiers (outer solid casings) are not affected.
     */
    public static boolean chemPlantRelaxHatchTier;

    /**
     * 菲涅尔透镜:每格增殖元件每个反应堆 tick(20 游戏刻 = 1 秒)吃到的中子脉冲次数。
     *
     * <p>
     * GT5U 增殖棒的进度公式是"每收一次脉冲 {@code + 1 + 堆芯热量/3000}",满 10000 出产物。
     * 原版燃料棒只给紧邻的四格各 1 次脉冲,所以默认值 4 相当于"把四邻的辐照一次性摊给全堆
     * 每一格、且完全均匀"(芷若 2026-10-13 拍板)。0 = 关闭辐照,只保留物品本体。
     */
    public static int fresnelLensPulsesPerTick;

    /**
     * 高级放射仓:原版放射仓的消耗周期倍率。10000 = 消耗速度降为万分之一(芷若 2026-10-31 拍板),
     * 也就是同一根辐射材料在仓里能撑 10000 倍的时间。改成 1 = 与原版一致;改成 5000 = 消耗速度
     * 降为五千分之一。改完对已经放好的高级放射仓立即生效(它每 tick 读一次配置),不用重新放置。
     */
    public static int advancedRadioHatchMultiplier;

    /**
     * 综合采掘工厂:矿石/流体先攒在机器内部缓存,每 N 秒批量倾倒进 AE 网络。
     *
     * <p>
     * 0 = 实时(每秒倒一次,旧行为);86400 = 一天一倒。默认 120 秒(芷若 2026-11-02 拍板),
     * 目的是把高频的小批量 AE 插入合并成低频的大批次,降低 AE/网络侧的压力。改成别的值需要
     * 重启客户端/服务端才生效(配置只在启动时读一次)。
     */
    public static int infMinerFlushSeconds;

    /**
     * 综合采掘工厂的默认产出模式（只对新放置的机器生效；已放置的机器按自身 NBT 里的
     * {@code miningMode}）。1 = 实时、2 = 汇集（默认）、3 = 结算，含义见
     * {@code MTEComprehensiveMiningPlant.MODE_REALTIME/MODE_ACCUMULATE/MODE_SETTLE}。
     */
    public static int infMinerMode;

    /**
     * ME 输出总线/输出仓：玩家下单期间的"当 tick 刷盘"。
     *
     * <p>
     * GT5U 的 Output Bus (ME) / Output Hatch (ME) 每 {@code refreshTime}(默认 40 tick = 2 秒)才把缓存
     * 整包灌进 AE，而 2 秒是写死的下限（GUI/闪存粘贴都会被 {@code Math.max(40, x)} 夹回去）。开着本项时，
     * 只要"这台多方块正在干活"且"它那张 AE 网上有玩家下的合成单"，就由 ElinarCurios 每 tick 替它把有货的
     * 缓存刷掉；机器停手满 {@link #meOutFastFlushHoldTicks} 后不再插手，GT 自己的 2 秒定时器照旧兜底。
     *
     * <p>
     * 只认玩家单（`CraftingCPUCluster` 记录的 `PlayerSource`）：AE2FC 的库存维持器走 `MachineSource`，
     * 不会触发快刷。玩家在终端 / ECO 合成确认框 / GTNL 量子计算机界面下的单都算玩家单。
     */
    public static boolean meOutFastFlushEnabled;

    /**
     * 机器"停手"后还继续快刷多少 tick（默认 100 = 5 秒）。目的是把一单的最后几批产物也立刻送进 AE，
     * 而不是等 GT 的下一个 2 秒周期。0 = 只在机器真正干活的 tick 快刷。
     */
    public static int meOutFastFlushHoldTicks;

    /**
     * 彩蛋（默认关闭）：开启后，净水厂七级（脱气）与八级（重子完美）净化单元的最终成功率恒为 100%。
     * 默认 false = 完全原版行为，不修改任何东西；改完需重启游戏。
     */
    public static boolean purificationTier78AlwaysSucceed;

    /** 配置文件句柄（供运行期改默认值时写回 cfg 用）。 */
    private static Configuration configFile;

    /**
     * 记住综合采掘工厂的默认产出模式并写回 {@code config\ElinarCurios.cfg}
     * （{@code /Elinar setore mod1|mod2|mod3} 用）：这样之后新放的机器也会跟着新档位。
     */
    public static void setInfMinerMode(int mode) {
        infMinerMode = Math.max(1, Math.min(3, mode));
        if (configFile == null) return;
        configFile.get(Configuration.CATEGORY_GENERAL, "infMinerMode", 2)
            .set(infMinerMode);
        configFile.save();
    }

    /**
     * 触发本类的静态初始化(也就是读写 {@code ElinarCurios.cfg})。
     *
     * <p>
     * 本类的静态字段目前没有任何跨类引用({@code Config} 的旧字段都是"被别处读"却没有
     * 主动加载它的调用点),所以必须由注册流程显式触碰一次,否则配置文件会拖到第一个访问者
     * 出现时才生成。{@code CommonProxy.preInit} 在注册完 IC2 元件后调用它。
     */
    public static void ensureLoaded() {}

    static {
        // The backpack currently grants a single passive buff (Night Vision),
        // which is toggled from the backpack GUI; no backpack options yet.
        final Configuration config = new Configuration(new File("config" + File.separatorChar + "ElinarCurios.cfg"));
        configFile = config;
        peaceTotemRangeHorizontal = config.getInt(
            "peaceTotemRangeHorizontal",
            Configuration.CATEGORY_GENERAL,
            64,
            1,
            512,
            "Horizontal radius (blocks) in which a Peace Totem blocks natural hostile-mob spawning.");
        peaceTotemRangeVertical = config.getInt(
            "peaceTotemRangeVertical",
            Configuration.CATEGORY_GENERAL,
            32,
            1,
            256,
            "Vertical radius (blocks) in which a Peace Totem blocks natural hostile-mob spawning.");
        chemPlantRelaxHatchTier = config.getBoolean(
            "chemPlantRelaxHatchTier",
            Configuration.CATEGORY_GENERAL,
            true,
            "GT++ ExxonMobil Chemical Plant: release the hatch tier cap imposed by the tier of the internal "
                + "machine casings, so a plant built with HV casings can accept an UEV Crafting Input Buffer/Bus "
                + "(ME). Recipe tiers (outer solid casings) and the ten-same-tier casing requirement are unaffected.");
        fresnelLensPulsesPerTick = config.getInt(
            "fresnelLensPulsesPerTick",
            Configuration.CATEGORY_GENERAL,
            4,
            0,
            64,
            "Fresnel Lens: neutron pulses delivered to EVERY breeder cell in the reactor per reactor tick "
                + "(20 game ticks = 1 s). GT5U breeder cells gain 1 + hullHeat/3000 progress per pulse and "
                + "need 10000, so 4 means the whole reactor is irradiated evenly at four times the pulse "
                + "count a single vanilla fuel rod hands to each of its four neighbours. 0 disables the lens.");
        advancedRadioHatchMultiplier = config.getInt(
            "advancedRadioHatchMultiplier",
            Configuration.CATEGORY_GENERAL,
            10000,
            1,
            1000000,
            "Advanced Radio Hatch: multiplier applied to every radioactive item's decay time (the original "
                + "Radio Hatch consumes one mass point per calcDecayTicks(SV) ticks). 10000 = consumption is "
                + "1/10000 of vanilla (e.g. Americium rod 59.9 s -> 599000 s). 1 = same as vanilla. Takes "
                + "effect immediately on placed hatches.");
        infMinerFlushSeconds = config.getInt(
            "infMinerFlushSeconds",
            Configuration.CATEGORY_GENERAL,
            120,
            0,
            86400,
            "Comprehensive Mining Plant: ore/fluid output is accumulated in the machine's internal buffer and "
                + "dumped into the AE network every N seconds. 0 = real time (flush every second, the old "
                + "behaviour), 86400 = once a day. Larger values mean fewer but bigger AE insertions; the buffer "
                + "is saved to NBT, so nothing is lost on world save/reload. Requires a game restart to change.");
        infMinerMode = config.getInt(
            "infMinerMode",
            Configuration.CATEGORY_GENERAL,
            2,
            1,
            3,
            "Comprehensive Mining Plant: default output mode for NEW machines only (placed machines keep their own "
                + "mode, saved in NBT). 1 = real time (settle and dump into AE every second), 2 = accumulate (produce "
                + "every second, dump every infMinerFlushSeconds), 3 = settle (produce and dump once per "
                + "infMinerFlushSeconds). In game: GUI parallel panel, or /Elinar setore mod1|mod2|mod3.");
        meOutFastFlushEnabled = config.getBoolean(
            "meOutFastFlushEnabled",
            Configuration.CATEGORY_GENERAL,
            true,
            "ME Output Bus/Hatch (GT5U): while a multiblock is working AND the AE network it is plugged into "
                + "has a crafting job placed by a player, ElinarCurios flushes that hatch's cached output every "
                + "tick instead of waiting for GT's hard-coded 2 second timer (refreshTime = 40 ticks, clamped in "
                + "setRefreshTime). Stock-keeper orders (AE2FC TileLevelMaintainer, a MachineSource) do NOT "
                + "trigger it; player orders do, including ECO's and GTNL's quantum CPUs (they all end up in "
                + "CraftingCPUCluster with a PlayerSource). Idle machines keep GT's original behaviour.");
        meOutFastFlushHoldTicks = config.getInt(
            "meOutFastFlushHoldTicks",
            Configuration.CATEGORY_GENERAL,
            100,
            0,
            24000,
            "How many ticks after a multiblock's last working tick the fast flush keeps going, so the last "
                + "batches of an order leave immediately instead of waiting for GT's next 2 second cycle. "
                + "100 = 5 seconds. 0 = only on ticks the machine actually works.");
        purificationTier78AlwaysSucceed = config.getBoolean(
            "purificationTier78AlwaysSucceed",
            Configuration.CATEGORY_GENERAL,
            false,
            "Easter egg: force the tier 7 (Degasser) and tier 8 (Baryonic Perfection) purification units of the "
                + "GT5U Water Purification Plant to always succeed (100%). Default false = vanilla behaviour, "
                + "nothing is modified. Requires a game restart after changing.");

        if (config.hasChanged()) config.save();
    }

    private Config() {}
}
