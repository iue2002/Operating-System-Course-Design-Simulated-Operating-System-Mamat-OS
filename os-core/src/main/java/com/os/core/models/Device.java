package com.os.core.models;

/**
 * 设备模型
 * 表示系统中的逻辑设备A、B、C
 */
public class Device {
    public enum DeviceStatus {
        FREE,           // 空闲状态
        BUSY,           // 忙碌状态
        MAINTENANCE,    // 维护状态
        FAULT          // 故障状态
    }
    
    public enum DeviceType {
        DEVICE_A('A', "输入设备"),
        DEVICE_B('B', "输出设备"), 
        DEVICE_C('C', "存储设备");
        
        private final char id;
        private final String description;
        
        DeviceType(char id, String description) {
            this.id = id;
            this.description = description;
        }
        
        public char getId() {
            return id;
        }
        
        public String getDescription() {
            return description;
        }
        
        public static DeviceType fromId(char id) {
            for (DeviceType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }
    
    private char deviceId;              // 设备标识 ('A', 'B', 'C')
    private DeviceType deviceType;       // 设备类型
    private DeviceStatus status;        // 设备状态
    private int ownerPid;             // 当前占用进程ID（0表示空闲）
    private int usedTime;              // 已使用时间（tick单位）
    private int requestTime;           // 请求使用总时间
    private java.util.Queue<Integer> waitQueue; // 等待队列（进程ID列表）
    private int totalRequests;         // 总请求数统计
    private int busyTime;             // 累计忙碌时间
    
    public Device(char deviceId) {
        this.deviceId = deviceId;
        this.deviceType = DeviceType.fromId(deviceId);
        this.status = DeviceStatus.FREE;
        this.ownerPid = 0;
        this.usedTime = 0;
        this.requestTime = 0;
        this.waitQueue = new java.util.LinkedList<>();
        this.totalRequests = 0;
        this.busyTime = 0;
    }
    
    // Getters and Setters
    public char getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(char deviceId) {
        this.deviceId = deviceId;
        this.deviceType = DeviceType.fromId(deviceId);
    }
    
    public DeviceType getDeviceType() {
        return deviceType;
    }
    
    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }
    
    public DeviceStatus getStatus() {
        return status;
    }
    
    public void setStatus(DeviceStatus status) {
        this.status = status;
    }
    
    public int getOwnerPid() {
        return ownerPid;
    }
    
    public void setOwnerPid(int ownerPid) {
        this.ownerPid = ownerPid;
    }
    
    public int getUsedTime() {
        return usedTime;
    }
    
    public void setUsedTime(int usedTime) {
        this.usedTime = usedTime;
    }
    
    public int getRequestTime() {
        return requestTime;
    }
    
    public void setRequestTime(int requestTime) {
        this.requestTime = requestTime;
    }
    
    public java.util.Queue<Integer> getWaitQueue() {
        return waitQueue;
    }
    
    public void setWaitQueue(java.util.Queue<Integer> waitQueue) {
        this.waitQueue = waitQueue;
    }
    
    public int getTotalRequests() {
        return totalRequests;
    }
    
    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }
    
    public int getBusyTime() {
        return busyTime;
    }
    
    public void setBusyTime(int busyTime) {
        this.busyTime = busyTime;
    }
    
    /**
     * 分配设备给进程
     */
    public boolean allocate(int pid, int requestTime) {
        if (status != DeviceStatus.FREE) {
            return false;
        }
        
        this.status = DeviceStatus.BUSY;
        this.ownerPid = pid;
        this.requestTime = requestTime;
        this.usedTime = 0;
        this.totalRequests++;
        return true;
    }
    
    /**
     * 释放设备
     */
    public void release() {
        this.status = DeviceStatus.FREE;
        this.ownerPid = 0;
        this.usedTime = 0;
        this.requestTime = 0;
    }
    
    /**
     * 进程加入等待队列
     */
    public void addToWaitQueue(int pid) {
        waitQueue.offer(pid);
    }
    
    /**
     * 从等待队列移除进程
     */
    public int removeFromWaitQueue() {
        return waitQueue.poll();
    }
    
    /**
     * 获取等待队列长度
     */
    public int getWaitQueueLength() {
        return waitQueue.size();
    }
    
    /**
     * 检查设备是否可用
     */
    public boolean isAvailable() {
        return status == DeviceStatus.FREE;
    }
    
    /**
     * 更新设备使用时间（每个tick调用）
     */
    public void tick() {
        if (status == DeviceStatus.BUSY) {
            usedTime++;
            busyTime++;
            
            // 检查是否使用完毕
            if (usedTime >= requestTime) {
                release();
            }
        }
    }
    
    /**
     * 获取设备利用率
     */
    public double getUtilizationRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) busyTime / (busyTime + getTotalIdleTime());
    }
    
    /**
     * 获取总空闲时间
     */
    private int getTotalIdleTime() {
        return totalRequests * 5; // 假设平均每次使用5个tick
    }
    
    /**
     * 获取设备完成进度（0-100%）
     */
    public int getProgress() {
        if (requestTime == 0) {
            return 0;
        }
        return Math.min(100, (usedTime * 100) / requestTime);
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        this.totalRequests = 0;
        this.busyTime = 0;
    }
    
    @Override
    public String toString() {
        return String.format("Device{id=%c, type=%s, status=%s, owner=%d, usedTime=%d/%d, waitQueue=%d}", 
                         deviceId, deviceType.getDescription(), status, ownerPid, usedTime, requestTime, waitQueue.size());
    }
}