package com.os.core.interfaces;

import com.os.core.models.PCB;
import com.os.core.models.Instruction;
import com.os.core.models.Device;
import com.os.core.models.PageTableEntry;
import com.os.core.models.ProcessTickResult;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 操作系统内核总控制器
 * 统一管理所有子系统模块，提供协调服务
 */
public interface OSKernel {
    
    // ========== 系统控制 ==========
    
    /**
     * 初始化操作系统内核
     * @return 初始化结果
     */
    boolean initialize();
    
    /**
     * 启动系统
     * @return 启动结果
     */
    boolean start();
    
    /**
     * 停止系统
     * @param force 是否强制停止
     * @return 停止结果
     */
    boolean stop(boolean force);
    
    /**
     * 重置系统
     * @param keepFileSystem 是否保留文件系统
     * @return 重置结果
     */
    boolean reset(boolean keepFileSystem);
    
    /**
     * 获取系统运行状态
     * @return 是否运行中
     */
    boolean isRunning();
    
    /**
     * 系统时钟滴答
     * @return 时钟滴答结果
     */
    Map<String, Object> systemTick();
    
    // ========== 子系统访问 ==========
    
    /**
     * 获取文件系统接口
     * @return 文件系统API
     */
    FileSystemAPI getFileSystem();
    
    /**
     * 获取进程管理器接口
     * @return 进程管理API
     */
    ProcessManagerAPI getProcessManager();
    
    /**
     * 获取内存管理器接口
     * @return 内存管理API
     */
    MemoryManagerAPI getMemoryManager();
    
    /**
     * 获取设备管理器接口
     * @return 设备管理API
     */
    DeviceManagerAPI getDeviceManager();
    
    // ========== 进程执行 ==========
    
    /**
     * 创建并运行进程
     * @param executablePath 可执行文件路径
     * @return 进程创建结果
     */
    Map<String, Object> createAndRunProcess(String executablePath);
    
    /**
     * 单步执行
     * @return 执行结果
     */
    Map<String, Object> stepExecution();
    
    /**
     * 执行指令
     * @param process 进程
     * @param instruction 指令
     * @return 执行结果
     */
    Map<String, Object> executeInstruction(PCB process, Instruction instruction);
    
    /**
     * 处理程序结束
     * @param process 进程
     * @return 处理结果
     */
    Map<String, Object> handleProgramEnd(PCB process);

    /**
     * CPU()：课程要求的无参处理器仿真函数，返回一次调度后的结果快照。
     * @return ProcessTickResult 包含本次调度所产生的状态变化
     */
    ProcessTickResult CPU();
    
    // ========== 中断处理 ==========
    
    /**
     * 生成中断
     * @param type 中断类型
     * @param parameters 中断参数
     */
    void generateInterrupt(String type, Map<String, Object> parameters);
    
    /**
     * 处理中断
     * @param type 中断类型
     * @param parameters 中断参数
     * @return 处理结果
     */
    Map<String, Object> handleInterrupt(String type, Map<String, Object> parameters);
    
    /**
     * 启用中断
     */
    void enableInterrupts();
    
    /**
     * 禁用中断
     */
    void disableInterrupts();
    
    // ========== 系统状态查询 ==========
    
    /**
     * 获取系统信息
     * @return 系统信息
     */
    Map<String, Object> getSystemInfo();
    
    /**
     * 获取系统统计
     * @return 系统统计
     */
    Map<String, Object> getSystemStatistics();
    
    /**
     * 获取系统配置
     * @return 系统配置
     */
    Map<String, Object> getSystemConfig();
    
    /**
     * 获取系统健康状态
     * @return 健康状态
     */
    Map<String, Object> getSystemHealth();
    
    // ========== 性能监控 ==========
    
    /**
     * 获取性能指标
     * @return 性能指标
     */
    Map<String, Object> getPerformanceMetrics();
    
    /**
     * 获取资源使用情况
     * @return 资源使用情况
     */
    Map<String, Object> getResourceUsage();
    
    /**
     * 获取瓶颈分析
     * @return 瓶颈分析结果
     */
    Map<String, Object> getBottleneckAnalysis();
    
    // ========== 调试支持 ==========
    
    /**
     * 启用调试模式
     * @param level 调试级别
     */
    void enableDebugMode(int level);
    
    /**
     * 禁用调试模式
     */
    void disableDebugMode();
    
    /**
     * 获取调试信息
     * @return 调试信息
     */
    Map<String, Object> getDebugInfo();
    
    /**
     * 获取系统日志
     * @param level 日志级别
     * @param limit 条数限制
     * @return 日志列表
     */
    List<Map<String, Object>> getSystemLogs(String level, int limit);
    
    /**
     * 设置日志级别
     * @param level 日志级别
     */
    void setLogLevel(String level);
    
    // ========== 配置管理 ==========
    
    /**
     * 更新系统配置
     * @param config 配置项
     * @return 更新结果
     */
    boolean updateConfig(Map<String, Object> config);
    
    /**
     * 获取配置项
     * @param key 配置键
     * @return 配置值
     */
    Object getConfig(String key);
    
    /**
     * 设置配置项
     * @param key 配置键
     * @param value 配置值
     * @return 设置结果
     */
    boolean setConfig(String key, Object value);
    
    /**
     * 保存配置到文件
     * @param filePath 文件路径
     * @return 保存结果
     */
    boolean saveConfig(String filePath);
    
    /**
     * 从文件加载配置
     * @param filePath 文件路径
     * @return 加载结果
     */
    boolean loadConfig(String filePath);
    
    // ========== 事件通知 ==========
    
    /**
     * 注册事件监听器
     * @param eventType 事件类型
     * @param listener 监听器
     */
    void registerEventListener(String eventType, EventListener listener);
    
    /**
     * 注销事件监听器
     * @param eventType 事件类型
     * @param listener 监听器
     */
    void unregisterEventListener(String eventType, EventListener listener);
    
    /**
     * 触发事件
     * @param eventType 事件类型
     * @param eventData 事件数据
     */
    void fireEvent(String eventType, Map<String, Object> eventData);
    
    /**
     * 事件监听器接口
     */
    interface EventListener {
        void onEvent(String eventType, Map<String, Object> eventData);
    }
    
    // ========== 错误处理 ==========
    
    /**
     * 获取错误信息
     * @param errorCode 错误代码
     * @return 错误信息
     */
    String getErrorMessage(String errorCode);
    
    /**
     * 记录错误
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     * @param exception 异常对象
     */
    void logError(String errorType, String errorMessage, Exception exception);
    
    /**
     * 获取错误日志
     * @param limit 条数限制
     * @return 错误日志列表
     */
    List<Map<String, Object>> getErrorLogs(int limit);
    
    /**
     * 清空错误日志
     */
    void clearErrorLogs();
}