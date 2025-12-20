package com.os.core.models;

/**
 * 页表项
 * 用于逻辑地址到物理地址的映射
 */
public class PageTableEntry {
    private int logicalPage;     // 逻辑页号
    private int physicalPage;    // 物理页框号
    private boolean valid;        // 有效位 (0=无效，1=有效)
    private boolean dirty;        // 修改位 (0=未修改，1=已修改)
    private boolean accessed;     // 访问位 (0=未访问，1=已访问)
    private boolean protection;   // 保护位 (0=可读写，1=只读)
    
    public PageTableEntry() {
        this.valid = false;
        this.dirty = false;
        this.accessed = false;
        this.protection = false;
    }
    
    public PageTableEntry(int logicalPage, int physicalPage) {
        this();
        this.logicalPage = logicalPage;
        this.physicalPage = physicalPage;
        this.valid = true;
    }
    
    // Getters and Setters
    public int getLogicalPage() {
        return logicalPage;
    }
    
    public void setLogicalPage(int logicalPage) {
        this.logicalPage = logicalPage;
    }
    
    public int getPhysicalPage() {
        return physicalPage;
    }
    
    public void setPhysicalPage(int physicalPage) {
        this.physicalPage = physicalPage;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public boolean isDirty() {
        return dirty;
    }
    
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    
    public boolean isAccessed() {
        return accessed;
    }
    
    public void setAccessed(boolean accessed) {
        this.accessed = accessed;
    }
    
    public boolean isProtection() {
        return protection;
    }
    
    public void setProtection(boolean protection) {
        this.protection = protection;
    }
    
    /**
     * 设置页表项状态位
     * 16位状态位格式：[valid:1][dirty:1][accessed:1][protection:1][保留:12]
     */
    public void setStatusBits(int status) {
        this.valid = (status & 0x8000) != 0;     // bit15
        this.dirty = (status & 0x4000) != 0;      // bit14
        this.accessed = (status & 0x2000) != 0;    // bit13
        this.protection = (status & 0x1000) != 0;   // bit12
    }
    
    /**
     * 获取页表项状态位
     */
    public int getStatusBits() {
        int status = 0;
        if (valid) status |= 0x8000;    // bit15
        if (dirty) status |= 0x4000;     // bit14
        if (accessed) status |= 0x2000;   // bit13
        if (protection) status |= 0x1000;  // bit12
        return status;
    }
    
    /**
     * 重置访问状态
     */
    public void resetAccessed() {
        this.accessed = false;
    }
    
    /**
     * 设置为修改状态
     */
    public void markDirty() {
        this.dirty = true;
    }
    
    /**
     * 设置为已访问
     */
    public void markAccessed() {
        this.accessed = true;
    }
    
    @Override
    public String toString() {
        return String.format("PageTableEntry{logical=%d, physical=%d, valid=%b, dirty=%b, accessed=%b, protection=%b}", 
                         logicalPage, physicalPage, valid, dirty, accessed, protection);
    }
}