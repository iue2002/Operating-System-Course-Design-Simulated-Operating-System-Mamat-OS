// 设备管理：实现 A/B/C 三类设备、等待队列与安全分配（非阻塞，队列式等待）

export type DeviceType = 'A' | 'B' | 'C';

export interface Device {
  id: string;
  type: DeviceType;
  inUseBy?: number; // pid
  // ...existing fields...
}

export class DeviceManager {
  devices: Device[] = [];
  // 每类设备的等待队列（FIFO），存放等待的 pid（用于顺序与快照）
  private waitQueues: Record<DeviceType, number[]> = { A: [], B: [], C: [] };

  // 每类设备的等待 Promise 回调映射：type -> pid -> {resolve,reject,timer}
  private waitResolvers: Record<
    DeviceType,
    Map<
      number,
      { resolve: (deviceId: string) => void; reject: (reason?: any) => void; timer?: ReturnType<typeof setTimeout> }
    >
  > = { A: new Map(), B: new Map(), C: new Map() };

  /**
   * constructor
   * counts: 可选配置每类设备数量，默认 {A:2,B:2,C:1}
   */
  constructor(counts?: { A?: number; B?: number; C?: number }) {
    const cfg = { A: 2, B: 2, C: 1, ...(counts || {}) };
    // 初始化设备列表，id 采用 类型+索引 形式 (例如 A0, A1)
    for (const t of (['A', 'B', 'C'] as DeviceType[])) {
      const n = cfg[t] || 0;
      for (let i = 0; i < n; i++) {
        this.devices.push({ id: `${t}${i}`, type: t });
      }
    }
  }

  /**
   * 请求设备（非阻塞）
   * - 如果存在空闲设备，立即分配并返回 deviceId
   * - 否则把 pid 放入对应类型的等待队列（若已在队列中则不重复入队），返回 null
   */
  requestDevice(pid: number, type: DeviceType): string | null {
    // 先尝试分配空闲设备
    const freeDev = this.devices.find(d => d.type === type && d.inUseBy === undefined);
    if (freeDev) {
      freeDev.inUseBy = pid;
      // 如果该 pid 有等待的 Promise（不常见），立即 resolve
      const rmap = this.waitResolvers[type];
      const resolver = rmap.get(pid);
      if (resolver) {
        // 清理定时器
        if (resolver.timer) clearTimeout(resolver.timer);
        rmap.delete(pid);
        // 同步返回的同时也 resolve Promise（保持一致）
        setTimeout(() => resolver.resolve(freeDev.id), 0);
      }
      return freeDev.id;
    }
    // 无可用设备，入队等待（去重）
    const q = this.waitQueues[type];
    if (!q.includes(pid)) q.push(pid);
    return null;
  }

  /**
   * 等待设备（异步接口）
   * - 若设备立即可分配，返回已分配的 deviceId
   * - 否则返回一个 Promise，当设备分配到 pid 时 resolve deviceId
   * - 可选超时（ms），超时后 Promise reject 并从等待队列移除
   */
  waitForDevice(pid: number, type: DeviceType, timeoutMs?: number): Promise<string> {
    // 先尝试立即分配
    const immediate = this.requestDevice(pid, type);
    if (immediate) return Promise.resolve(immediate);

    // 已经在队列则复用；否则入队（requestDevice 已保证去重）
    // 创建 Promise 并保存 resolve/reject
    return new Promise<string>((resolve, reject) => {
      const rmap = this.waitResolvers[type];
      // 如果已有 resolver（同一 pid 重复等待），先 reject 旧的再覆盖
      const existing = rmap.get(pid);
      if (existing) {
        if (existing.timer) clearTimeout(existing.timer);
        existing.reject(new Error('新等待替代旧等待'));
        rmap.delete(pid);
      }
      const entry: { resolve: (id: string) => void; reject: (err?: any) => void; timer?: ReturnType<typeof setTimeout> } = {
        resolve: (id: string) => {
          // 清理并从 map 中删除（队列头释放时已从队列中 shift）
          if (entry.timer) clearTimeout(entry.timer);
          rmap.delete(pid);
          resolve(id);
        },
        reject: (err?: any) => {
          if (entry.timer) clearTimeout(entry.timer);
          rmap.delete(pid);
          // 也从等待队列中移除（可能已被分配）
          const q = this.waitQueues[type];
          const idx = q.indexOf(pid);
          if (idx >= 0) q.splice(idx, 1);
          reject(err);
        },
      };
      // 设置超时（若指定）
      if (timeoutMs && timeoutMs > 0) {
        entry.timer = setTimeout(() => {
          // 超时：调用 reject 并清理
          const r = rmap.get(pid);
          if (r) r.reject(new Error('waitForDevice timeout'));
        }, timeoutMs);
      }
      rmap.set(pid, entry);
      // 确保 pid 在队列中（requestDevice 可能已入队，但若外部直接调用 waitForDevice 要确保）
      const q = this.waitQueues[type];
      if (!q.includes(pid)) q.push(pid);
    });
  }

  /**
   * 取消等待（如果 pid 正在等待某类设备）
   * - 返回 true 表示确实存在等待并已取消，false 表示没有等待
   */
  cancelWait(pid: number, type: DeviceType): boolean {
    const rmap = this.waitResolvers[type];
    const resolver = rmap.get(pid);
    if (!resolver) {
      // 仍尝试从队列中移除（即便没有 Promise）
      const q = this.waitQueues[type];
      const idx = q.indexOf(pid);
      if (idx >= 0) {
        q.splice(idx, 1);
        return true;
      }
      return false;
    }
    resolver.reject(new Error('wait cancelled'));
    // reject 回调会清理队列与 map
    return true;
  }

  /**
   * 释放设备
   * - 验证设备存在且由 pid 持有
   * - 释放后，如果该类型有等待队列，则把设备分配给队首 pid 并返回该 pid
   * 返回值：被分配到该设备的下一个 pid（若没有则返回 null）；若释放失败（例如 pid 非持有者或 deviceId 不存在），也返回 null
   */
  releaseDevice(pid: number, deviceId: string): number | null {
    const dev = this.devices.find(d => d.id === deviceId);
    if (!dev) return null;
    if (dev.inUseBy !== pid) return null;
    dev.inUseBy = undefined;
    const q = this.waitQueues[dev.type];
    const nextPid = q.shift();
    if (nextPid !== undefined) {
      dev.inUseBy = nextPid;
      // 如果 nextPid 有等待的 Promise，resolve 并清理
      const rmap = this.waitResolvers[dev.type];
      const resolver = rmap.get(nextPid);
      if (resolver) {
        // 清理定时器并在下个事件循环中 resolve（避免同步回调导致意外）
        if (resolver.timer) clearTimeout(resolver.timer);
        rmap.delete(nextPid);
        setTimeout(() => resolver.resolve(dev.id), 0);
      }
      return nextPid;
    }
    return null;
  }

  /**
   * 强制释放某 pid 占用的所有设备（例如进程退出时调用）
   * 对每个释放出的设备会尝试分配给对应类型队列中的下一个 pid
   * 返回值：被唤醒（获得设备）的 pid 列表（可能为空）
   */
  forceReleaseByPid(pid: number): number[] {
    const awakened: number[] = [];
    for (const dev of this.devices) {
      if (dev.inUseBy === pid) {
        dev.inUseBy = undefined;
        const q = this.waitQueues[dev.type];
        const nextPid = q.shift();
        if (nextPid !== undefined) {
          dev.inUseBy = nextPid;
          // 唤醒等待 Promise（若存在）
          const rmap = this.waitResolvers[dev.type];
          const resolver = rmap.get(nextPid);
          if (resolver) {
            if (resolver.timer) clearTimeout(resolver.timer);
            rmap.delete(nextPid);
            setTimeout(() => resolver.resolve(dev.id), 0);
          }
          awakened.push(nextPid);
        }
      }
    }
    return awakened;
  }

  /**
   * 列出设备当前快照（浅拷贝）
   */
  listDevices(): Device[] {
    return this.devices.map(d => ({ ...d }));
  }

  /**
   * 查询等待队列快照
   */
  getWaitQueues(): Record<DeviceType, number[]> {
    return {
      A: [...this.waitQueues.A],
      B: [...this.waitQueues.B],
      C: [...this.waitQueues.C],
    };
  }

  /**
   * 调试/状态一并返回
   */
  getStatus() {
    return {
      devices: this.listDevices(),
      queues: this.getWaitQueues(),
      // 不直接暴露 resolver 内部回调，但可返回等待 pid 列表
      waitingResolvers: {
        A: Array.from(this.waitResolvers.A.keys()),
        B: Array.from(this.waitResolvers.B.keys()),
        C: Array.from(this.waitResolvers.C.keys()),
      },
    };
  }
}
