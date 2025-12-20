package com.os;

import com.os.filesystem.*;
import java.util.List;

/**
 * 简单文件系统测试 - 验证基础功能符合课程设计要求
 */
public class SimpleFileSystemTest {
    
    public static void main(String[] args) {
        System.out.println("=== 课程设计文件系统测试 ===");
        
        // 测试1：目录项结构
        testDirectoryEntry();
        
        // 测试2：FAT表操作
        testFAT();
        
        // 测试3：可执行文件格式
        testExecutableFormat();
        
        System.out.println("=== 测试完成 ===");
    }
    
    /**
     * 测试目录项8字节结构
     */
    private static void testDirectoryEntry() {
        System.out.println("\\n1. 测试目录项结构（8字节）：");
        
        // 创建文件目录项 — 使用现有的 DirectoryEntry
        DirectoryEntry file = DirectoryEntry.createFile("ABC", "TXT", 10, 64);
        System.out.println("文件: " + file);
        System.out.println("  完整名: " + file.getFullName());
        System.out.println("  是文件: " + file.isFile());
        System.out.println("  是目录: " + file.isDirectory());
        
        // 创建目录目录项
        DirectoryEntry dir = DirectoryEntry.createDirectory("DIR", 5);
        System.out.println("目录: " + dir);
        System.out.println("  完整名: " + dir.getFullName());
        System.out.println("  是文件: " + dir.isFile());
        System.out.println("  是目录: " + dir.isDirectory());
        
        // 创建可执行文件
        DirectoryEntry exe = DirectoryEntry.createFile("PROG", "EXE", 8, 32);
        System.out.println("可执行文件: " + exe);
        System.out.println("  完整名: " + exe.getFullName());
        System.out.println("  可执行: " + exe.isExecutable());
    }
    
    /**
     * 测试FAT表操作
     */
    private static void testFAT() {
        System.out.println("\\n2. 测试FAT表操作：");
        
        Disk disk = new Disk();
        FAT fat = new FAT(disk); // 使用现有的 FAT 类

        // 获取FAT信息
        FAT.FATInfo info = fat.getFATInfo();
        System.out.println("初始FAT信息: " + info);
        
        // 分配一个块
        int block1 = fat.allocateBlock();
        System.out.println("分配块1: " + block1);
        System.out.println("块1空闲: " + fat.isBlockFree(block1));
        
        // 分配多个块的文件
        int firstBlock = fat.allocateFile(3);
        System.out.println("分配3块文件，起始块: " + firstBlock);
        
        List<Integer> blocks = fat.getFileBlocks(firstBlock);
        System.out.println("文件块链: " + blocks);
        
        // 检查FAT链表
        if (blocks != null && blocks.size() >= 3) {
            int next1 = fat.getFATEntry(blocks.get(0));
            int next2 = fat.getFATEntry(blocks.get(1));
            int next3 = fat.getFATEntry(blocks.get(2));
            System.out.println("FAT链表: " + blocks.get(0) + " -> " + next1 + " -> " + next2 + " -> " + next3);
        }
        
        // 释放文件
        boolean freed = fat.freeFile(firstBlock);
        System.out.println("释放文件结果: " + freed);
        
        FAT.FATInfo finalInfo = fat.getFATInfo();
        System.out.println("最终FAT信息: " + finalInfo);
    }
    
    /**
     * 测试可执行文件格式
     */
    private static void testExecutableFormat() {
        System.out.println("\\n3. 测试可执行文件格式：");
        
        // 符合要求的可执行文件内容
        String program = "x=5;\\nx++;\\nx--;\\n!A3;\\nend.";
        System.out.println("可执行文件内容:");
        System.out.println(program);
        
        // 解析每一行命令
        String[] lines = program.split("\\\\n");
        System.out.println("\\n命令解析:");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String type = getCommandType(line);
            System.out.println((i+1) + ". " + line + "  =>  " + type);
        }
    }
    
    /**
     * 获取命令类型
     */
    private static String getCommandType(String command) {
        if (command.startsWith("x=") && command.endsWith(";")) {
            return "赋值指令";
        } else if (command.equals("x++;")) {
            return "自增指令";
        } else if (command.equals("x--;")) {
            return "自减指令";
        } else if (command.startsWith("!") && command.endsWith(";")) {
            return "设备使用指令";
        } else if (command.equals("end.")) {
            return "程序结束指令";
        } else {
            return "未知指令";
        }
    }
}