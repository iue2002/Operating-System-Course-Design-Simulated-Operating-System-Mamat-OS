package com.os.core.models;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 可执行文件指令模型
 * 支持课程设计中定义的五种指令类型
 */
public class Instruction {
    public enum InstructionType {
        ASSIGN,        // x=?; 变量赋值
        INCREMENT,     // x++; 变量自增
        DECREMENT,     // x--; 变量自减
        DEVICE_IO,     // !A?; 设备I/O操作
        END            // end. 程序结束
    }
    
    private InstructionType type;
    private char variable;          // 变量名 (x, y, z...)
    private int value;              // 数值参数 (0-9)
    private char deviceType;        // 设备类型 (A, B, C)
    private int deviceTime;         // 设备使用时间
    private int lineNumber;         // 行号
    private String originalText;      // 原始指令文本
    
    public Instruction() {}
    
    public Instruction(InstructionType type, int lineNumber, String originalText) {
        this.type = type;
        this.lineNumber = lineNumber;
        this.originalText = originalText;
    }
    
    // Getters and Setters
    public InstructionType getType() {
        return type;
    }
    
    public void setType(InstructionType type) {
        this.type = type;
    }
    
    public char getVariable() {
        return variable;
    }
    
    public void setVariable(char variable) {
        this.variable = variable;
    }
    
    public int getValue() {
        return value;
    }
    
    public void setValue(int value) {
        this.value = value;
    }
    
    public char getDeviceType() {
        return deviceType;
    }
    
    public void setDeviceType(char deviceType) {
        this.deviceType = deviceType;
    }
    
    public int getDeviceTime() {
        return deviceTime;
    }
    
    public void setDeviceTime(int deviceTime) {
        this.deviceTime = deviceTime;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    public String getOriginalText() {
        return originalText;
    }
    
    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }
    
    @Override
    public String toString() {
        return String.format("Instruction{type=%s, line=%d, text='%s'}", 
                         type, lineNumber, originalText);
    }

    /**
     * 解析一个可执行程序字符串或文件路径为指令列表。
     * - 如果参数包含换行或分号，则按字符串解析；否则尝试把它作为文件路径读取并解析文件内容。
     */
    public static List<Instruction> parseFromStringOrFile(String source) {
        String content = source == null ? "" : source;
        // 支持传入的字符串中包含转义的 "\\n"，将其转换为真实换行以便解析
        if (content.contains("\\n")) {
            content = content.replace("\\n", "\n");
        }
        if (!(content.contains("\n") || content.contains(";") || content.contains("end."))) {
            // 可能是文件路径，尝试读取
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(source));
                content = new String(bytes);
            } catch (IOException e) {
                // 无法读取文件，则把原始字符串按行解析（可能就是单行程序）
                content = source;
            }
        }

        List<Instruction> out = new ArrayList<>();
        // 分割为语句：先按换行分，再按分号分
        String[] lines = content.split("\\r?\\n");
        int lineno = 0;
        for (String line : lines) {
            String[] parts = line.split(";");
            for (String part : parts) {
                String stmt = part.trim();
                if (stmt.isEmpty()) continue;
                lineno++;
                // 如果语句以 end. 结束（可能没有分号）
                if (stmt.equals("end.")) {
                    Instruction ins = new Instruction(InstructionType.END, lineno, stmt);
                    out.add(ins);
                    continue;
                }
                // 赋值: x=5
                if (stmt.matches("^[a-zA-Z]=\\d+$")) {
                    Instruction ins = new Instruction(InstructionType.ASSIGN, lineno, stmt);
                    ins.setVariable(stmt.charAt(0));
                    ins.setValue(Character.getNumericValue(stmt.charAt(2)));
                    out.add(ins);
                    continue;
                }
                // 自增: x++
                if (stmt.matches("^[a-zA-Z]\\+\\+$")) {
                    Instruction ins = new Instruction(InstructionType.INCREMENT, lineno, stmt);
                    ins.setVariable(stmt.charAt(0));
                    out.add(ins);
                    continue;
                }
                // 自减: x--
                if (stmt.matches("^[a-zA-Z]\\-\\-$")) {
                    Instruction ins = new Instruction(InstructionType.DECREMENT, lineno, stmt);
                    ins.setVariable(stmt.charAt(0));
                    out.add(ins);
                    continue;
                }
                // 设备I/O: !A3 或 !A3; (叹号后设备字符和数字)
                if (stmt.matches("^![A-Za-z]\\d+$") || stmt.matches("^![A-Za-z]\\d+;$")) {
                    // strip trailing ; if any
                    String s = stmt;
                    if (s.endsWith(";")) s = s.substring(0, s.length()-1);
                    char dev = Character.toUpperCase(s.charAt(1));
                    int t = 1;
                    try { t = Integer.parseInt(s.substring(2)); } catch (Exception ex) { t = 1; }
                    Instruction ins = new Instruction(InstructionType.DEVICE_IO, lineno, stmt);
                    ins.setDeviceType(dev);
                    ins.setDeviceTime(t);
                    out.add(ins);
                    continue;
                }
                // 备选：x=5; 格式（含分号）
                if (stmt.matches("^[a-zA-Z]=\\d+;$")) {
                    String s = stmt.substring(0, stmt.length()-1);
                    Instruction ins = new Instruction(InstructionType.ASSIGN, lineno, stmt);
                    ins.setVariable(s.charAt(0));
                    ins.setValue(Character.getNumericValue(s.charAt(2)));
                    out.add(ins);
                    continue;
                }
                // 若无法识别，忽略或当做注释
            }
        }
        return out;
    }
}