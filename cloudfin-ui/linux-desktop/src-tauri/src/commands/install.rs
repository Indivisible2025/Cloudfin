use std::path::PathBuf;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};

#[derive(Clone, Serialize)]
pub struct Progress {
    stage: String,
    percent: f32,
    message: String,
}

#[derive(Deserialize)]
pub struct InstallRequest {
    pub items: Vec<InstallItem>,
    pub install_dir: Option<String>,
}

#[derive(Deserialize)]
pub struct InstallItem {
    pub name: String,       // "core" | "p2p" | "crdt"
    pub url: String,
    pub filename: String,   // e.g. "Cloudfin-Core-Linux-amd64-v2026.4.5.1.tar.gz"
}

#[tauri::command]
pub async fn install_execute(
    app: AppHandle,
    request: InstallRequest,
) -> Result<(), String> {
    let default_dir = dirs::home_dir()
        .map(|h| h.join(".cloudfin"))
        .unwrap_or_else(|| PathBuf::from("/tmp/cloudfin"));
    let install_dir = request.install_dir
        .map(PathBuf::from)
        .unwrap_or(default_dir);

    std::fs::create_dir_all(&install_dir).map_err(|e| e.to_string())?;
    std::fs::create_dir_all(install_dir.join("modules")).map_err(|e| e.to_string())?;

    let total = request.items.len();
    for (i, item) in request.items.iter().enumerate() {
        let percent = (i as f32 / total as f32) * 100.0;

        let _ = app.emit("install_progress", Progress {
            stage: "downloading".into(),
            percent,
            message: format!("下载 {}...", item.name),
        });

        // Download to temp
        let temp_path = std::env::temp_dir().join(&item.filename);
        download_file(&item.url, &temp_path).await?;

        let _ = app.emit("install_progress", Progress {
            stage: "extracting".into(),
            percent: percent + 40.0 / total as f32,
            message: format!("解压 {}...", item.name),
        });

        // Extract
        extract_file(&temp_path, &install_dir, &item.filename)?;

        // Cleanup temp
        let _ = std::fs::remove_file(&temp_path);

        let _ = app.emit("install_progress", Progress {
            stage: "done".into(),
            percent: ((i + 1) as f32 / total as f32) * 100.0,
            message: format!("{} 安装完成", item.name),
        });
    }

    Ok(())
}

async fn download_file(url: &str, dest: &PathBuf) -> Result<(), String> {
    let client = reqwest::Client::new();
    let response = client.get(url)
        .send()
        .await
        .map_err(|e| format!("下载失败: {}", e))?;

    let mut file = std::fs::File::create(dest).map_err(|e| e.to_string())?;
    let mut stream = response.bytes_stream();
    use futures_util::StreamExt;
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| format!("下载错误: {}", e))?;
        std::io::Write::write_all(&mut file, &chunk).map_err(|e| e.to_string())?;
    }
    Ok(())
}

fn extract_file(src: &PathBuf, dest: &PathBuf, filename: &str) -> Result<(), String> {
    if filename.ends_with(".tar.gz") || filename.ends_with(".tgz") {
        let file = std::fs::File::open(src).map_err(|e| e.to_string())?;
        let mut archive = tar::Archive::new(flate2::read::GzDecoder::new(file));
        archive.unpack(dest).map_err(|e| format!("tar 解压失败: {}", e))?;
    } else if filename.ends_with(".zip") {
        let file = std::fs::File::open(src).map_err(|e| e.to_string())?;
        let mut archive = zip::ZipArchive::new(file).map_err(|e| format!("zip 解压失败: {}", e))?;
        archive.extract(dest).map_err(|e| format!("zip 解压失败: {}", e))?;
    } else if filename.ends_with(".deb") {
        // Extract deb package contents to dest (dpkg-deb -x style)
        let output = std::process::Command::new("dpkg-deb")
            .args(["-x", src.to_str().unwrap_or_default(), dest.to_str().unwrap_or_default()])
            .output()
            .map_err(|e| format!("dpkg-deb 执行失败: {}", e))?;
        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(format!("dpkg-deb 解压失败: {}", stderr));
        }
    } else if filename.ends_with(".AppImage") {
        // Copy AppImage to dest and make executable
        let appimage_name = filename.trim_end_matches(".AppImage");
        let dest_path = dest.join(filename);
        std::fs::copy(src, &dest_path).map_err(|e| format!("AppImage 复制失败: {}", e))?;
        // Make executable
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mut perms = std::fs::metadata(&dest_path)
                .map_err(|e| e.to_string())?
                .permissions();
            perms.set_mode(0o755);
            std::fs::set_permissions(&dest_path, perms)
                .map_err(|e| format!("设置权限失败: {}", e))?;
        }
        // Create a launcher script
        let launcher = dest.join("cloudfin-launcher.sh");
        let launcher_content = format!("#!/bin/bash\ncd \"$(dirname \"$0\")\"\nexec \"./{}\",\n", filename);
        std::fs::write(&launcher, launcher_content)
            .map_err(|e| format!("创建启动器失败: {}", e))?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(&launcher, std::fs::Permissions::from_mode(0o755))
                .map_err(|e| e.to_string())?;
        }
    } else {
        return Err(format!("不支持的文件格式: {}", filename));
    }
    Ok(())
}
