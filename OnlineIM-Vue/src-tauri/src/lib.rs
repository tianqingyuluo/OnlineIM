mod config;
mod e2ee_store;
mod notification;
mod state;
mod tray;

use std::sync::atomic::Ordering;

use tauri::{Manager, RunEvent, WindowEvent, Wry};

use crate::config::{
    config_from_env, env_from_process, load_server_config, save_server_config, validate_server_config, ServerConfig,
};
use crate::e2ee_store::{delete_bundle, load_bundle, save_bundle};
use crate::notification::{install_notification_actions, send_native_notification};
use crate::state::DesktopState;
use crate::tray::{install_tray, quit_app, set_unread_badge, should_hide_on_close};

fn config_dir(app: &tauri::AppHandle<Wry>) -> Result<std::path::PathBuf, String> {
    app.path().app_config_dir().map_err(|error| error.to_string())
}

fn key_dir(app: &tauri::AppHandle<Wry>) -> Result<std::path::PathBuf, String> {
    Ok(app.path().app_data_dir().map_err(|error| error.to_string())?.join("e2ee"))
}

#[tauri::command]
fn get_server_config(app: tauri::AppHandle<Wry>) -> ServerConfig {
    let env = env_from_process();
    match config_dir(&app) {
        Ok(dir) => load_server_config(&dir, &env),
        Err(_) => config_from_env(&env),
    }
}

#[tauri::command]
fn set_server_config(app: tauri::AppHandle<Wry>, api_base_url: String, ws_base_url: String) -> Result<ServerConfig, String> {
    let config = validate_server_config(&api_base_url, &ws_base_url)?;
    save_server_config(&config_dir(&app)?, &config)?;
    Ok(config)
}

#[tauri::command]
fn save_e2ee_key_bundle(app: tauri::AppHandle<Wry>, user_id: String, bundle_json: String) -> Result<(), String> {
    save_bundle(&key_dir(&app)?, &user_id, &bundle_json)
}

#[tauri::command]
fn load_e2ee_key_bundle(app: tauri::AppHandle<Wry>, user_id: String) -> Result<Option<String>, String> {
    load_bundle(&key_dir(&app)?, &user_id)
}

#[tauri::command]
fn delete_e2ee_key_bundle(app: tauri::AppHandle<Wry>, user_id: String) -> Result<(), String> {
    delete_bundle(&key_dir(&app)?, &user_id)
}

pub fn run() {
    tauri::Builder::<Wry>::default()
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            tray::show_main_window(app);
        }))
        .manage(DesktopState::default())
        .invoke_handler(tauri::generate_handler![
            set_unread_badge,
            send_native_notification,
            get_server_config,
            set_server_config,
            save_e2ee_key_bundle,
            load_e2ee_key_bundle,
            delete_e2ee_key_bundle,
            quit_app,
        ])
        .setup(|app| {
            install_notification_actions(app.handle())?;
            install_tray(app.handle())?;
            let handle = app.handle().clone();
            if let Some(window) = app.get_webview_window("main") {
                window.on_window_event(move |event| {
                    if let WindowEvent::CloseRequested { api, .. } = event {
                        let quitting = handle
                            .try_state::<DesktopState>()
                            .is_some_and(|state| state.quitting.load(Ordering::SeqCst));
                        if should_hide_on_close(quitting) {
                            api.prevent_close();
                            if let Some(window) = handle.get_webview_window("main") {
                                let _ = window.hide();
                            }
                        }
                    }
                });
            }
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("启动 OnlineIM 桌面端失败")
        .run(|app, event| {
            if let RunEvent::ExitRequested { api, code, .. } = event {
                let quitting = app
                    .try_state::<DesktopState>()
                    .is_some_and(|state| state.quitting.load(Ordering::SeqCst));
                if !quitting && code.is_none() {
                    api.prevent_exit();
                }
            }
        });
}
