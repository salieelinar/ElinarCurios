package elinarcurios.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import elinarcurios.Config;
import elinarcurios.machine.MTEComprehensiveMiningPlant;
import elinarcurios.machine.MinerRegistry;
import elinarcurios.meout.MEOutputFlushHandler;
import elinarcurios.util.DimensionNames;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * {@code /Elinar} —— 综合采掘工厂的命令行入口。
 *
 * <ul>
 * <li>{@code /Elinar infiore}：列出所有（已加载的）综合采掘工厂：维度（中文名）+ 坐标 + 当前并行；</li>
 * <li>{@code /Elinar setore <流体并行> <矿石并行>}：批量改并行，带范围检测，改完立即生效；</li>
 * <li>{@code /Elinar setore mod1|mod2|mod3}：批量切换产出模式（实时 / 汇集 / 结算），并把该档写成新机器的默认档；</li>
 * <li>{@code /Elinar help}：命令格式提示。</li>
 * </ul>
 */
public class CommandElinar implements ICommand {

    private static final String USAGE = "/Elinar <infiore|setore|meflush|idmigrate|idcheck|help>";

    @Override
    public String getCommandName() {
        return "Elinar";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return new ArrayList<>();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("infiore".equals(sub) || "info".equals(sub)) {
            listMiners(sender);
            return;
        }
        if ("setore".equals(sub)) {
            setOre(sender, args);
            return;
        }
        if ("meflush".equals(sub)) {
            meFlushStatus(sender);
            return;
        }
        if ("idmigrate".equals(sub)) {
            machineIdMigrate(sender);
            return;
        }
        if ("idcheck".equals(sub)) {
            machineIdCheck(sender);
            return;
        }
        if ("help".equals(sub) || "?".equals(sub)) {
            sendHelp(sender);
            return;
        }
        send(sender, EnumChatFormatting.RED + "未知子命令：" + args[0]);
        sendHelp(sender);
    }

    // ------------------------------------------------------------------
    // /Elinar infiore
    // ------------------------------------------------------------------

    private void listMiners(ICommandSender sender) {
        List<MTEComprehensiveMiningPlant> miners = MinerRegistry.snapshot();
        if (miners.isEmpty()) {
            send(sender, EnumChatFormatting.GRAY + "综合采掘工厂：无（只统计已加载区块里的机器）");
            return;
        }
        send(sender, EnumChatFormatting.GOLD + "--- 综合采掘工厂（共 " + miners.size() + " 台）---");
        for (MTEComprehensiveMiningPlant miner : miners) {
            String line = describe(miner);
            if (line == null) continue;
            // 同时落日志：出问题时崩溃报告/日志里能直接对账
            System.out.println(line);
            send(sender, line);
        }
    }

    /** 一台机器的单行描述：维度（中文名）+ 坐标 + 并行。 */
    private String describe(MTEComprehensiveMiningPlant miner) {
        IGregTechTileEntity base = miner.getBaseMetaTileEntity();
        if (base == null) return null;
        World world = base.getWorld();
        String dim = String.valueOf(world == null ? 0 : world.provider.dimensionId);
        return "综合采掘工厂 " + dim
            + "（"
            + DimensionNames.of(world)
            + "） "
            + base.getXCoord()
            + " "
            + base.getYCoord()
            + " "
            + base.getZCoord()
            + "（坐标） 流体"
            + miner.getConfiguredFluid()
            + "X 矿石"
            + miner.getConfiguredOre()
            + "X";
    }

    // ------------------------------------------------------------------
    // /Elinar setore <流体并行> <矿石并行>
    // ------------------------------------------------------------------

    private void setOre(ICommandSender sender, String[] args) {
        // /Elinar setore mod1|mod2|mod3 —— 三个产出模式的全局切换（不是并行数值）。
        Integer mode = args.length >= 2 ? parseMode(args[1]) : null;
        if (mode != null) {
            setMode(sender, mode);
            return;
        }
        if (args.length < 3) {
            send(
                sender,
                EnumChatFormatting.RED + "用法：/Elinar setore <流体并行> <矿石并行>"
                    + "（范围：流体 0~"
                    + MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL
                    + "，矿石 1~"
                    + MTEComprehensiveMiningPlant.MAX_ORE_PARALLEL
                    + "）");
            send(sender, EnumChatFormatting.GRAY + "例：/Elinar setore 10 256");
            send(sender, EnumChatFormatting.GRAY + "切换产出模式：/Elinar setore mod1|mod2|mod3");
            return;
        }
        Long fluid = parseLong(args[1]);
        Long ore = parseLong(args[2]);
        if (fluid == null || ore == null) {
            send(
                sender,
                EnumChatFormatting.RED + "参数必须是整数：流体=" + args[1] + "，矿石=" + args[2] + "（例：/Elinar setore 10 256）");
            return;
        }
        // 顺序提示：第一位是流体，上限比矿石小得多，填反了很容易触发
        if (fluid > MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL && ore >= 0
            && ore <= MTEComprehensiveMiningPlant.MAX_ORE_PARALLEL) {
            send(
                sender,
                EnumChatFormatting.RED + "流体并行上限 "
                    + MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL
                    + "（你填了 "
                    + fluid
                    + "）。第一位是流体、第二位是矿石，是不是顺序反了？");
            return;
        }
        if (fluid < 0 || fluid > MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL) {
            send(
                sender,
                EnumChatFormatting.RED + "流体并行超出限制：0~"
                    + MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL
                    + "（你填了 "
                    + fluid
                    + "），未做任何修改");
            return;
        }
        if (ore < 1 || ore > MTEComprehensiveMiningPlant.MAX_ORE_PARALLEL) {
            send(
                sender,
                EnumChatFormatting.RED + "矿石并行超出限制：1~"
                    + MTEComprehensiveMiningPlant.MAX_ORE_PARALLEL
                    + "（你填了 "
                    + ore
                    + "），未做任何修改");
            return;
        }

        List<MTEComprehensiveMiningPlant> miners = MinerRegistry.snapshot();
        if (miners.isEmpty()) {
            send(sender, EnumChatFormatting.GRAY + "没有检测到综合采掘工厂（只统计已加载区块里的机器）");
            return;
        }
        int noEnergy = 0;
        for (MTEComprehensiveMiningPlant miner : miners) {
            miner.setConfiguredFluid(fluid.intValue());
            miner.setConfiguredOre(ore.intValue());
            if (!miner.applyConfiguredParallel()) noEnergy++;
        }
        long euPerTick = (fluid + ore) * MTEComprehensiveMiningPlant.EU_PER_PARALLEL;
        send(
            sender,
            EnumChatFormatting.GREEN + "已设置 "
                + miners.size()
                + " 台综合采掘工厂：流体 "
                + fluid
                + "X / 矿石 "
                + ore
                + "X（已立即生效，无需开关机；耗电 "
                + String.format(Locale.ROOT, "%,d", euPerTick)
                + " EU/t）");
        if (noEnergy > 0) {
            send(sender, EnumChatFormatting.YELLOW + "其中 " + noEnergy + " 台无线电网电量不足，暂按现有电量缓冲运行（并行调高后耗电变大，注意电网）");
        }
        for (MTEComprehensiveMiningPlant miner : miners) {
            String line = describe(miner);
            if (line != null) send(sender, line);
        }
    }

    // ------------------------------------------------------------------
    // /Elinar setore mod1|mod2|mod3
    // ------------------------------------------------------------------

    /** mod1/mod2/mod3 → 1/2/3；不是模式词返回 null。 */
    private Integer parseMode(String raw) {
        if (raw == null) return null;
        String token = raw.trim()
            .toLowerCase(Locale.ROOT);
        if ("mod1".equals(token)) return MTEComprehensiveMiningPlant.MODE_REALTIME;
        if ("mod2".equals(token)) return MTEComprehensiveMiningPlant.MODE_ACCUMULATE;
        if ("mod3".equals(token)) return MTEComprehensiveMiningPlant.MODE_SETTLE;
        return null;
    }

    private static String modeToken(int mode) {
        return "mod" + mode;
    }

    /** 模式的一句话说明（README 口径，和机器 GUI 一致）。 */
    private static String modeSummary(int mode) {
        int seconds = MTEComprehensiveMiningPlant.intervalSecondsFor(mode);
        switch (mode) {
            case MTEComprehensiveMiningPlant.MODE_REALTIME:
                return "实时：每秒结算并倾倒进 AE";
            case MTEComprehensiveMiningPlant.MODE_SETTLE:
                return "结算：每 " + seconds + " 秒结算一次并倾倒";
            default:
                return "汇集：每 " + seconds + " 秒批量倾倒一次";
        }
    }

    /**
     * 批量切换产出模式：把所有（已加载的）综合采掘工厂切到该档，并把该档写回 cfg 当作
     * 之后新放置机器的默认档。
     */
    private void setMode(ICommandSender sender, int mode) {
        Config.setInfMinerMode(mode);
        List<MTEComprehensiveMiningPlant> miners = MinerRegistry.snapshot();
        if (miners.isEmpty()) {
            send(
                sender,
                EnumChatFormatting.GREEN + "默认产出模式已改为 "
                    + modeToken(mode)
                    + "（"
                    + modeSummary(mode)
                    + "）；当前没有检测到综合采掘工厂（只统计已加载区块里的机器），之后新放的机器会用这个档位");
            return;
        }
        for (MTEComprehensiveMiningPlant miner : miners) {
            miner.setMode(mode);
        }
        send(
            sender,
            EnumChatFormatting.GREEN + "已把 "
                + miners.size()
                + " 台综合采掘工厂切到 "
                + modeToken(mode)
                + "（"
                + modeSummary(mode)
                + "），立即生效，无需开关机；该档已写回 config 作为新机器的默认档");
        for (MTEComprehensiveMiningPlant miner : miners) {
            String line = describe(miner);
            if (line != null) send(sender, line);
        }
    }

    // ------------------------------------------------------------------
    // /Elinar meflush
    // ------------------------------------------------------------------

    /** 打印 ME 输出快刷的开关、hold 窗口和上一 tick 的命中情况（验收/排错用）。 */
    private void meFlushStatus(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "===== ME 输出快刷（玩家下单期间当 tick 刷产）=====");
        send(
            sender,
            "开关：" + (Config.meOutFastFlushEnabled ? EnumChatFormatting.GREEN + "开启" : EnumChatFormatting.RED + "关闭")
                + EnumChatFormatting.GRAY
                + "（hold = "
                + Config.meOutFastFlushHoldTicks
                + " tick = "
                + String.format(Locale.ROOT, "%.2f", Config.meOutFastFlushHoldTicks / 20.0D)
                + " 秒）");
        send(
            sender,
            "上一 tick：有玩家单的 AE 网 " + MEOutputFlushHandler.lastTickPlayerJobGrids
                + " 张 / 在干活的多方块 "
                + MEOutputFlushHandler.lastTickWorkingMachines
                + " 台 / 实际刷出仓 "
                + MEOutputFlushHandler.lastTickFlushedHatches
                + " 个");
        send(sender, EnumChatFormatting.GRAY + "累计刷出：" + MEOutputFlushHandler.totalFlushedHatches + " 次");
        send(sender, EnumChatFormatting.DARK_GRAY + "口径：机器在干活（含停手后 hold 窗口）+ 这张网有玩家合成单，两个条件同时成立才快刷。");
        send(sender, EnumChatFormatting.DARK_GRAY + "库存维持器（AE2FC）走 MachineSource，不算玩家单；终端 / ECO / GTNL 量子计算机的单算。");
        send(sender, EnumChatFormatting.DARK_GRAY + "改 cfg 后需要重启客户端/服务端；hold 改 0 = 只在机器真正干活的 tick 快刷。");
    }

    // ------------------------------------------------------------------
    // /Elinar idmigrate —— 把存档里旧号段的机器搬到 23100-23112
    // ------------------------------------------------------------------

    private void machineIdMigrate(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "===== MTE 号段迁移（19082-19104 / 32030 → 23100-23112）=====");
        send(sender, EnumChatFormatting.GRAY + "扫描已加载的机器方块 / 机器物品 / 玩家背包 / 掉落物……");
        final elinarcurios.machine.MachineIdMigration.Stats stats = elinarcurios.machine.MachineIdMigration
            .migrateAll();
        send(
            sender,
            EnumChatFormatting.GREEN + "完成：迁移机器 "
                + stats.tilesMigrated
                + " 台 / 修正物品 "
                + stats.stacksFixed
                + " 个 / 扫描世界 "
                + stats.worldsScanned
                + " 个 / 玩家 "
                + stats.playersScanned
                + " 名");
        if (stats.tilesLocked > 0) {
            send(
                sender,
                EnumChatFormatting.RED + "其中 " + stats.tilesLocked + " 台在迁移前已经是「锁死」状态（旧号没注册过），它们的库存可能已经丢过一次，建议去坐标确认一下");
        }
        send(
            sender,
            EnumChatFormatting.DARK_GRAY + "未加载区块会在加载时自动迁移（每 5 秒兜底扫一遍）；AE 存储单元里的机器物品不会自动迁移——"
                + "请把它们取到背包/箱子里（取出时会被自动改成新号），或迁移前先取出来。");
        send(sender, EnumChatFormatting.DARK_GRAY + "全部迁完后建议存盘退出再重启一次（让按 UUID 登记的静态表重建）。");
    }

    // ------------------------------------------------------------------
    // /Elinar idcheck —— 检查还有没有旧号残留
    // ------------------------------------------------------------------

    private void machineIdCheck(ICommandSender sender) {
        final java.util.List<String> issues = elinarcurios.machine.MachineIdMigration.checkAll();
        if (issues.isEmpty()) {
            send(sender, EnumChatFormatting.GREEN + "没有检测到旧号残留（只统计已加载区块里的机器方块）");
            send(sender, EnumChatFormatting.DARK_GRAY + "物品残留靠背包/容器自动修正；离线的区块请去走一圈再查一次。");
            return;
        }
        send(sender, EnumChatFormatting.RED + "还有 " + issues.size() + " 台机器是旧号（前 20 条）：");
        for (int i = 0; i < Math.min(20, issues.size()); i++) {
            send(sender, EnumChatFormatting.GRAY + issues.get(i));
        }
        send(sender, EnumChatFormatting.YELLOW + "再执行一次 /Elinar idmigrate，或去对应坐标附近让区块加载后自动迁移。");
    }

    // ------------------------------------------------------------------
    // /Elinar help
    // ------------------------------------------------------------------

    private void sendHelp(ICommandSender sender) {
        send(sender, EnumChatFormatting.GOLD + "===== ElinarCurios 命令 =====");
        send(sender, EnumChatFormatting.YELLOW + "/Elinar infiore" + EnumChatFormatting.GRAY + " —— 查询所有综合采掘工厂");
        send(sender, EnumChatFormatting.DARK_GRAY + "  维度（中文名）+ 坐标 + 当前并行");
        send(
            sender,
            EnumChatFormatting.YELLOW + "/Elinar setore <流体并行> <矿石并行>" + EnumChatFormatting.GRAY + " —— 批量改并行");
        send(sender, EnumChatFormatting.DARK_GRAY + "  改完立即生效，不用关机开机");
        send(
            sender,
            EnumChatFormatting.DARK_GRAY + "  范围：流体 0~"
                + MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL
                + "，矿石 1~"
                + MTEComprehensiveMiningPlant.MAX_ORE_PARALLEL
                + "；例："
                + EnumChatFormatting.WHITE
                + "/Elinar setore 10 256");
        send(
            sender,
            EnumChatFormatting.YELLOW + "/Elinar setore mod1|mod2|mod3" + EnumChatFormatting.GRAY + " —— 批量切产出模式");
        send(sender, EnumChatFormatting.DARK_GRAY + "  mod1 实时（每秒倒）/ mod2 汇集（每 N 秒倒）/ mod3 结算（每 N 秒算一次）");
        send(sender, EnumChatFormatting.DARK_GRAY + "  立即生效；并把该档写回 config 作为新机器的默认档");
        send(sender, EnumChatFormatting.YELLOW + "/Elinar meflush" + EnumChatFormatting.GRAY + " —— 查看 ME 输出快刷状态");
        send(sender, EnumChatFormatting.DARK_GRAY + "  玩家下单期间，输出总线(ME)/输出仓(ME) 改为每 tick 刷，停机 5 秒后恢复 2 秒");
        send(sender, EnumChatFormatting.YELLOW + "/Elinar idmigrate" + EnumChatFormatting.GRAY + " —— 把旧号机器搬到新号段");
        send(sender, EnumChatFormatting.DARK_GRAY + "  19082-19104 / 32030 → 23100-23112（v1.0.0c2 过渡版专用，只用一次）");
        send(sender, EnumChatFormatting.YELLOW + "/Elinar idcheck" + EnumChatFormatting.GRAY + " —— 检查旧号残留");
        send(sender, EnumChatFormatting.YELLOW + "/Elinar help" + EnumChatFormatting.GRAY + " —— 显示本帮助");
    }

    private void send(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }

    private Long parseLong(String raw) {
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("infiore");
            options.add("setore");
            options.add("meflush");
            options.add("idmigrate");
            options.add("idcheck");
            options.add("help");
            return options;
        }
        if (args.length == 2 && "setore".equalsIgnoreCase(args[0])) {
            // 只给一个起点，免得补全列表把屏幕刷满
            options.add("mod1");
            options.add("mod2");
            options.add("mod3");
            options.add("0");
            options.add("10");
            return options;
        }
        if (args.length == 3 && "setore".equalsIgnoreCase(args[0])) {
            options.add("256");
            options.add("1024");
            return options;
        }
        return options;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }
}
