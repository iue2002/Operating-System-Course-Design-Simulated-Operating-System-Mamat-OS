package com.os.filesystem;

import java.util.Arrays;
import java.io.*;

/**
 * 磁盘存储管理类
 * 模拟磁盘：128块，每块64字节
 * 使用位示图管理空闲块
 */
public class Disk {
    public static final int BLOCK_COUNT = 128;       // 总块数
    public static final int BLOCK_SIZE = 64;         // 每块大小（字节）
    public static final int DISK_SIZE = BLOCK_COUNT * BLOCK_SIZE; // 总容量（8KB）
        // 默认磁盘映像路径。优先使用环境变量 OS_DISK_PATH 指定的路径，
        // 以避免在开发时把运行时写入的文件放在 src/tree（例如 src-tauri）内触发热重载。
        public static final String DEFAULT_DISK_IMAGE = System.getenv("OS_DISK_PATH") != null
            ? System.getenv("OS_DISK_PATH")
            : "disk.img";

    private byte[] storage;              // 磁盘存储空间
    private boolean[] bitmap;            // 位示图，记录块使用情况
    private int freeBlocks;             // 空闲块数量
    
    public Disk() {
        storage = new byte[DISK_SIZE];
        bitmap = new boolean[BLOCK_COUNT];
        Arrays.fill(bitmap, false);      // false表示空闲，true表示已使用
        freeBlocks = BLOCK_COUNT;
    }
    
    /**
     * 分配一个空闲块
     * @return 块号，失败返回-1
     */
    public synchronized int allocateBlock() {
        if (freeBlocks == 0) {
            return -1;  // 无空闲块
        }
        
        for (int i = 0; i < BLOCK_COUNT; i++) {
            if (!bitmap[i]) {
                bitmap[i] = true;
                freeBlocks--;
                return i;
            }
        }
        
        return -1;  // 理论上不会执行到这里
    }
    
    /**
     * 释放一个块
     * @param blockNumber 块号
     * @return 成功返回true，失败返回false
     */
    public synchronized boolean freeBlock(int blockNumber) {
        if (blockNumber < 0 || blockNumber >= BLOCK_COUNT || !bitmap[blockNumber]) {
            return false;  // 无效块号或块未被使用
        }

        // 清除块内容（安全考虑）
        int start = blockNumber * BLOCK_SIZE;
        int end = start + BLOCK_SIZE;
        Arrays.fill(storage, start, end, (byte) 0);

        bitmap[blockNumber] = false;
        freeBlocks++;
        return true;
    }

    /**
     * 标记一个块为已使用（外部调用，例如 FAT 初始化时用来同步位示图）
     */
    public synchronized void markBlockUsed(int blockNumber) {
        if (blockNumber < 0 || blockNumber >= BLOCK_COUNT) return;
        if (!bitmap[blockNumber]) {
            bitmap[blockNumber] = true;
            freeBlocks = Math.max(0, freeBlocks - 1);
        }
    }

     /**
      * 向指定块写入数据
      * @param blockNumber 块号
      * @param data 数据
      * @param offset 数据起始偏移
      * @param length 数据长度
      * @return 成功写入的字节数
      */
    public synchronized int writeBlock(int blockNumber, byte[] data, int offset, int length) {
        if (blockNumber < 0 || blockNumber >= BLOCK_COUNT || !bitmap[blockNumber]) {
            return -1;  // 无效块号或块未分配
        }
        
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
            return -1;  // 无效参数
        }
        
        int start = blockNumber * BLOCK_SIZE;
        int actualLength = Math.min(length, BLOCK_SIZE);
        
        System.arraycopy(data, offset, storage, start, actualLength);
        return actualLength;
    }
    
    /**
     * 从指定块读取数据
     * @param blockNumber 块号
     * @param buffer 缓冲区
     * @param offset 缓冲区起始偏移
     * @param length 读取长度
     * @return 成功读取的字节数
     */
    public synchronized int readBlock(int blockNumber, byte[] buffer, int offset, int length) {
        if (blockNumber < 0 || blockNumber >= BLOCK_COUNT || !bitmap[blockNumber]) {
            return -1;  // 无效块号或块未分配
        }
        
        if (buffer == null || offset < 0 || length < 0 || offset + length > buffer.length) {
            return -1;  // 无效参数
        }
        
        int start = blockNumber * BLOCK_SIZE;
        int actualLength = Math.min(length, BLOCK_SIZE);
        
        System.arraycopy(storage, start, buffer, offset, actualLength);
        return actualLength;
    }
    
    /**
     * 获取磁盘使用情况
     */
    public DiskUsageInfo getUsageInfo() {
        int usedBlocks = BLOCK_COUNT - freeBlocks;
        return new DiskUsageInfo(
            BLOCK_COUNT,
            freeBlocks,
            usedBlocks,
            (double) usedBlocks / BLOCK_COUNT * 100
        );
    }
    
    /**
     * 获取位示图状态（用于调试和监控）
     */
    public boolean[] getBitmap() {
        return Arrays.copyOf(bitmap, bitmap.length);
    }
    
    /**
     * 格式化磁盘
     */
    public synchronized void format() {
        Arrays.fill(storage, (byte) 0);
        Arrays.fill(bitmap, false);
        freeBlocks = BLOCK_COUNT;
    }

    /**
     * 将磁盘映像保存到文件
     * @param path 文件路径
     * @return 成功返回true，失败返回false
     */
    public synchronized boolean saveToFile(String path) {
        if (path == null || path.isEmpty()) path = DEFAULT_DISK_IMAGE;
        File tmp = new File(path + ".tmp");
        File target = new File(path);
        try (FileOutputStream fos = new FileOutputStream(tmp);
             DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(fos))) {
            // 写入存储区
            dos.writeInt(DISK_SIZE);
            dos.write(storage);
            // 以字节方式写入位示图
            for (int i = 0; i < BLOCK_COUNT; i++) {
                dos.writeBoolean(bitmap[i]);
            }
            // 写入空闲块数量
            dos.writeInt(freeBlocks);
            dos.flush();
            // 强制数据写入磁盘
            fos.getFD().sync();
        } catch (IOException e) {
            // 失败时清理临时文件
            try { tmp.delete(); } catch (Exception ignored) {}
            return false;
        }
        // 原子重命名
        try {
            if (target.exists()) {
                File backup = new File(path + ".bak");
                if (backup.exists()) backup.delete();
                if (!target.renameTo(backup)) {
                    // 备份重命名失败，删除备份
                }
            }
            if (!tmp.renameTo(target)) {
                // 最后手段：尝试复制
                try (FileInputStream fis = new FileInputStream(tmp);
                     FileOutputStream fos2 = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = fis.read(buf)) != -1) fos2.write(buf, 0, r);
                    fos2.getFD().sync();
                }
                tmp.delete();
            }
            return true;
        } catch (Exception ex) {
            try { tmp.delete(); } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * 从文件加载磁盘映像
     * @param path 文件路径
     * @return 成功返回true，失败返回false
     */
    public synchronized boolean loadFromFile(String path) {
        if (path == null || path.isEmpty()) path = DEFAULT_DISK_IMAGE;
        File f = new File(path);
        if (!f.exists() || !f.isFile()) return false;
        try (FileInputStream fis = new FileInputStream(f);
             DataInputStream dis = new DataInputStream(new BufferedInputStream(fis))) {
            int size = dis.readInt();
            if (size != DISK_SIZE) {
                // 不兼容的映像文件
                return false;
            }
            int read = dis.read(storage, 0, DISK_SIZE);
            if (read != DISK_SIZE) return false;
            for (int i = 0; i < BLOCK_COUNT; i++) {
                bitmap[i] = dis.readBoolean();
            }
            freeBlocks = dis.readInt();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 磁盘使用情况信息
     */
    public static class DiskUsageInfo {
        public final int totalBlocks;
        public final int freeBlocks;
        public final int usedBlocks;
        public final double usagePercentage;
        
        public DiskUsageInfo(int totalBlocks, int freeBlocks, int usedBlocks, double usagePercentage) {
            this.totalBlocks = totalBlocks;
            this.freeBlocks = freeBlocks;
            this.usedBlocks = usedBlocks;
            this.usagePercentage = usagePercentage;
        }
        
        @Override
        public String toString() {
            return String.format("DiskUsage{total=%d, free=%d, used=%d, usage=%.1f%%}", 
                totalBlocks, freeBlocks, usedBlocks, usagePercentage);
        }
    }
    
    /**
     * 磁盘状态快照（用于前端显示）
     */
    public DiskSnapshot getSnapshot() {
        return new DiskSnapshot(freeBlocks, BLOCK_COUNT - freeBlocks, getBitmap());
    }
    
    public static class DiskSnapshot {
        public final int freeBlocks;
        public final int usedBlocks;
        public final boolean[] blockUsage;
        
        public DiskSnapshot(int freeBlocks, int usedBlocks, boolean[] blockUsage) {
            this.freeBlocks = freeBlocks;
            this.usedBlocks = usedBlocks;
            this.blockUsage = blockUsage;
        }
    }
}
