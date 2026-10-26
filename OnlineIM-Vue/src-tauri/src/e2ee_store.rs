use std::fs;
use std::path::{Path, PathBuf};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

const MAX_BUNDLE_BYTES: usize = 1_000_000;

pub fn validate_user_id(user_id: &str) -> Result<&str, String> {
    let trimmed = user_id.trim();
    if trimmed.is_empty() || trimmed.len() > 128 {
        return Err("user_id 无效".to_string());
    }
    if !trimmed.chars().all(|ch| ch.is_ascii_alphanumeric() || ch == '_' || ch == '-') {
        return Err("user_id 含非法字符".to_string());
    }
    Ok(trimmed)
}

pub fn bundle_file(dir: &Path, user_id: &str) -> Result<PathBuf, String> {
    let user_id = validate_user_id(user_id)?;
    Ok(dir.join(format!("{user_id}.json")))
}

fn ensure_private_dir(dir: &Path) -> Result<(), String> {
    fs::create_dir_all(dir).map_err(|error| format!("创建密钥目录失败: {error}"))?;
    restrict_permissions(dir, 0o700);
    Ok(())
}

fn restrict_permissions(path: &Path, mode: u32) {
    #[cfg(unix)]
    {
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(mode));
    }
    #[cfg(not(unix))]
    {
        let _ = (path, mode);
    }
}

pub fn save_bundle(dir: &Path, user_id: &str, bundle_json: &str) -> Result<(), String> {
    if bundle_json.len() > MAX_BUNDLE_BYTES {
        return Err("密钥包过大".to_string());
    }
    serde_json::from_str::<serde_json::Value>(bundle_json).map_err(|_| "bundle_json 不是合法 JSON".to_string())?;
    ensure_private_dir(dir)?;
    let path = bundle_file(dir, user_id)?;
    let temporary = path.with_extension("json.tmp");
    fs::write(&temporary, bundle_json).map_err(|error| format!("写入密钥失败: {error}"))?;
    restrict_permissions(&temporary, 0o600);
    fs::rename(&temporary, &path).map_err(|error| format!("保存密钥失败: {error}"))?;
    restrict_permissions(&path, 0o600);
    Ok(())
}

pub fn load_bundle(dir: &Path, user_id: &str) -> Result<Option<String>, String> {
    let path = bundle_file(dir, user_id)?;
    match fs::read_to_string(path) {
        Ok(body) => Ok(Some(body)),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(format!("读取密钥失败: {error}")),
    }
}

pub fn delete_bundle(dir: &Path, user_id: &str) -> Result<(), String> {
    let path = bundle_file(dir, user_id)?;
    match fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(format!("删除密钥失败: {error}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT: AtomicU64 = AtomicU64::new(1);

    fn temp_dir() -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "onlineim-e2ee-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        let _ = fs::remove_dir_all(&dir);
        dir
    }

    #[test]
    fn round_trip_and_delete() {
        let dir = temp_dir();
        assert_eq!(load_bundle(&dir, "usr_a").unwrap(), None);
        save_bundle(&dir, "usr_a", r#"{"keyId":"key_1","identityPrivateJwk":{"d":"secret"}}"#).unwrap();
        let loaded = load_bundle(&dir, " usr_a ").unwrap().unwrap();
        assert!(loaded.contains("secret"));
        delete_bundle(&dir, "usr_a").unwrap();
        assert_eq!(load_bundle(&dir, "usr_a").unwrap(), None);
        delete_bundle(&dir, "usr_a").unwrap();
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn rejects_path_traversal_and_invalid_json() {
        let dir = temp_dir();
        assert!(save_bundle(&dir, "../etc/passwd", "{}").is_err());
        assert!(save_bundle(&dir, "a/b", "{}").is_err());
        assert!(save_bundle(&dir, "", "{}").is_err());
        assert!(save_bundle(&dir, "usr_a", "not-json").is_err());
        assert!(!dir.join("passwd").exists());
        let _ = fs::remove_dir_all(dir);
    }

    #[cfg(unix)]
    #[test]
    fn unix_permissions_are_owner_only() {
        let dir = temp_dir();
        save_bundle(&dir, "usr_desktop", r#"{"keyId":"k"}"#).unwrap();
        let file_mode = fs::metadata(bundle_file(&dir, "usr_desktop").unwrap()).unwrap().permissions().mode() & 0o777;
        let dir_mode = fs::metadata(&dir).unwrap().permissions().mode() & 0o777;
        assert_eq!(file_mode, 0o600);
        assert_eq!(dir_mode, 0o700);
        let _ = fs::remove_dir_all(dir);
    }
}
