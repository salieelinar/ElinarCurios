package elinarcurios.machine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.World;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 当前存档内所有综合采掘工厂的登记表（供 {@code /Elinar infiore} 与 {@code /Elinar setore} 使用）。
 *
 * <p>
 * 只登记已加载的机器：机器被拆或区块卸载时由 {@link #unregister} 摘掉。静态表跨存档/跨维度
 * 不会自动清空，所以 {@link #snapshot()} 会顺手用 {@code MetaTileEntity.isValid()} 做一次
 * 兜底清理，防止换存档后残留的旧对象被命令改到。
 */
public final class MinerRegistry {

    private static final Map<String, MTEComprehensiveMiningPlant> MACHINES = new LinkedHashMap<>();

    private MinerRegistry() {}

    public static synchronized void register(MTEComprehensiveMiningPlant machine) {
        String key = keyOf(machine);
        if (key != null) MACHINES.put(key, machine);
    }

    /** 按对象身份摘除（{@code onRemoval} 里坐标/世界可能已经取不到了）。 */
    public static synchronized void unregister(MTEComprehensiveMiningPlant machine) {
        Iterator<Map.Entry<String, MTEComprehensiveMiningPlant>> it = MACHINES.entrySet()
            .iterator();
        while (it.hasNext()) {
            if (it.next()
                .getValue() == machine) it.remove();
        }
    }

    /** 存活机器快照（顺手清掉已失效的登记项），顺序 = 登记顺序。 */
    public static synchronized List<MTEComprehensiveMiningPlant> snapshot() {
        List<MTEComprehensiveMiningPlant> alive = new ArrayList<>(MACHINES.size());
        Iterator<Map.Entry<String, MTEComprehensiveMiningPlant>> it = MACHINES.entrySet()
            .iterator();
        while (it.hasNext()) {
            MTEComprehensiveMiningPlant machine = it.next()
                .getValue();
            if (isAlive(machine)) alive.add(machine);
            else it.remove();
        }
        return alive;
    }

    /**
     * GT 的 {@code BaseMetaTileEntity.invalidate()} 里 {@code onRemoval()} 之后会把 MTE 的 base 引用置空
     * （而且它会顺手把 {@code tileEntityInvalid} 复位成 false，所以 {@code isInvalid()} 不可靠）。
     * 这里用 GT 自己的 {@code MetaTileEntity.isValid()}：base 非空 + {@code getMetaTileEntity() == this} + 未 dead。
     */
    private static boolean isAlive(MTEComprehensiveMiningPlant machine) {
        if (machine == null || !machine.isValid()) return false;
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        return base != null && base.getWorld() != null;
    }

    private static String keyOf(MTEComprehensiveMiningPlant machine) {
        if (machine == null) return null;
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null) return null;
        World world = base.getWorld();
        if (world == null) return null;
        return world.provider.dimensionId + ":" + base.getXCoord() + ":" + base.getYCoord() + ":" + base.getZCoord();
    }
}
