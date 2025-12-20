package com.os.process;

import com.os.core.models.PCB;
import java.util.List;

/**
 * 调度器接口（抽象调度策略）
 */
public interface Scheduler {
    /** 添加进程到就绪队列 */
    void add(PCB pcb);

    /** 返回下一个要运行的进程（并从就绪队列移除） */
    PCB next();

    /** 移除指定进程（例如终止时） */
    void remove(int pid);

    /** 清空就绪队列 */
    void clear();

    /** 获取就绪队列大小 */
    int size();

    /** 返回就绪队列的快照（不改变原队列） */
    default List<PCB> snapshot() {
        return java.util.Collections.emptyList();
    }
}

