package com.os.filesystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * 文件分配表 - 严格按照需求设计
 * 
 * FAT占用第0、1块（128字节）：
 * - 位示图：前128位（16字节）表示128个块的使用情况
 * - FAT表：剩余112字节，每个条目1字节，表示下一个块号或特殊标记
 * 
 * 特殊标记：
 * - 0xFF：空闲块
 * - 0xFE：文件结束
 * - 0xFD：坏块
 * - 0x00-0x7B：下一个块号（0-123）
 */
public class FAT {
    // FAT特殊标记
    public static final int FREE_BLOCK = 0xFF;
    public static final int END_OF_FILE = 0xFE;
    public static final int BAD_BLOCK = 0xFD;
    
    // 位置常量
    public static final int FAT_BLOCK_0 = 0;     // FAT表第0块
    public static final int FAT_BLOCK_1 = 1;     // FAT表第1块
    public static final int ROOT_DIR_BLOCK = 2;   // 根目录块
    public static final int DATA_START_BLOCK = 3;  // 数据区开始块
    
    private final Disk disk;
    private byte[] fatData;  // FAT表数据（128字节）
    private boolean[] bitmap;  // 位示图（128位）
    
    public FAT(Disk disk) {
        this.disk = disk;
        this.fatData = new byte[Disk.BLOCK_SIZE * 2];  // 128字节
        this.bitmap = new boolean[Disk.BLOCK_COUNT];     // 128位
        initializeFAT();
    }
    
    /**
     * 初始化FAT表
     */
    private void initializeFAT() {
        // 初始化FAT表
        for (int i = 0; i < fatData.length; i++) {
            fatData[i] = (byte) FREE_BLOCK;
        }
        
        // 设置系统块为已使用
        markBlockUsed(FAT_BLOCK_0);
        markBlockUsed(FAT_BLOCK_1);
        markBlockUsed(ROOT_DIR_BLOCK);
        
        // 保存到磁盘
        saveFAT();
    }
    
    /**
     * 标记块为已使用
     */
    private void markBlockUsed(int blockNumber) {
        if (blockNumber >= 0 && blockNumber < Disk.BLOCK_COUNT) {
            bitmap[blockNumber] = true;
            // sync disk-level bitmap
            try { disk.markBlockUsed(blockNumber); } catch (Exception ignore) {}
        }
    }
    
    /**
     * 标记块为空闲
     */
    private void markBlockFree(int blockNumber) {
        if (blockNumber >= 0 && blockNumber < Disk.BLOCK_COUNT) {
            bitmap[blockNumber] = false;
            try { disk.freeBlock(blockNumber); } catch (Exception ignore) {}
        }
    }
    
    /**
     * 检查块是否空闲
     */
    public boolean isBlockFree(int blockNumber) {
        if (blockNumber < 0 || blockNumber >= Disk.BLOCK_COUNT) {
            return false;
        }
        return !bitmap[blockNumber];
    }
    
    /**
     * 分配一个空闲块
     */
    public synchronized int allocateBlock() {
        // 从数据区开始查找空闲块
        for (int i = DATA_START_BLOCK; i < Disk.BLOCK_COUNT; i++) {
            if (isBlockFree(i)) {
                markBlockUsed(i);
                setFATEntry(i, END_OF_FILE);  // 新分配的块初始为文件结束
                saveFAT();
                return i;
            }
        }
        return -1;  // 无空闲块
    }
    
    /**
     * 释放块
     */
    public synchronized boolean freeBlock(int blockNumber) {
        if (blockNumber < DATA_START_BLOCK || blockNumber >= Disk.BLOCK_COUNT || isBlockFree(blockNumber)) {
            return false;
        }
        
        markBlockFree(blockNumber);
        setFATEntry(blockNumber, FREE_BLOCK);
        saveFAT();
        return true;
    }
    
    /**
     * 设置FAT表项
     */
    private void setFATEntry(int blockNumber, int value) {
        int offset = 16 + blockNumber;  // 跳过位示图（16字节）
        if (offset < fatData.length) {
            fatData[offset] = (byte) (value & 0xFF);
        }
    }
    
    /**
     * 获取FAT表项
     */
    public int getFATEntry(int blockNumber) {
        int offset = 16 + blockNumber;  // 跳过位示图（16字节）
        if (offset < fatData.length) {
            return fatData[offset] & 0xFF;
        }
        return FREE_BLOCK;
    }
    
    /**
     * 为文件分配块链
     */
    public synchronized int allocateFile(int blockCount) {
        if (blockCount <= 0) {
            return -1;
        }
        
        List<Integer> blocks = new ArrayList<>();
        
        // 分配所需数量的块
        for (int i = 0; i < blockCount; i++) {
            int block = allocateBlock();
            if (block == -1) {
                // 分配失败，回滚
                for (int b : blocks) {
                    freeBlock(b);
                }
                return -1;
            }
            blocks.add(block);
        }
        
        // 设置FAT链
        for (int i = 0; i < blocks.size(); i++) {
            int currentBlock = blocks.get(i);
            if (i < blocks.size() - 1) {
                setFATEntry(currentBlock, blocks.get(i + 1));
            } else {
                setFATEntry(currentBlock, END_OF_FILE);
            }
        }
        
        saveFAT();
        return blocks.get(0);  // 返回第一个块号
    }
    
    /**
     * 释放文件占用的所有块
     */
    public synchronized boolean freeFile(int firstBlock) {
        if (firstBlock < DATA_START_BLOCK || firstBlock >= Disk.BLOCK_COUNT) {
            return false;
        }
        
        int currentBlock = firstBlock;
        while (currentBlock != END_OF_FILE && currentBlock != FREE_BLOCK) {
            if (currentBlock < DATA_START_BLOCK || currentBlock >= Disk.BLOCK_COUNT) {
                break;  // 异常情况
            }
            
            int nextBlock = getFATEntry(currentBlock);
            freeBlock(currentBlock);
            currentBlock = nextBlock;
        }
        
        saveFAT();
        return true;
    }
    
    /**
     * 获取文件占用的所有块
     */
    public synchronized List<Integer> getFileBlocks(int firstBlock) {
        List<Integer> blocks = new ArrayList<>();
        int currentBlock = firstBlock;
        
        while (currentBlock != END_OF_FILE && currentBlock != FREE_BLOCK) {
            if (currentBlock < DATA_START_BLOCK || currentBlock >= Disk.BLOCK_COUNT) {
                return null;  // 链表损坏
            }
            
            blocks.add(currentBlock);
            currentBlock = getFATEntry(currentBlock);
        }
        
        return blocks;
    }
    
    /**
     * 向文件追加块
     */
    public synchronized int appendToFile(int lastBlock, int blockCount) {
        if (blockCount <= 0 || lastBlock < DATA_START_BLOCK || lastBlock >= Disk.BLOCK_COUNT) {
            return -1;
        }
        
        // 找到当前文件的最后一个块
        int currentLast = lastBlock;
        int nextBlock;
        while ((nextBlock = getFATEntry(currentLast)) != END_OF_FILE) {
            if (nextBlock == FREE_BLOCK || nextBlock < DATA_START_BLOCK || nextBlock >= Disk.BLOCK_COUNT) {
                return -1;  // 链表损坏
            }
            currentLast = nextBlock;
        }
        
        // 分配新块
        List<Integer> newBlocks = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) {
            int block = allocateBlock();
            if (block == -1) {
                // 分配失败，回滚
                for (int b : newBlocks) {
                    freeBlock(b);
                }
                return -1;
            }
            newBlocks.add(block);
        }
        
        // 连接到原文件末尾
        setFATEntry(currentLast, newBlocks.get(0));
        
        // 设置新块的FAT链
        for (int i = 0; i < newBlocks.size(); i++) {
            int block = newBlocks.get(i);
            if (i < newBlocks.size() - 1) {
                setFATEntry(block, newBlocks.get(i + 1));
            } else {
                setFATEntry(block, END_OF_FILE);
            }
        }
        
        saveFAT();
        return newBlocks.get(0);
    }
    
    /**
     * 保存FAT到磁盘
     */
    private void saveFAT() {
        // 构建完整的FAT数据（位示图 + FAT表）
        byte[] fullFAT = new byte[Disk.BLOCK_SIZE * 2];
        
        // 前16字节：位示图
        for (int i = 0; i < Disk.BLOCK_COUNT; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            if (bitmap[i]) {
                fullFAT[byteIndex] |= (1 << bitIndex);
            }
        }
        
        // 后112字节：FAT表项
        System.arraycopy(fatData, 16, fullFAT, 16, fatData.length - 16);
        
        // 写入磁盘第0、1块
        disk.writeBlock(FAT_BLOCK_0, fullFAT, 0, Disk.BLOCK_SIZE);
        disk.writeBlock(FAT_BLOCK_1, fullFAT, Disk.BLOCK_SIZE, Disk.BLOCK_SIZE);
    }
    
    /**
     * 从磁盘加载FAT
     */
    public synchronized boolean loadFAT() {
        byte[] fullFAT = new byte[Disk.BLOCK_SIZE * 2];
        
        if (disk.readBlock(FAT_BLOCK_0, fullFAT, 0, Disk.BLOCK_SIZE) != Disk.BLOCK_SIZE) {
            return false;
        }
        if (disk.readBlock(FAT_BLOCK_1, fullFAT, Disk.BLOCK_SIZE, Disk.BLOCK_SIZE) != Disk.BLOCK_SIZE) {
            return false;
        }
        
        // 加载位示图
        for (int i = 0; i < Disk.BLOCK_COUNT; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            bitmap[i] = (fullFAT[byteIndex] & (1 << bitIndex)) != 0;
        }
        
        // 加载FAT表
        System.arraycopy(fullFAT, 16, fatData, 16, fatData.length - 16);

        // Ensure Disk-level bitmap matches FAT's bitmap for data blocks.
        // This synchronizes Disk.bitmap with FAT.bitmap after loading from disk image,
        // preventing cases where the Disk image's saved bitmap differs from FAT's view.
        for (int i = DATA_START_BLOCK; i < Disk.BLOCK_COUNT; i++) {
            try {
                if (bitmap[i]) {
                    disk.markBlockUsed(i);
                } else {
                    // attempt to free on disk if it was previously marked used
                    disk.freeBlock(i);
                }
            } catch (Exception ignored) {}
        }

        return true;
    }
    
    /**
     * 获取磁盘使用情况
     */
    public FATInfo getFATInfo() {
        int freeBlocks = 0;
        int usedBlocks = 0;
        
        for (int i = DATA_START_BLOCK; i < Disk.BLOCK_COUNT; i++) {
            if (isBlockFree(i)) {
                freeBlocks++;
            } else {
                usedBlocks++;
            }
        }
        
        return new FATInfo(Disk.BLOCK_COUNT, freeBlocks, usedBlocks);
    }

    /**
     * 返回 FAT 层面的位示图（用于调试）
     */
    public synchronized boolean[] getBitmap() {
        return Arrays.copyOf(bitmap, bitmap.length);
    }
    
    /**
     * FAT信息
     */
    public static class FATInfo {
        public final int totalBlocks;
        public final int freeBlocks;
        public final int usedBlocks;
        
        public FATInfo(int totalBlocks, int freeBlocks, int usedBlocks) {
            this.totalBlocks = totalBlocks;
            this.freeBlocks = freeBlocks;
            this.usedBlocks = usedBlocks;
        }
        
        @Override
        public String toString() {
            return String.format("FATInfo{total=%d, free=%d, used=%d}", totalBlocks, freeBlocks, usedBlocks);
        }
    }
}