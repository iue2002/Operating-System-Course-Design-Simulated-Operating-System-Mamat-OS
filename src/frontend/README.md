前端（Vue + TDesign）占位

建议：
- 使用 Vue 3 + Vite
- 页面：进程列表、内存地图（页/帧）、设备状态、日志/事件
- 与后端通过简单的 JSON API 或直接导入模块进行状态展示（开发阶段）

下一步：由后端提供简单的状态查询 API（或导出状态对象），前端实现可视化组件。

设备管理使用示例（Node/测试/前端直接导入模块）
```ts
// 假设在 node 环境或打包环境下可直接 import DeviceManager
import { DeviceManager } from '../src/device/deviceManager';

const dm = new DeviceManager({ A: 2, B: 1, C: 1 });

// 同步非阻塞申请
const d = dm.requestDevice(1, 'A');
if (d) {
  console.log('pid1 分配到设备', d);
} else {
  console.log('pid1 等待 A 设备');
}

// 异步等待（可超时）
dm.waitForDevice(2, 'A', 5000)
  .then(deviceId => console.log('pid2 分配到', deviceId))
  .catch(err => console.error('pid2 等待失败/超时', err));

// 释放并唤醒下一个等待者
dm.releaseDevice(1, d!);
```
