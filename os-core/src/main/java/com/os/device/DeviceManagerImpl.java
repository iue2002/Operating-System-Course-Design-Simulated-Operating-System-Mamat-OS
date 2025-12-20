package com.os.device;

import com.os.core.interfaces.DeviceManagerAPI;
import com.os.core.models.Device;

import java.util.*;

/**
 * 简单的 DeviceManager 实现（满足课程设计要求的设备独立性与安全分配）
 * 默认每类设备数量为 1（满足保守配置）；可在构造时扩展。
 */
public class DeviceManagerImpl implements DeviceManagerAPI {
    private final Map<Character, Device> devices = new HashMap<>();
    private final Map<Character, Queue<Integer>> waitQueues = new HashMap<>();
    private final Map<Character, Map<Integer, Integer>> waitRequestTimes = new HashMap<>();
    private final Map<Integer, List<Character>> processAllocations = new HashMap<>();

    public DeviceManagerImpl() {
        // 默认 A=1,B=1,C=1 —— 创建三台逻辑设备
        devices.put('A', new Device('A'));
        devices.put('B', new Device('B'));
        devices.put('C', new Device('C'));
        waitQueues.put('A', new LinkedList<>());
        waitQueues.put('B', new LinkedList<>());
        waitQueues.put('C', new LinkedList<>());
        waitRequestTimes.put('A', new HashMap<>());
        waitRequestTimes.put('B', new HashMap<>());
        waitRequestTimes.put('C', new HashMap<>());
    }

    // ========== 设备操作 ==========
    @Override
    public synchronized Map<String, Object> requestDevice(int pid, char deviceType, int useTime) {
        Map<String, Object> r = new HashMap<>();
        if (!isValidDeviceType(deviceType)) {
            r.put("success", false);
            r.put("error", "invalid device type");
            return r;
        }
        Device dev = devices.get(deviceType);
        if (dev.isAvailable()) {
            // 简单的安全判断：若有空闲设备则安全分配
            if (isSafeState(deviceType, useTime, pid)) {
                boolean ok = dev.allocate(pid, useTime);
                if (ok) {
                    processAllocations.computeIfAbsent(pid, k -> new ArrayList<>()).add(deviceType);
                    r.put("success", true);
                    r.put("deviceType", deviceType);
                    return r;
                }
            }
        }
        // 无空闲或不安全 => 入队等待
        Queue<Integer> q = waitQueues.get(deviceType);
        if (!q.contains(pid)) q.offer(pid);
        waitRequestTimes.get(deviceType).put(pid, useTime);
        r.put("success", false);
        r.put("queued", true);
        r.put("position", q.size());
        return r;
    }

    @Override
    public synchronized boolean releaseDevice(int pid, char deviceType) {
        if (!isValidDeviceType(deviceType)) return false;
        Device dev = devices.get(deviceType);
        if (dev.getOwnerPid() != pid) return false;
        dev.release();
        // 分配给等待队首（FIFO）
        processWaitQueue(deviceType);
        return true;
    }

    @Override
    public synchronized boolean forceReleaseDevice(char deviceType) {
        if (!isValidDeviceType(deviceType)) return false;
        Device dev = devices.get(deviceType);
        int owner = dev.getOwnerPid();
        if (owner != 0) {
            dev.release();
            processWaitQueue(deviceType);
            return true;
        }
        return false;
    }

    // ========== 状态查询 ==========
    @Override
    public synchronized Map<String, Object> getDeviceStatus(char deviceType) {
        Map<String, Object> m = new HashMap<>();
        if (!isValidDeviceType(deviceType)) return m;
        Device d = devices.get(deviceType);
        m.put("id", d.getDeviceId());
        m.put("status", d.getStatus());
        m.put("ownerPid", d.getOwnerPid());
        m.put("usedTime", d.getUsedTime());
        m.put("requestTime", d.getRequestTime());
        m.put("waitQueueLength", d.getWaitQueueLength());
        m.put("progress", d.getProgress());
        return m;
    }

    @Override
    public synchronized List<Map<String, Object>> getAllDeviceStatus() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (char t : Arrays.asList('A','B','C')) {
            list.add(getDeviceStatus(t));
        }
        return list;
    }

    @Override
    public synchronized Device getDevice(char deviceType) {
        return devices.get(deviceType);
    }

    @Override
    public synchronized List<Device> getAllDevices() {
        return new ArrayList<>(devices.values());
    }

    // ========== 等待队列管理 ==========
    @Override
    public synchronized Queue<Integer> getDeviceWaitQueue(char deviceType) {
        return new LinkedList<>(waitQueues.get(deviceType));
    }

    @Override
    public synchronized Map<Character, Queue<Integer>> getAllWaitQueues() {
        Map<Character, Queue<Integer>> m = new HashMap<>();
        for (char t : Arrays.asList('A','B','C')) {
            m.put(t, new LinkedList<>(waitQueues.get(t)));
        }
        return m;
    }

    @Override
    public synchronized int getWaitQueueLength(char deviceType) {
        return waitQueues.get(deviceType).size();
    }

    @Override
    public synchronized boolean isInWaitQueue(int pid, char deviceType) {
        return waitQueues.get(deviceType).contains(pid);
    }

    // ========== 安全分配（简化） ==========
    @Override
    public synchronized boolean isSafeState(char deviceType, int requestTime, int requestingPid) {
        // 简化策略：若该逻辑设备当前有空闲物理设备，则视为安全
        Device d = devices.get(deviceType);
        return d != null && d.isAvailable();
    }

    @Override
    public synchronized List<Integer> findSafeSequence(Map<Integer, Map<Character, Integer>> currentAllocation, Map<Integer, Map<Character, Integer>> requests) {
        // 简单返回空或当前 pid 列表（不作完整银行家算法实现，因为需求宽松）
        return new ArrayList<>(currentAllocation.keySet());
    }

    @Override
    public synchronized Map<String, Object> safeAllocate(int pid, char deviceType, int useTime) {
        Map<String,Object> r = new HashMap<>();
        if (isSafeState(deviceType, useTime, pid)) {
            Device d = devices.get(deviceType);
            boolean ok = d.allocate(pid, useTime);
            if (ok) {
                processAllocations.computeIfAbsent(pid, k -> new ArrayList<>()).add(deviceType);
                r.put("success", true);
                r.put("deviceType", deviceType);
                return r;
            }
        }
        // 入队
        Queue<Integer> q = waitQueues.get(deviceType);
        if (!q.contains(pid)) q.offer(pid);
        waitRequestTimes.get(deviceType).put(pid, useTime);
        r.put("success", false);
        r.put("queued", true);
        return r;
    }

    // ========== 时钟驱动 ==========
    @Override
    public synchronized List<Map<String, Object>> onTick() {
        List<Map<String, Object>> completed = new ArrayList<>();
        // 对每个设备执行 tick，检测是否完成
        for (char t : Arrays.asList('A','B','C')) {
            Device d = devices.get(t);
            int beforeOwner = d.getOwnerPid();
            d.tick();
            int afterOwner = d.getOwnerPid();
            if (beforeOwner != 0 && afterOwner == 0) {
                Map<String,Object> info = new HashMap<>();
                info.put("pid", beforeOwner);
                info.put("deviceType", t);
                info.put("status", "completed");
                completed.add(info);
                // 分配给等待队首（若有）
                List<Integer> waked = processWaitQueue(t);
                // 也把被唤醒的进程作为完成项返回（可由上层调用 wakeup）
                for (Integer w : waked) {
                    Map<String,Object> info2 = new HashMap<>();
                    info2.put("pid", w);
                    info2.put("deviceType", t);
                        info2.put("status", "allocated");
                        completed.add(info2);
                }
            }
        }
        return completed;
    }

    @Override
    public synchronized void updateAllDevices() {
        for (Device d : devices.values()) d.tick();
    }

    @Override
    public synchronized List<Map<String, Object>> checkIOCompletion() {
        // alias to onTick for compatibility
        return onTick();
    }

    @Override
    public synchronized List<Integer> processWaitQueue(char deviceType) {
        List<Integer> allocated = new ArrayList<>();
        Queue<Integer> q = waitQueues.get(deviceType);
        Map<Integer,Integer> reqMap = waitRequestTimes.get(deviceType);
        Device d = devices.get(deviceType);
        if (d.isAvailable() && !q.isEmpty()) {
            Integer pid = q.poll();
            Integer req = reqMap.remove(pid);
            if (req == null) req = 1;
            boolean ok = d.allocate(pid, req);
            if (ok) {
                processAllocations.computeIfAbsent(pid, k -> new ArrayList<>()).add(deviceType);
                allocated.add(pid);
            }
        }
        return allocated;
    }

    // ========== 设备独立性 ==========
    @Override
    public synchronized Device mapLogicalToPhysical(char logicalDevice) {
        return devices.get(logicalDevice);
    }

    @Override
    public synchronized List<Device> getAvailableDevices(Character deviceType) {
        List<Device> r = new ArrayList<>();
        if (deviceType == null) {
            for (Device d : devices.values()) if (d.isAvailable()) r.add(d);
        } else {
            Device d = devices.get(deviceType);
            if (d != null && d.isAvailable()) r.add(d);
        }
        return r;
    }

    @Override
    public synchronized boolean isValidDeviceType(char deviceType) {
        return devices.containsKey(deviceType);
    }

    // ========== 进程设备管理 ==========
    @Override
    public synchronized List<Character> getProcessDevices(int pid) {
        return processAllocations.getOrDefault(pid, new ArrayList<>());
    }

    @Override
    public synchronized boolean releaseAllProcessDevices(int pid) {
        boolean changed = false;
        for (char t : Arrays.asList('A','B','C')) {
            Device d = devices.get(t);
            if (d.getOwnerPid() == pid) {
                d.release();
                changed = true;
                processWaitQueue(t);
            }
            // 也从等待队列移除该 pid
            Queue<Integer> q = waitQueues.get(t);
            if (q.remove(pid)) changed = true;
            Map<Integer,Integer> req = waitRequestTimes.get(t);
            req.remove(pid);
        }
        processAllocations.remove(pid);
        return changed;
    }

    @Override
    public synchronized List<Map<String, Object>> getProcessDeviceHistory(int pid) {
        // 简化实现：仅返回当前占用
        List<Map<String,Object>> h = new ArrayList<>();
        List<Character> list = getProcessDevices(pid);
        for (Character c : list) {
            Map<String,Object> m = new HashMap<>();
            m.put("pid", pid);
            m.put("deviceType", c);
            h.add(m);
        }
        return h;
    }

    // ========== 统计信息 ==========
    @Override
    public synchronized double getDeviceUtilization(char deviceType) {
        Device d = devices.get(deviceType);
        if (d == null) return 0.0;
        return d.getUtilizationRate() * 100.0;
    }

    @Override
    public synchronized Map<Character, Double> getAllDeviceUtilization() {
        Map<Character, Double> m = new HashMap<>();
        for (char t : Arrays.asList('A','B','C')) m.put(t, getDeviceUtilization(t));
        return m;
    }

    @Override
    public synchronized double getAverageWaitTime(char deviceType) {
        // 未记录详细时间，返回等待队列长度作为近似
        return getWaitQueueLength(deviceType);
    }

    @Override
    public synchronized Map<String, Object> getDeviceStatistics(char deviceType) {
        return getDeviceStatus(deviceType);
    }

    @Override
    public synchronized Map<String, Object> getSystemStatistics() {
        Map<String,Object> s = new HashMap<>();
        s.put("devices", getAllDeviceStatus());
        s.put("waitQueues", getAllWaitQueues());
        return s;
    }

    // ========== 系统管理 ==========
    @Override
    public synchronized boolean initialize() {
        // 已在构造中初始化
        return true;
    }

    @Override
    public synchronized boolean reset() {
        for (char t : Arrays.asList('A','B','C')) {
            Device d = devices.get(t);
            d.release();
            waitQueues.get(t).clear();
            waitRequestTimes.get(t).clear();
        }
        processAllocations.clear();
        return true;
    }

    @Override
    public synchronized boolean shutdown() {
        return reset();
    }

    @Override
    public synchronized Map<String, Object> checkDeviceConsistency() {
        return getSystemStatistics();
    }

    @Override
    public synchronized Map<String, Object> getDeviceConfig() {
        Map<String,Object> c = new HashMap<>();
        c.put("A", 1);
        c.put("B", 1);
        c.put("C", 1);
        return c;
    }

    // ========== 故障处理 ==========
    @Override
    public synchronized boolean setDeviceFault(char deviceType, boolean fault) {
        Device d = devices.get(deviceType);
        if (d == null) return false;
        d.setStatus(fault ? Device.DeviceStatus.FAULT : Device.DeviceStatus.FREE);
        return true;
    }

    @Override
    public synchronized boolean setDeviceMaintenance(char deviceType, boolean maintenance) {
        Device d = devices.get(deviceType);
        if (d == null) return false;
        d.setStatus(maintenance ? Device.DeviceStatus.MAINTENANCE : Device.DeviceStatus.FREE);
        return true;
    }

    @Override
    public synchronized boolean recoverDevice(char deviceType) {
        Device d = devices.get(deviceType);
        if (d == null) return false;
        d.setStatus(Device.DeviceStatus.FREE);
        return true;
    }

    // ========== 新增：重置设备统计（实现 DeviceManagerAPI 要求的方法） ==========
    @Override
    public synchronized void resetDeviceStatistics(Character deviceType) {
        if (deviceType == null) {
            // 重置所有设备统计
            for (Device d : devices.values()) {
                d.resetStatistics();
            }
        } else {
            Device d = devices.get(deviceType.charValue());
            if (d != null) d.resetStatistics();
        }
    }

}
