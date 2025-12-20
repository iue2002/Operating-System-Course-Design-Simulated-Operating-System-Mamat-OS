package com.os.core.interfaces;

import com.os.core.models.PCB;
import com.os.core.models.ProcessTickResult;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 进程管理接口
 * 定义进程管理操作的标准API
 */
public interface ProcessManagerAPI {
    
    // ========== 进程控制 ==========
    
    /**
     * 创建进程
     * @param executablePath 可执行文件路径
     * @param priority 进程优先级（可选）
     * @param args 命令行参数（可选）
     * @return 创建的进程信息
     */
    PCB createProcess(String executablePath, Integer priority, List<String> args);
    
    /**
     * 终止进程
     * @param pid 进程ID
     * @param force 是否强制终止
     * @return 终止结果
     */
    boolean terminateProcess(int pid, boolean force);
    
    /**
     * 阻塞进程
     * @param pid 进程ID
     * @param reason 阻塞原因
     * @return 阻塞结果
     */
    boolean blockProcess(int pid, String reason);
    
    /**
     * 唤醒进程
     * @param pid 进程ID
     * @return 唤醒结果
     */
    boolean wakeupProcess(int pid);
    
    // ========== 进程查询 ==========
    
    /**
     * 获取所有进程
     * @return 进程列表
     */
    List<PCB> getAllProcesses();
    
    /**
     * 获取指定进程
     * @param pid 进程ID
     * @return 进程信息
     */
    PCB getProcess(int pid);
    
    /**
     * 获取进程队列
     * @return 队列状态
     */
    Map<String, Object> getProcessQueues();
    
    /**
     * 获取就绪队列
     * @return 就绪队列
     */
    Queue<PCB> getReadyQueue();
    
    /**
     * 获取阻塞队列
     * @return 阻塞队列
     */
    Map<String, Queue<PCB>> getBlockedQueue();
    
    /**
     * 获取当前运行进程
     * @return 运行进程
     */
    PCB getRunningProcess();
    
    // ========== 进程调度 ==========
    
    /**
     * 执行进程调度
     * @return 当前运行进程
     */
    PCB schedule();
    
    /**
     * 时钟滴答处理
     * @return Tick结果（包含状态变化和潜在中断）
     */
    ProcessTickResult onTick();
    
    /**
     * 设置时间片大小
     * @param size 时间片大小（指令数）
     * @return 设置前的时间片大小
     */
    int setTimeSlice(int size);
    
    /**
     * 获取时间片大小
     * @return 时间片大小
     */
    int getTimeSlice();
    
    // ========== 进程状态 ==========
    
    /**
     * 保存进程上下文
     * @param process 进程
     */
    void saveProcessContext(PCB process);
    
    /**
     * 恢复进程上下文
     * @param process 进程
     */
    void restoreProcessContext(PCB process);
    
    /**
     * 更新进程运行时间
     * @param pid 进程ID
     */
    void updateProcessRunTime(int pid);
    
    /**
     * 设置进程状态
     * @param pid 进程ID
     * @param state 新状态
     */
    void setProcessState(int pid, PCB.ProcessState state);
    
    // ========== 队列管理 ==========
    
    /**
     * 添加进程到就绪队列
     * @param process 进程
     */
    void addToReadyQueue(PCB process);
    
    /**
     * 从就绪队列移除进程
     * @return 进程
     */
    PCB removeFromReadyQueue();
    
    /**
     * 添加进程到阻塞队列
     * @param process 进程
     * @param reason 阻塞原因
     */
    void addToBlockedQueue(PCB process, String reason);
    
    /**
     * 从阻塞队列移除进程
     * @param pid 进程ID
     * @param reason 阻塞原因
     * @return 移除的进程
     */
    PCB removeFromBlockedQueue(int pid, String reason);
    
    // ========== 统计信息 ==========
    
    /**
     * 获取进程数量
     * @return 各状态进程数量
     */
    Map<String, Integer> getProcessCount();
    
    /**
     * 获取系统统计信息
     * @return 统计信息
     */
    Map<String, Object> getSystemStatistics();
    
    /**
     * 获取进程历史信息
     * @param pid 进程ID
     * @return 历史信息
     */
    Map<String, Object> getProcessHistory(int pid);
    
    /**
     * 重置统计信息
     */
    void resetStatistics();
    
    // ========== 工具方法 ==========
    
    /**
     * 分配进程ID
     * @return 进程ID
     */
    int allocatePid();
    
    /**
     * 释放进程ID
     * @param pid 进程ID
     */
    void releasePid(int pid);
    
    /**
     * 检查进程是否存在
     * @param pid 进程ID
     * @return 是否存在
     */
    boolean processExists(int pid);
    
    /**
     * 获取进程执行进度
     * @param pid 进程ID
     * @return 执行进度（0-100%）
     */
    int getProcessProgress(int pid);
}