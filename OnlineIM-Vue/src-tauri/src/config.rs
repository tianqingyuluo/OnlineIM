use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};

pub const DEFAULT_API_BASE_URL: &str = "http://localhost:8080/api/v1";
pub const DEFAULT_WS_BASE_URL: &str = "ws://localhost:8081/api/v1/chat";
pub const CONFIG_FILE_NAME: &str = "server-config.json";
pub const ENV_API_BASE_URL: &str = "ONLINEIM_API_BASE_URL";
pub const ENV_WS_BASE_URL: &str = "ONLINEIM_WS_BASE_URL";

/// 桌面端后端地址。持久化文件优先于环境变量，环境变量优先于内置默认值。
/// 设置页写入文件之后，下次启动仍以用户保存的地址为准。
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct ServerConfig {
    pub api_base_url: String,
    pub ws_base_url: String,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            api_base_url: DEFAULT_API_BASE_URL.to_string(),
            ws_base_url: DEFAULT_WS_BASE_URL.to_string(),
        }
    }
}

pub fn config_file(config_dir: &Path) -> PathBuf {
    config_dir.join(CONFIG_FILE_NAME)
}

pub fn normalize_endpoint(value: &str, kind: EndpointKind) -> Result<String, String> {
    let trimmed = value.trim().trim_end_matches('/');
    if trimmed.is_empty() {
        return Err("地址不能为空".to_string());
    }
    if trimmed.chars().any(|ch| ch.is_whitespace()) {
        return Err("地址不能包含空白".to_string());
    }
    let lower = trimmed.to_ascii_lowercase();
    let allowed = match kind {
        EndpointKind::Api => lower.starts_with("http://") || lower.starts_with("https://"),
        EndpointKind::WebSocket => lower.starts_with("ws://") || lower.starts_with("wss://"),
    };
    if !allowed {
        return Err(match kind {
            EndpointKind::Api => "API 地址必须以 http:// 或 https:// 开头".to_string(),
            EndpointKind::WebSocket => "WebSocket 地址必须以 ws:// 或 wss:// 开头".to_string(),
        });
    }
    Ok(trimmed.to_string())
}

#[derive(Clone, Copy)]
pub enum EndpointKind {
    Api,
    WebSocket,
}

pub fn validate_server_config(api_base_url: &str, ws_base_url: &str) -> Result<ServerConfig, String> {
    Ok(ServerConfig {
        api_base_url: normalize_endpoint(api_base_url, EndpointKind::Api)?,
        ws_base_url: normalize_endpoint(ws_base_url, EndpointKind::WebSocket)?,
    })
}

fn overlay_env(config: &mut ServerConfig, env: &HashMap<String, String>) {
    if let Some(value) = non_empty(env.get(ENV_API_BASE_URL)) {
        if let Ok(normalized) = normalize_endpoint(value, EndpointKind::Api) {
            config.api_base_url = normalized;
        }
    }
    if let Some(value) = non_empty(env.get(ENV_WS_BASE_URL)) {
        if let Ok(normalized) = normalize_endpoint(value, EndpointKind::WebSocket) {
            config.ws_base_url = normalized;
        }
    }
}

fn non_empty(value: Option<&String>) -> Option<&str> {
    value.map(|item| item.trim()).filter(|item| !item.is_empty())
}

fn read_saved(config_dir: &Path) -> Option<ServerConfig> {
    let path = config_file(config_dir);
    let text = fs::read_to_string(path).ok()?;
    let parsed: ServerConfig = serde_json::from_str(&text).ok()?;
    validate_server_config(&parsed.api_base_url, &parsed.ws_base_url).ok()
}

/// 读取顺序：内置默认值 → 环境变量 → `server-config.json`。
pub fn load_server_config(config_dir: &Path, env: &HashMap<String, String>) -> ServerConfig {
    let mut config = ServerConfig::default();
    overlay_env(&mut config, env);
    if let Some(saved) = read_saved(config_dir) {
        return saved;
    }
    config
}

pub fn save_server_config(config_dir: &Path, config: &ServerConfig) -> Result<(), String> {
    let checked = validate_server_config(&config.api_base_url, &config.ws_base_url)?;
    fs::create_dir_all(config_dir).map_err(|error| format!("创建配置目录失败: {error}"))?;
    let path = config_file(config_dir);
    let body = serde_json::to_string_pretty(&checked).map_err(|error| format!("序列化配置失败: {error}"))?;
    fs::write(path, body).map_err(|error| format!("写入配置失败: {error}"))?;
    Ok(())
}

pub fn config_from_env(env: &HashMap<String, String>) -> ServerConfig {
    let mut config = ServerConfig::default();
    overlay_env(&mut config, env);
    config
}

pub fn env_from_process() -> HashMap<String, String> {
    let mut env = HashMap::new();
    for key in [ENV_API_BASE_URL, ENV_WS_BASE_URL] {
        if let Ok(value) = std::env::var(key) {
            env.insert(key.to_string(), value);
        }
    }
    env
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT: AtomicU64 = AtomicU64::new(1);

    fn temp_dir() -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "onlineim-config-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn defaults_when_nothing_is_configured() {
        let dir = temp_dir();
        let loaded = load_server_config(&dir, &HashMap::new());
        assert_eq!(loaded, ServerConfig::default());
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn environment_overrides_defaults() {
        let dir = temp_dir();
        let mut env = HashMap::new();
        env.insert(ENV_API_BASE_URL.to_string(), "https://im.example.com/api/v1/".to_string());
        env.insert(ENV_WS_BASE_URL.to_string(), "wss://im.example.com/api/v1/chat".to_string());
        let loaded = load_server_config(&dir, &env);
        assert_eq!(loaded.api_base_url, "https://im.example.com/api/v1");
        assert_eq!(loaded.ws_base_url, "wss://im.example.com/api/v1/chat");
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn saved_file_overrides_environment() {
        let dir = temp_dir();
        let saved = validate_server_config("http://10.0.0.8:8080/api/v1", "ws://10.0.0.8:8081/api/v1/chat").unwrap();
        save_server_config(&dir, &saved).unwrap();
        let mut env = HashMap::new();
        env.insert(ENV_API_BASE_URL.to_string(), "https://ignored.example/api/v1".to_string());
        env.insert(ENV_WS_BASE_URL.to_string(), "wss://ignored.example/chat".to_string());
        assert_eq!(load_server_config(&dir, &env), saved);
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn rejects_blank_and_wrong_scheme() {
        assert!(validate_server_config("   ", "ws://localhost:8081/api/v1/chat").is_err());
        assert!(validate_server_config("ftp://localhost/api", "ws://localhost:8081/api/v1/chat").is_err());
        assert!(validate_server_config("http://localhost:8080/api/v1", "http://localhost:8081/chat").is_err());
        assert!(validate_server_config("http://local host/api/v1", "ws://localhost:8081/api/v1/chat").is_err());
    }

    #[test]
    fn broken_file_falls_back_to_environment() {
        let dir = temp_dir();
        fs::write(config_file(&dir), "{not json").unwrap();
        let mut env = HashMap::new();
        env.insert(ENV_API_BASE_URL.to_string(), "http://127.0.0.1:8080/api/v1".to_string());
        env.insert(ENV_WS_BASE_URL.to_string(), "ws://127.0.0.1:8081/api/v1/chat".to_string());
        let loaded = load_server_config(&dir, &env);
        assert_eq!(loaded.api_base_url, "http://127.0.0.1:8080/api/v1");
        assert_eq!(loaded.ws_base_url, "ws://127.0.0.1:8081/api/v1/chat");
        let _ = fs::remove_dir_all(dir);
    }
}
