package com.os.core.interfaces;

import com.os.core.models.Instruction;
import java.util.List;
import java.util.Map;

/**
 * 文件系统接口
 * 定义文件系统操作的标准API
 */
public interface FileSystemAPI {
    
    // ========== 目录操作 ==========
    
    /**
     * 列出目录内容
     * @param path 目录路径
     * @return 目录项列表
     */
    List<Map<String, Object>> listDirectory(String path);
    
    /**
     * 创建目录
     * @param path 目录路径
     * @return 创建结果
     */
    boolean createDirectory(String path);
    
    /**
     * 删除目录
     * @param path 目录路径
     * @param recursive 是否递归删除
     * @return 删除结果
     */
    boolean deleteDirectory(String path, boolean recursive);
    
    // ========== 文件操作 ==========
    
    /**
     * 创建文件
     * @param path 文件路径
     * @param content 文件内容
     * @param isExecutable 是否为可执行文件
     * @param overwrite 是否覆盖已存在文件
     * @return 创建结果
     */
    boolean createFile(String path, String content, boolean isExecutable, boolean overwrite);
    
    /**
     * 读取文件内容
     * @param path 文件路径
     * @return 文件内容
     */
    String readFile(String path);
    
    /**
     * 写入文件内容
     * @param path 文件路径
     * @param content 文件内容
     * @param append 是否追加写入
     * @return 写入结果
     */
    boolean writeFile(String path, String content, boolean append);
    
    /**
     * 删除文件
     * @param path 文件路径
     * @return 删除结果
     */
    boolean deleteFile(String path);
    
    /**
     * 复制文件
     * @param sourcePath 源文件路径
     * @param destPath 目标文件路径
     * @param overwrite 是否覆盖已存在文件
     * @return 复制结果
     */
    boolean copyFile(String sourcePath, String destPath, boolean overwrite);
    
    /**
     * 移动文件
     * @param sourcePath 源文件路径
     * @param destPath 目标文件路径
     * @param overwrite 是否覆盖已存在文件
     * @return 移动结果
     */
    boolean moveFile(String sourcePath, String destPath, boolean overwrite);
    
    // ========== 可执行文件操作 ==========
    
    /**
     * 加载可执行文件
     * @param path 可执行文件路径
     * @param validateOnly 仅验证不加载
     * @return 可执行文件信息
     */
    Map<String, Object> loadExecutable(String path, boolean validateOnly);
    
    /**
     * 写入进程输出
     * @param processId 进程ID
     * @param content 输出内容
     * @return 写入结果
     */
    boolean writeOutput(int processId, String content);
    
    // ========== 系统管理 ==========
    
    /**
     * 格式化磁盘
     * @return 格式化结果
     */
    boolean formatDisk();
    
    /**
     * 获取磁盘使用状态
     * @return 磁盘状态信息
     */
    Map<String, Object> getDiskStatus();
    
    /**
     * 检查文件系统一致性
     * @return 检查结果
     */
    Map<String, Object> checkConsistency();
    
    // ========== 路径解析 ==========
    
    /**
     * 解析路径
     * @param path 路径字符串
     * @return 路径段数组
     */
    String[] parsePath(String path);
    
    /**
     * 规范化路径
     * @param path 原始路径
     * @return 规范化路径
     */
    String normalizePath(String path);
    
    /**
     * 检查路径是否存在
     * @param path 路径
     * @return 是否存在
     */
    boolean pathExists(String path);
    
    /**
     * 检查是否为目录
     * @param path 路径
     * @return 是否为目录
     */
    boolean isDirectory(String path);
    
    /**
     * 检查是否为可执行文件
     * @param path 路径
     * @return 是否为可执行文件
     */
    boolean isExecutable(String path);
    
    // ========== 文件信息 ==========
    
    /**
     * 获取文件/目录属性
     * @param path 路径
     * @return 文件属性信息
     */
    Map<String, Object> getFileAttributes(String path);
    
    /**
     * 获取文件大小
     * @param path 路径
     * @return 文件大小（字节）
     */
    int getFileSize(String path);
    
    /**
     * 获取文件修改时间
     * @param path 路径
     * @return 修改时间戳
     */
    long getModificationTime(String path);
}