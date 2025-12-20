#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{
    env,
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::Mutex,
};

use tauri::{AppHandle, Manager, State, WindowEvent};
// ...已在上方 use std::{process::{Child, Command, Stdio}, ...} 导入，无需重复
use reqwest;

#[tauri::command]
fn ping() -> String {
    "pong".into()
}

#[derive(Clone, Copy)]
enum BackendKind {
    Jar,
    Exe,
}

struct BackendState {
    child: Mutex<Option<Child>>,
    auto_spawn: Mutex<bool>,
}

impl BackendState {
    fn new() -> Self {
        Self {
            child: Mutex::new(None),
            auto_spawn: Mutex::new(true),
        }
    }

    fn stop(&self) {
        if let Ok(mut guard) = self.child.lock() {
            if let Some(mut child) = guard.take() {
                let _ = child.kill();
            }
        }
    }

    fn disable_auto_spawn(&self) {
        if let Ok(mut guard) = self.auto_spawn.lock() {
            *guard = false;
        }
    }

    fn should_auto_spawn(&self) -> bool {
        if let Ok(guard) = self.auto_spawn.lock() {
            *guard
        } else {
            true
        }
    }
}

fn first_existing<'a>(base: &Path, list: &[(BackendKind, &'a str)]) -> Option<(BackendKind, PathBuf)> {
    for (kind, rel) in list {
        let p = base.join(rel);
        if p.exists() {
            return Some((*kind, p));
        }
    }
    None
}

fn project_root_candidates() -> Vec<PathBuf> {
    // CARGO_MANIFEST_DIR 指向 src-tauri，向上两级到项目根
    let manifest = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    vec![
        manifest.clone(),
        manifest.join(".."),
        manifest.join("../.."),
    ]
}

fn resolve_backend_path(app: &AppHandle) -> Option<(BackendKind, PathBuf)> {
    // 1) 明确指定路径优先
    if let Ok(custom) = env::var("OS_BACKEND_PATH") {
        let p = PathBuf::from(custom);
        if p.exists() {
            let kind = if p.extension().and_then(|e| e.to_str()) == Some("jar") {
                BackendKind::Jar
            } else {
                BackendKind::Exe
            };
            return Some((kind, p));
        }
    }

    // 2) 打包后的资源目录
    let resolver = app.path_resolver();
    if let Some(resource_dir) = resolver.resource_dir() {
        if let Some(found) = first_existing(
            &resource_dir,
            &[ 
                (BackendKind::Exe, "os-kernel.exe"),
                (BackendKind::Exe, "backend/os-kernel.exe"),
                (BackendKind::Jar, "os-core-1.0.0.jar"),
                (BackendKind::Jar, "backend/os-core-1.0.0.jar"),
            ],
        ) {
            return Some(found);
        }
    }

        // 3) 运行目录（tauri dev/debug 下往往在 target/debug）
        let exe_dir = env::current_exe().ok().and_then(|p| p.parent().map(|pp| pp.to_path_buf()));
        if let Some(exe_dir) = exe_dir {
            let bases = [exe_dir.clone(), exe_dir.join(".."), exe_dir.join("../..")];
            for b in bases {
                if let Some(found) = first_existing(
                    &b,
                    &[ 
                        (BackendKind::Exe, "backend/bin/os-kernel.exe"),
                        (BackendKind::Jar, "os-core/target/os-core-1.0.0.jar"),
                    ],
                ) {
                    return Some(found);
                }
            }
        }

    // 4) 开发目录（cwd 通常是 frontend/ 或 src-tauri/）
    if let Ok(cwd) = env::current_dir() {
        let bases = [
            cwd.clone(),
            cwd.join(".."),          // frontend -> root
            cwd.join("../../"),       // src-tauri -> root
            cwd.join("../.."),        // target/debug -> root (if run from there)
        ];
        let rels = [
            (BackendKind::Exe, "backend/bin/os-kernel.exe"),
            (BackendKind::Exe, "../backend/bin/os-kernel.exe"),
            (BackendKind::Exe, "../../backend/bin/os-kernel.exe"),
            (BackendKind::Jar, "os-core/target/os-core-1.0.0.jar"),
            (BackendKind::Jar, "../os-core/target/os-core-1.0.0.jar"),
            (BackendKind::Jar, "../../os-core/target/os-core-1.0.0.jar"),
        ];
        for b in bases {
            if let Some(found) = first_existing(&b, &rels) {
                return Some(found);
            }
        }
    }

    // 5) 构建时基于 CARGO_MANIFEST_DIR 的候选（更可靠）
    for b in project_root_candidates() {
        if let Some(found) = first_existing(&b, &[ 
            (BackendKind::Exe, "backend/bin/os-kernel.exe"),
            (BackendKind::Jar, "os-core/target/os-core-1.0.0.jar"),
        ]) {
            return Some(found);
        }
    }

    None
}

fn spawn_backend(app: &AppHandle, state: &BackendState) -> Result<(), String> {
    let mut guard = state
        .child
        .lock()
        .map_err(|_| "后端进程锁被占用，请重试".to_string())?;

    // 如果已经在跑就直接复用
    if let Some(child) = guard.as_mut() {
        if let Ok(None) = child.try_wait() {
            return Ok(());
        }
        // 已退出则清理
        *guard = None;
    }

    let (kind, path) = resolve_backend_path(app).ok_or_else(|| {
        eprintln!("[backend] 未找到后端可执行文件或 JAR，可设置 OS_BACKEND_PATH 指向 exe 或 jar");
        "未找到后端可执行文件或 JAR，请先在 IDEA 里用 Maven 打包 os-core 模块；也可以设置环境变量 OS_BACKEND_PATH 指定路径".to_string()
    })?;

    let mut cmd = match kind {
        BackendKind::Jar => {
            let mut c = Command::new("java");
            c.arg("-jar").arg(&path);
            c
        }
        BackendKind::Exe => Command::new(&path),
    };

    // 设置后端需要的环境变量：HTTP 端口
    cmd.env("OS_HTTP_PORT", "8080");
    // 将磁盘映像放到应用数据目录下，避免写入 src-tauri 导致 dev 重建
    if let Some(app_dir) = app.path_resolver().app_data_dir() {
        let disk_path = app_dir.join("disk.img");
        // Ensure parent directory exists so backend can write the disk image reliably
        if let Some(parent) = disk_path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        cmd.env("OS_DISK_PATH", disk_path.to_string_lossy().to_string());
    }

    cmd.stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    println!("[backend] launching {:?}", path);
    let child = cmd.spawn().map_err(|e| format!("启动后端失败: {e}"))?;
    *guard = Some(child);

    Ok(())
}

#[tauri::command]
fn ensure_backend(state: State<BackendState>, app: AppHandle) -> Result<(), String> {
    spawn_backend(&app, &state)
}

#[tauri::command]
fn stop_backend(state: State<BackendState>) -> Result<(), String> {
    state.stop();
    Ok(())
}

#[tauri::command]
fn shutdown_app(state: State<BackendState>, app: AppHandle) -> Result<(), String> {
    // 禁用自动重启后端，优雅停止后端并退出应用
    state.disable_auto_spawn();
    state.stop();
    app.exit(0);
    Ok(())
}

#[tauri::command]
fn force_close(app: AppHandle) -> Result<(), String> {
    if let Some(win) = app.get_window("main") {
        win.close().map_err(|e| format!("关闭窗口失败: {e}"))?;
    } else {
        return Err("未找到窗口 main".into());
    }
    Ok(())
}

#[tauri::command]
fn shutdown_all(_state: State<BackendState>, app: AppHandle) -> Result<(), String> {
    // 优雅关机：先发HTTP请求
    let _ = reqwest::blocking::get("http://localhost:8080/api/shutdown");
    // 等待后端进程优雅退出，最多等待 5 秒
    if let Some(state) = app.try_state::<BackendState>() {
        // 禁用自动 spawn，确保不会在关闭后被重新拉起
        state.disable_auto_spawn();
        if let Ok(mut guard) = state.child.lock() {
            if let Some(child) = guard.as_mut() {
                for _ in 0..25 {
                    if let Ok(Some(_)) = child.try_wait() {
                        break;
                    }
                    std::thread::sleep(std::time::Duration::from_millis(200));
                }
            }
        }
    }

    // 关闭窗口并退出
    if let Some(win) = app.get_window("main") {
        let _ = win.close();
    }
    app.exit(0);
    Ok(())
}

fn main() {
    tauri::Builder::default()
        .manage(BackendState::new())
        .invoke_handler(tauri::generate_handler![
            ping,
            ensure_backend,
            stop_backend,
            shutdown_app,
            force_close,
            shutdown_all
        ])
        .setup(|app| {
              // 应用启动时尝试拉起后端（尊重 auto_spawn 标志）
              if let Some(state) = app.try_state::<BackendState>() {
                  if state.should_auto_spawn() {
                     let _ = spawn_backend(&app.handle(), &state);
                  }
              }
            Ok(())
        })
        .on_window_event(|event| {
            if let WindowEvent::CloseRequested { .. } = event.event() {
                if let Some(state) = event.window().app_handle().try_state::<BackendState>() {
                    state.stop();
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
