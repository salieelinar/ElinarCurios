package elinarcurios.machine;

/** 「束流虚空输入仓 / 输入室」共用的只读界面数据（给 {@code BeamVoidInputGui} 用）。 */
public interface IBeamVoidCollector {

    /** 界面标题（方块本地名）。 */
    String getGuiTitle();

    boolean isBound();

    int getLastParticleId();

    int getLastRate();

    float getLastEnergy();

    float getLastFocus();

    long getTotalReceived();

    String describeLink();

    String describeStatus();
}
