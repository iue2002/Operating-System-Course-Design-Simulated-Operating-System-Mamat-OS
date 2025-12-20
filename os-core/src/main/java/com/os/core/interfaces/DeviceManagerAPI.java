package com.os.core.interfaces;

import com.os.core.models.Device;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 设备管理接口
 * 定义设备管理操作的标准API
 */
public interface DeviceManagerAPI {
    
    // ========== 设备操作 ==========
    
    /**
     * 请求设备
     * @param pid 进程ID
     * @param deviceType 设备类型 (A/B/C)
     * @param useTime 使用时间
     * @return 设备请求信息
     */
    Map<String, Object> requestDevice(int pid, char deviceType, int useTime);
    
    /**
     * 释放设备
     * @param pid 进程ID
     * @param deviceType 设备类型
     * @return 释放结果
     */
    boolean releaseDevice(int pid, char deviceType);
    
    /**
     * 强制释放设备
     * @param deviceType 设备类型
     * @return 释放结果
     */
    boolean forceReleaseDevice(char deviceType);
    
    // ========== 设备状态查询 ==========
    
    /**
     * 获取设备状态
     * @param deviceType 设备类型
     * @return 设备状态信息
     */
    Map<String, Object> getDeviceStatus(char deviceType);
    
    /**
     * 获取所有设备状态
     * @return 所有设备状态列表
     */
    List<Map<String, Object>> getAllDeviceStatus();
    
    /**
     * 获取设备信息
     * @param deviceType 设备类型
     * @return 设备详细信息
     */
    Device getDevice(char deviceType);
    
    /**
     * 获取所有设备
     * @return 设备列表
     */
    List<Device> getAllDevices();
    
    // ========== 等待队列管理 ==========
    
    /**
     * 获取设备等待队列
     * @param deviceType 设备类型
     * @return 等待队列
     */
    Queue<Integer> getDeviceWaitQueue(char deviceType);
    
    /**
     * 获取所有等待队列
     * @return 所有等待队列
     */
    Map<Character, Queue<Integer>> getAllWaitQueues();
    
    /**
     * 获取等待队列长度
     * @param deviceType 设备类型
     * @return 队列长度
     */
    int getWaitQueueLength(char deviceType);
    
    /**
     * 检查进程是否在等待队列中
     * @param pid 进程ID
     * @param deviceType 设备类型
     * @return 是否在等待队列中
     */
    boolean isInWaitQueue(int pid, char deviceType);
    
    // ========== 安全分配算法 ==========
    
    /**
     * 检查安全状态
     * @param deviceType 设备类型
     * @param requestTime 请求时间
     * @param requestingPid 请求进程ID
     * @return 是否安全
     */
    boolean isSafeState(char deviceType, int requestTime, int requestingPid);
    
    /**
     * 查找安全序列
     * @param currentAllocation 当前分配情况
     * @param requests 请求列表
     * @return 安全序列
     */
    List<Integer> findSafeSequence(Map<Integer, Map<Character, Integer>> currentAllocation, 
                               Map<Integer, Map<Character, Integer>> requests);
    
    /**
     * 执行安全分配检查
     * @param pid 进程ID
     * @param deviceType 设备类型
     * @param useTime 使用时间
     * @return 分配结果和安全性信息
     */
    Map<String, Object> safeAllocate(int pid, char deviceType, int useTime);
    
    // ========== 时钟驱动 ==========
    
    /**
     * 时钟滴答处理
     * @return 完成的I/O操作列表
     */
    List<Map<String, Object>> onTick();
    
    /**
     * 更新所有设备状态
     */
    void updateAllDevices();
    
    /**
     * 检查I/O完成
     * @return 完成的I/O操作
     */
    List<Map<String, Object>> checkIOCompletion();
    
    /**
     * 处理设备等待队列
     * @param deviceType 设备类型
     * @return 唤醒的进程列表
     */
    List<Integer> processWaitQueue(char deviceType);
    
    // ========== 设备独立性 ==========
    
    /**
     * 逻辑设备到物理设备映射
     * @param logicalDevice 逻辑设备名
     * @return 物理设备
     */
    Device mapLogicalToPhysical(char logicalDevice);
    
    /**
     * 获取可用设备列表
     * @param deviceType 设备类型（可选）
     * @return 可用设备列表
     */
    List<Device> getAvailableDevices(Character deviceType);
    
    /**
     * 检查设备类型有效性
     * @param deviceType 设备类型
     * @return 是否有效
     */
    boolean isValidDeviceType(char deviceType);
    
    // ========== 进程设备管理 ==========
    
    /**
     * 获取进程占用设备
     * @param pid 进程ID
     * @return 占用设备列表
     */
    List<Character> getProcessDevices(int pid);
    
    /**
     * 释放进程所有设备
     * @param pid 进程ID
     * @return 释放结果
     */
    boolean releaseAllProcessDevices(int pid);
    
    /**
     * 获取进程设备请求历史
     * @param pid 进程ID
     * @return 请求历史
     */
    List<Map<String, Object>> getProcessDeviceHistory(int pid);
    
    // ========== 统计信息 ==========
    
    /**
     * 获取设备利用率
     * @param deviceType 设备类型
     * @return 利用率（0-100%）
     */
    double getDeviceUtilization(char deviceType);
    
    /**
     * 获取所有设备利用率
     * @return 利用率映射
     */
    Map<Character, Double> getAllDeviceUtilization();
    
    /**
     * 获取平均等待时间
     * @param deviceType 设备类型
     * @return 平均等待时间
     */
    double getAverageWaitTime(char deviceType);
    
    /**
     * 获取设备使用统计
     * @param deviceType 设备类型
     * @return 使用统计
     */
    Map<String, Object> getDeviceStatistics(char deviceType);
    
    /**
     * 获取系统设备统计
     * @return 系统统计
     */
    Map<String, Object> getSystemStatistics();
    
    /**
     * 重置设备统计
     * @param deviceType 设备类型（可选，null表示重置所有）
     */
    void resetDeviceStatistics(Character deviceType);
    
    // ========== 系统管理 ==========
    
    /**
     * 初始化设备管理器
     * @return 初始化结果
     */
    boolean initialize();
    
    /**
     * 重置设备管理器
     * @return 重置结果
     */
    boolean reset();
    
    /**
     * 关闭所有设备
     * @return 关闭结果
     */
    boolean shutdown();
    
    /**
     * 检查设备一致性
     * @return 检查结果
     */
    Map<String, Object> checkDeviceConsistency();
    
    /**
     * 获取设备配置信息
     * @return 配置信息
     */
    Map<String, Object> getDeviceConfig();
    
    // ========== 故障处理 ==========
    
    /**
     * 设置设备故障状态
     * @param deviceType 设备类型
     * @param fault 是否故障
     * @return 设置结果
     */
    boolean setDeviceFault(char deviceType, boolean fault);
    
    /**
     * 设置设备维护状态
     * @param deviceType 设备类型
     * @param maintenance 是否维护
     * @return 设置结果
     */
    boolean setDeviceMaintenance(char deviceType, boolean maintenance);
    
    /**
     * 从故障中恢复设备
     * @param deviceType 设备类型
     * @return 恢复结果
     */
    boolean recoverDevice(char deviceType);
}