package com.os.core.models;

/**
 * 进程控制块 (Process Control Block)
 * 包含进程的所有状态信息和执行上下文
 */
public class PCB {
    public enum ProcessState {
        NEW,        // 新建状态
        READY,      // 就绪状态
        RUNNING,    // 运行状态  
        BLOCKED,    // 阻塞状态
        TERMINATED  // 终止状态
    }
    
    private int pid;                    // 进程标识符 (1-10)
    private String name;                // 进程名（可执行文件名）
    private ProcessState state;         // 进程状态
    private int priority;               // 进程优先级（固定为1）
    private int pc;                    // 程序计数器
    private int psw;                   // 程序状态字
    private int[] registers;            // 通用寄存器 [R0,R1,R2,R3]
    private int[] variables;            // 变量存储器 [x,y,z,...]
    private String waitingFor;          // 等待原因（设备类型/时间/其他）
    private int timeSlice;              // 剩余时间片
    private int startTime;              // 进程创建时间（系统时钟tick）
    private int runTime;               // 已运行时间（tick数）
    private int pageTablePtr;           // 页表指针（与内存管理对接）
    private int systemPage;             // 系统区中为该PCB保留的页面
    private java.util.List<Instruction> instructions; // 可执行文件指令列表
    private String executablePath;        // 可执行文件路径
    private String outputPath;          // 输出文件路径
    private String currentInstructionText; // 下一条待执行指令文本
    private int currentInstructionIndex;   // 下一条待执行指令索引
    
    public PCB(int pid, String name, String executablePath) {
        this.pid = pid;
        this.name = name;
        this.executablePath = executablePath;
        this.state = ProcessState.NEW;
        this.priority = 1;
        this.pc = 0;
        this.psw = 0;
        this.registers = new int[4]; // R0-R3
        this.variables = new int[26]; // x-z (26个字母变量)
        this.waitingFor = "";
        this.timeSlice = 5; // 默认时间片
        this.startTime = 0;
        this.runTime = 0;
        this.pageTablePtr = -1;
        this.systemPage = -1;
        this.instructions = new java.util.ArrayList<>();
        this.outputPath = executablePath.replace(".exe", "_out.tx");
        this.currentInstructionText = "";
        this.currentInstructionIndex = -1;
    }
    
    // Getters and Setters
    public int getPid() {
        return pid;
    }
    
    public void setPid(int pid) {
        this.pid = pid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public ProcessState getState() {
        return state;
    }
    
    public void setState(ProcessState state) {
        this.state = state;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public int getPc() {
        return pc;
    }
    
    public void setPc(int pc) {
        this.pc = pc;
    }
    
    public int getPsw() {
        return psw;
    }
    
    public void setPsw(int psw) {
        this.psw = psw;
    }
    
    public int[] getRegisters() {
        return registers;
    }
    
    public void setRegisters(int[] registers) {
        this.registers = registers;
    }
    
    public int[] getVariables() {
        return variables;
    }
    
    public void setVariables(int[] variables) {
        this.variables = variables;
    }
    
    public String getWaitingFor() {
        return waitingFor;
    }
    
    public void setWaitingFor(String waitingFor) {
        this.waitingFor = waitingFor;
    }
    
    public int getTimeSlice() {
        return timeSlice;
    }
    
    public void setTimeSlice(int timeSlice) {
        this.timeSlice = timeSlice;
    }
    
    public int getStartTime() {
        return startTime;
    }
    
    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }
    
    public int getRunTime() {
        return runTime;
    }
    
    public void setRunTime(int runTime) {
        this.runTime = runTime;
    }
    
    public int getPageTablePtr() {
        return pageTablePtr;
    }
    
    public void setPageTablePtr(int pageTablePtr) {
        this.pageTablePtr = pageTablePtr;
    }

    public int getSystemPage() {
        return systemPage;
    }

    public void setSystemPage(int systemPage) {
        this.systemPage = systemPage;
    }
    
    public java.util.List<Instruction> getInstructions() {
        return instructions;
    }
    
    public void setInstructions(java.util.List<Instruction> instructions) {
        this.instructions = instructions;
    }
    
    public String getExecutablePath() {
        return executablePath;
    }
    
    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getCurrentInstructionText() {
        return currentInstructionText;
    }

    public void setCurrentInstructionText(String currentInstructionText) {
        this.currentInstructionText = currentInstructionText == null ? "" : currentInstructionText;
    }

    public int getCurrentInstructionIndex() {
        return currentInstructionIndex;
    }

    public void setCurrentInstructionIndex(int currentInstructionIndex) {
        this.currentInstructionIndex = currentInstructionIndex;
    }
    
    /**
     * 获取变量值 (x=0, y=1, z=2...)
     */
    public int getVariable(char varName) {
        int index = varName - 'a'; // a=0, b=1, c=2...
        if (index >= 0 && index < variables.length) {
            return variables[index];
        }
        return 0;
    }
    
    /**
     * 设置变量值
     */
    public void setVariable(char varName, int value) {
        int index = varName - 'a'; // a=0, b=1, c=2...
        if (index >= 0 && index < variables.length) {
            variables[index] = Math.max(0, Math.min(9, value)); // 限制0-9
        }
    }
    
    /**
     * 获取寄存器值
     */
    public int getRegister(int regIndex) {
        if (regIndex >= 0 && regIndex < registers.length) {
            return registers[regIndex];
        }
        return 0;
    }
    
    /**
     * 设置寄存器值
     */
    public void setRegister(int regIndex, int value) {
        if (regIndex >= 0 && regIndex < registers.length) {
            registers[regIndex] = value;
        }
    }
    
    @Override
    public String toString() {
        return String.format("PCB{pid=%d, name='%s', state=%s, pc=%d, timeSlice=%d/%d}", 
                         pid, name, state, pc, timeSlice, runTime);
    }
}