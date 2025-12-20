package com.os.filesystem;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
/**
 * 目录项 - 严格按照需求8字节设计
 * 
 * 目录项格式（8字节）：
 * - 3字节：文件/目录名（ASCII编码）
 * - 2字节：扩展名（ASCII编码，目录为空）
 * - 1字节：属性（位0:目录/文件，位1:只读，位2:隐藏，位3:系统，位4:可执行）
 * - 1字节：起始盘块号
 * - 1字节：文件长度（字节数，目录无长度）
 */
public class DirectoryEntry {
    public static final int ENTRY_SIZE = 8;
    
    // 属性位定义
    public static final int ATTR_DIRECTORY = 0x01;
    public static final int ATTR_READONLY = 0x02;
    public static final int ATTR_HIDDEN = 0x04;
    public static final int ATTR_SYSTEM = 0x08;
    public static final int ATTR_EXECUTABLE = 0x10;
    
    private byte[] data;  // 8字节数据
    
    public DirectoryEntry() {
        this.data = new byte[ENTRY_SIZE];
    }
    
    public DirectoryEntry(String name, String ext, int attributes, int firstBlock, int fileSize) {
        this.data = new byte[ENTRY_SIZE];
        setName(name);
        setExtension(ext);
        setAttributes(attributes);
        setFirstBlock(firstBlock);
        setFileSize(fileSize);
    }
    
    // 文件名（3字节，ASCII）
    public String getName() {
        String name = new String(data, 0, 3, StandardCharsets.US_ASCII).trim();
        return name.isEmpty() ? "" : name;
    }
    
    public void setName(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        int length = Math.min(3, nameBytes.length);
        System.arraycopy(nameBytes, 0, data, 0, length);
        // 剩余字节填充0
        for (int i = length; i < 3; i++) {
            data[i] = 0;
        }
    }
    
    // 扩展名（2字节，ASCII）
    public String getExtension() {
        String ext = new String(data, 3, 2, StandardCharsets.US_ASCII).trim();
        return ext.isEmpty() ? "" : ext;
    }
    
    public void setExtension(String ext) {
        byte[] extBytes = ext.getBytes(StandardCharsets.US_ASCII);
        int length = Math.min(2, extBytes.length);
        System.arraycopy(extBytes, 0, data, 3, length);
        // 剩余字节填充0
        for (int i = 3 + length; i < 5; i++) {
            data[i] = 0;
        }
    }
    
    // 属性（1字节）
    public int getAttributes() {
        return data[5] & 0xFF;
    }
    
    public void setAttributes(int attributes) {
        data[5] = (byte) (attributes & 0xFF);
    }
    
    // 起始盘块号（1字节）
    public int getFirstBlock() {
        return data[6] & 0xFF;
    }
    
    public void setFirstBlock(int firstBlock) {
        data[6] = (byte) (firstBlock & 0xFF);
    }
    
    // 文件长度（1字节）
    public int getFileSize() {
        return data[7] & 0xFF;
    }
    
    public void setFileSize(int fileSize) {
        data[7] = (byte) (fileSize & 0xFF);
    }
    
    // 属性检查方法
    public boolean isDirectory() {
        return (getAttributes() & ATTR_DIRECTORY) != 0;
    }
    
    public boolean isFile() {
        return !isDirectory();
    }
    
    public boolean isExecutable() {
        return (getAttributes() & ATTR_EXECUTABLE) != 0;
    }
    
    public boolean isReadOnly() {
        return (getAttributes() & ATTR_READONLY) != 0;
    }
    
    public boolean isHidden() {
        return (getAttributes() & ATTR_HIDDEN) != 0;
    }
    
    public boolean isSystem() {
        return (getAttributes() & ATTR_SYSTEM) != 0;
    }
    
    // 获取完整文件名（包括扩展名）
    public String getFullName() {
        String name = getName();
        String ext = getExtension();
        return ext.isEmpty() ? name : (name + "." + ext);
    }
    
    // 获取字节数组
    public byte[] toBytes() {
        return data.clone();
    }
    
    // 从字节数组创建
    public static DirectoryEntry fromBytes(byte[] bytes) {
        DirectoryEntry entry = new DirectoryEntry();
        if (bytes.length >= ENTRY_SIZE) {
            System.arraycopy(bytes, 0, entry.data, 0, ENTRY_SIZE);
        }
        return entry;
    }
    
    @Override
    public String toString() {
        return String.format("DirectoryEntry{name='%s', ext='%s', attrs=0x%02x, firstBlock=%d, size=%d}", 
            getName(), getExtension(), getAttributes(), getFirstBlock(), getFileSize());
    }
    
    /**
     * 创建目录项
     */
    public static DirectoryEntry createDirectory(String name, int firstBlock) {
        return new DirectoryEntry(name, "", ATTR_DIRECTORY, firstBlock, 0);
    }
    
    /**
     * 创建文件
     */
    public static DirectoryEntry createFile(String name, String ext, int firstBlock, int fileSize) {
        int attributes = ATTR_FILE;
        // Do not rely on hard-coded extension checks here. Executable flag should be
        // set explicitly by caller via FileSystem.createFile isExecutable parameter.
        return new DirectoryEntry(name, ext, attributes, firstBlock, fileSize);
    }
    
    public static final int ATTR_FILE = 0x00;  // 普通文件
}