package com.os.core.interfaces;

import com.os.core.models.PageTableEntry;
import java.util.List;
import java.util.Map;

/**
 * 内存管理接口
 * 定义内存管理操作的标准API
 */
public interface MemoryManagerAPI {
    
    // ========== 内存分配 ==========
    
    /**
     * 为进程分配内存页
     * @param pid 进程ID
     * @param pageCount 页面数量
     * @param logicalPages 指定逻辑页号（可选）
     * @return 内存分配信息
     */
    Map<String, Object> allocatePages(int pid, int pageCount, List<Integer> logicalPages);
    
    /**
     * 释放进程内存
     * @param pid 进程ID
     * @param logicalPages 指定释放的逻辑页（可选）
     * @return 释放的页面数
     */
    int freeMemory(int pid, List<Integer> logicalPages);
    
    /**
     * 分配单个内存页
     * @param pid 进程ID
     * @param logicalPage 逻辑页号
     * @return 物理页框号，失败返回-1
     */
    int allocatePage(int pid, int logicalPage);
    
    /**
     * 释放单个内存页
     * @param pid 进程ID
     * @param logicalPage 逻辑页号
     * @return 释放结果
     */
    boolean freePage(int pid, int logicalPage);

    /**
     * 为系统区（PCB/页表等内核结构）分配页面
     * @param pid 进程ID
     * @return 物理页框号，失败返回 -1
     */
    int allocateSystemPage(int pid);

    /**
     * 释放系统区页面
     * @param pid 进程ID
     * @return 是否释放成功
     */
    boolean releaseSystemPage(int pid);

    /**
     * 获取系统区剩余页面数量
     * @return 剩余系统页数
     */
    int getFreeSystemPageCount();
    
    // ========== 地址转换 ==========
    
    /**
     * 逻辑地址到物理地址转换
     * @param pid 进程ID
     * @param logicalAddress 逻辑地址
     * @return 地址转换信息
     */
    Map<String, Object> translateAddress(int pid, int logicalAddress);
    
    /**
     * 检查地址有效性
     * @param pid 进程ID
     * @param logicalAddress 逻辑地址
     * @return 是否有效
     */
    boolean isValidAddress(int pid, int logicalAddress);
    
    /**
     * 获取物理地址
     * @param pid 进程ID
     * @param logicalAddress 逻辑地址
     * @return 物理地址，失败返回-1
     */
    int getPhysicalAddress(int pid, int logicalAddress);
    
    // ========== 页表管理 ==========
    
    /**
     * 创建进程页表
     * @param pid 进程ID
     * @param pageCount 页面数量
     * @return 页表位置
     */
    int createPageTable(int pid, int pageCount);
    
    /**
     * 销毁进程页表
     * @param pid 进程ID
     * @return 销毁结果
     */
    boolean destroyPageTable(int pid);
    
    /**
     * 获取进程页表
     * @param pid 进程ID
     * @return 页表项列表
     */
    List<PageTableEntry> getPageTable(int pid);
    
    /**
     * 更新页表项
     * @param pid 进程ID
     * @param logicalPage 逻辑页号
     * @param entry 页表项
     * @return 更新结果
     */
    boolean updatePageTableEntry(int pid, int logicalPage, PageTableEntry entry);
    
    /**
     * 获取页表项
     * @param pid 进程ID
     * @param logicalPage 逻辑页号
     * @return 页表项
     */
    PageTableEntry getPageTableEntry(int pid, int logicalPage);
    
    // ========== 内存状态查询 ==========
    
    /**
     * 获取内存使用状态
     * @return 内存状态信息
     */
    Map<String, Object> getMemoryStatus();
    
    /**
     * 获取空闲页面列表
     * @return 空闲页框号列表
     */
    List<Integer> getFreePages();
    
    /**
     * 获取进程内存使用情况
     * @param pid 进程ID
     * @return 内存使用信息
     */
    Map<String, Object> getProcessMemoryUsage(int pid);
    
    /**
     * 检查内存是否充足
     * @param requiredPages 需要的页面数
     * @return 是否充足
     */
    boolean hasEnoughMemory(int requiredPages);
    
    /**
     * 获取内存碎片信息
     * @return 碎片信息
     */
    Map<String, Object> getFragmentationInfo();
    
    // ========== 空闲页管理 ==========
    
    /**
     * 从空闲页列表分配页面
     * @return 物理页框号，失败返回-1
     */
    int allocateFreePage();
    
    /**
     * 释放页面到空闲列表
     * @param physicalPage 物理页框号
     * @return 释放结果
     */
    boolean releaseFreePage(int physicalPage);
    
    /**
     * 查找连续空闲页面
     * @param count 页面数量
     * @return 起始页框号，失败返回-1
     */
    int findConsecutiveFreePages(int count);
    
    /**
     * 更新空闲页位图
     * @param physicalPage 物理页框号
     * @param allocated 是否已分配
     */
    void updateFreePageBitmap(int physicalPage, boolean allocated);
    
    // ========== TLB管理 ==========
    
    /**
     * 查找TLB缓存
     * @param pid 进程ID
     * @param logicalPage 逻辑页号
     * @return 物理页框号，未命中返回-1
     */
    int lookupTLB(int pid, int logicalPage);
    
    /**
     * 更新TLB缓存
     * @param pid 进程ID
     * @param logicalPage 逻辑页号
     * @param physicalPage 物理页框号
     * @return 更新结果
     */
    boolean updateTLB(int pid, int logicalPage, int physicalPage);
    
    /**
     * 清空TLB缓存
     */
    void clearTLB();
    
    /**
     * 获取TLB命中率
     * @return 命中率（0-100%）
     */
    double getTLBHitRate();
    
    // ========== 系统管理 ==========
    
    /**
     * 初始化内存管理器
     * @return 初始化结果
     */
    boolean initialize();
    
    /**
     * 重置内存状态
     * @return 重置结果
     */
    boolean reset();
    
    /**
     * 检查内存一致性
     * @return 检查结果
     */
    Map<String, Object> checkMemoryConsistency();
    
    /**
     * 获取内存配置信息
     * @return 配置信息
     */
    Map<String, Object> getMemoryConfig();
    
    // ========== 性能统计 ==========
    
    /**
     * 获取分配统计
     * @return 分配统计信息
     */
    Map<String, Object> getAllocationStatistics();
    
    /**
     * 获取TLB统计
     * @return TLB统计信息
     */
    Map<String, Object> getTLBStatistics();
    
    /**
     * 获取性能指标
     * @return 性能指标
     */
    Map<String, Object> getPerformanceMetrics();
    
    // ========== 工具方法 ==========
    
    /**
     * 分解逻辑地址
     * @param logicalAddress 逻辑地址
     * @return [页号, 偏移]
     */
    int[] decomposeAddress(int logicalAddress);
    
    /**
     * 构造物理地址
     * @param physicalPage 物理页框号
     * @param offset 偏移
     * @return 物理地址
     */
    int composeAddress(int physicalPage, int offset);
    
    /**
     * 检查页号有效性
     * @param pageNumber 页号
     * @return 是否有效
     */
    boolean isValidPageNumber(int pageNumber);
    
    /**
     * 检查偏移有效性
     * @param offset 偏移
     * @return 是否有效
     */
    boolean isValidOffset(int offset);
}