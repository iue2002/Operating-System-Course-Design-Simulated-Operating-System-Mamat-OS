# 麦麦提OS - 操作系统模拟器

[![Java](https://img.shields.io/badge/Java-23-orange.svg)](https://www.oracle.com/java/)
[![Vue](https://img.shields.io/badge/Vue-3.4+-green.svg)](https://vuejs.org/)
[![Tauri](https://img.shields.io/badge/Tauri-1.5+-blue.svg)](https://tauri.app/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

一个基于Java和Vue的操作系统模拟器，实现了文件系统、进程管理、内存管理和设备管理等核心功能。

## 📋 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [系统架构](#系统架构)
- [安装与运行](#安装与运行)
- [使用指南](#使用指南)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

## 🚀 项目简介

麦麦提OS是一个教学用的操作系统模拟器，旨在帮助理解操作系统的核心概念和工作原理。该模拟器采用前后端分离架构，后端使用Java实现操作系统核心功能，前端使用Vue 3和Tauri构建桌面应用。

## ✨ 功能特性

### 🗂️ 文件系统
- 基于FAT（File Allocation Table）的文件系统
- 支持多级目录结构
- 文件创建、读取、写入、删除操作
- 支持简单的可执行文件格式
![image](https://github.com/iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS/blob/main/frontend/src-tauri/icons/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-03-12%20135612.png)
### ⚙️ 进程管理
- 进程控制块（PCB）管理
- 进程状态管理（就绪、运行、阻塞、终止）
- 简单的进程调度算法
- 进程同步与通信
![image](https://github.com/iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS/blob/main/frontend/src-tauri/icons/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-03-12%20135214.png)
### 💾 内存管理
- 分页内存管理机制
- 页表管理
- 简单的页面置换算法
- 内存分配与回收
![image](https://github.com/iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS/blob/main/frontend/src-tauri/icons/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202025-12-21%20132520.png)
### 🔌 设备管理
- I/O设备模拟
- 设备中断处理
- 设备状态监控
![image](https://github.com/iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS/blob/main/frontend/src-tauri/icons/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-03-12%20135701.png)
### 🖥️ 用户界面
- 类Windows桌面环境
- 文件资源管理器
- 进程监视器
- 内存监视器
- 设备状态监视器
![image](https://github.com/iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS/blob/main/frontend/src-tauri/icons/%E5%B1%8F%E5%B9%95%E6%88%AA%E5%9B%BE%202026-03-12%20140447.png)
## 🏗️ 系统架构

```
麦麦提OS
├── 后端 (Java)
│   ├── OSKernelImpl - 操作系统内核
│   ├── FileSystemImpl - 文件系统实现
│   ├── ProcessManagerImpl - 进程管理器
│   ├── MemoryManagerImpl - 内存管理器
│   └── DeviceManagerImpl - 设备管理器
├── 前端 (Vue 3 + Tauri)
│   ├── Desktop - 桌面环境
│   ├── FileExplorer - 文件资源管理器
│   ├── ProcessMonitor - 进程监视器
│   ├── MemoryMonitor - 内存监视器
│   └── DeviceStatus - 设备状态监视器
└── 通信 (REST API)
```

## 🛠️ 安装与运行

### 环境要求

- Java 23 或更高版本
- Node.js 18 或更高版本
- npm 或 yarn
- Maven 3.6 或更高版本

### 安装步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS.git
   cd Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS
   ```

2. **构建后端**
   ```bash
   cd os-core
   mvn clean compile
   mvn package
   ```

3. **安装前端依赖**
   ```bash
   cd ../frontend
   npm install
   ```

### 运行方式

#### 方式一：开发模式

1. **启动后端服务**
   ```bash
   cd os-core
   java -cp target/os-core-1.0.0.jar com.os.Main
   ```

2. **启动前端开发服务器**
   ```bash
   cd frontend
   npm run dev
   ```

3. **访问应用**
   打开浏览器访问 `http://localhost:5173`

#### 方式二：桌面应用

1. **构建Tauri应用**
   ```bash
   cd frontend
   npm run tauri:build
   ```

2. **运行桌面应用**
   ```bash
   cd frontend/src-tauri/target/release
   ./os-sim-frontend.exe  # Windows
   ./os-sim-frontend      # Linux/Mac
   ```

## 📖 使用指南

### 基本操作

1. **文件操作**
   - 创建文件：在文件资源管理器中右键选择"新建文件"
   - 编辑文件：双击文件打开编辑器
   - 运行程序：创建可执行文件(.tx)后，右键选择"运行"

2. **进程管理**
   - 查看进程：打开"任务管理器"应用
   - 终止进程：在进程列表中选择进程后点击"终止"

3. **系统监控**
   - 内存使用：打开"RAM和CPU监视器"查看内存使用情况
   - 设备状态：打开"设备状态"查看I/O设备状态

### 示例程序

创建一个简单的可执行文件：

```
x=5;
x++;
print(x);
end.
```

保存为 `.tx` 文件，然后右键选择"运行"。

## 📁 项目结构

```
麦麦提OS-源码/
├── os-core/                 # 后端核心模块
│   ├── src/main/java/       # Java源代码
│   │   └── com/os/         # 主要包
│   │       ├── core/       # 内核实现
│   │       ├── filesystem/ # 文件系统
│   │       ├── process/    # 进程管理
│   │       ├── memory/     # 内存管理
│   │       └── device/     # 设备管理
│   └── pom.xml             # Maven配置
├── frontend/               # 前端模块
│   ├── src/                # Vue源代码
│   │   ├── components/     # 组件
│   │   ├── views/          # 页面
│   │   ├── stores/         # 状态管理
│   │   └── api/            # API接口
│   ├── src-tauri/          # Tauri配置
│   ├── dist/               # 构建输出
│   └── package.json        # NPM配置
├── src/                    # 其他源代码
├── target/                 # Maven构建输出
└── README.md              # 项目说明
```

## 🛠️ 技术栈

### 后端
- **Java 23** - 主要编程语言
- **Maven** - 项目构建和依赖管理
- **JUnit 5** - 单元测试框架
- **Mockito** - 模拟测试框架

### 前端
- **Vue 3** - 前端框架
- **Vite** - 构建工具
- **Tauri** - 桌面应用框架
- **Tailwind CSS** - CSS框架
- **Pinia** - 状态管理
- **Axios** - HTTP客户端

### 通信
- **REST API** - 前后端通信协议
- **JSON** - 数据交换格式

## 🤝 贡献指南

我们欢迎任何形式的贡献！如果您想为项目做出贡献，请遵循以下步骤：

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

### 代码规范

- Java代码遵循Google Java Style Guide
- Vue代码遵循官方风格指南
- 提交信息遵循Conventional Commits规范

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 👥 作者

- **麦麦提艾力** - *项目发起人与主要开发者* - [GitHub Profile](https://github.com/iue2002)

## 🙏 致谢

- 感谢所有为项目做出贡献的同学
- 感谢开源社区提供的优秀工具和框架
- 感谢操作系统课程提供的指导和支持

## 📞 联系我们

如果您有任何问题或建议，请通过以下方式联系我们：

- 提交 [Issue](https://github.com/iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS/issues)
- 发送邮件至：212985006@qq.com

## ⭐ Star History

如果这个项目对您有帮助，请给我们一个 ⭐️！

[![Star History Chart](https://api.star-history.com/svg?repos=iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS&type=Date)](https://star-history.com/#iue2002/Operating-System-Course-Design-Simulated-Operating-System-Mamat-OS&Date)
