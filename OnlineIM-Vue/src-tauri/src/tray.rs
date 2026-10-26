#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TrayIconKind {
    Normal,
    Unread,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BadgeUpdate {
    pub count: u32,
    pub tooltip: String,
    pub icon: TrayIconKind,
}

pub const MENU_SHOW: &str = "show";
pub const MENU_MUTE: &str = "mute";
pub const MENU_QUIT: &str = "quit";
pub const TRAY_ID: &str = "main";

pub fn unread_tooltip(count: u32) -> String {
    if count == 0 {
        "OnlineIM".to_string()
    } else {
        format!("OnlineIM ({count} 条未读)")
    }
}

pub fn tray_icon_kind(count: u32) -> TrayIconKind {
    if count == 0 {
        TrayIconKind::Normal
    } else {
        TrayIconKind::Unread
    }
}

pub fn badge_update(count: u32) -> BadgeUpdate {
    BadgeUpdate {
        count,
        tooltip: unread_tooltip(count),
        icon: tray_icon_kind(count),
    }
}

pub fn mute_menu_label(muted: bool) -> &'static str {
    if muted {
        "取消静音"
    } else {
        "静音通知"
    }
}

/// 关闭窗口时隐藏到托盘。只有托盘菜单或 `quit_app` 把 quitting 置上之后才真正退出。
pub fn should_hide_on_close(quitting: bool) -> bool {
    !quitting
}

use std::sync::atomic::Ordering;

use tauri::image::Image;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, Wry};

use crate::state::DesktopState;

const TRAY_NORMAL: &[u8] = include_bytes!("../icons/tray.png");
const TRAY_UNREAD: &[u8] = include_bytes!("../icons/icon-unread.png");

pub fn show_main_window(app: &AppHandle<Wry>) {
    let Some(window) = app.get_webview_window("main") else {
        return;
    };
    let _ = window.unminimize();
    let _ = window.show();
    let _ = window.set_focus();
}

pub fn request_quit(app: &AppHandle<Wry>) {
    if let Some(state) = app.try_state::<DesktopState>() {
        state.quitting.store(true, Ordering::SeqCst);
    }
    app.exit(0);
}

pub fn apply_unread_badge(app: &AppHandle<Wry>, count: u32) {
    let update = badge_update(count);
    let Some(tray) = app.tray_by_id(TRAY_ID) else {
        return;
    };
    let _ = tray.set_tooltip(Some(update.tooltip));
    let bytes = match update.icon {
        TrayIconKind::Unread => TRAY_UNREAD,
        TrayIconKind::Normal => TRAY_NORMAL,
    };
    if let Ok(icon) = Image::from_bytes(bytes) {
        let _ = tray.set_icon(Some(icon));
    }
}

fn toggle_mute(app: &AppHandle<Wry>) {
    let Some(state) = app.try_state::<DesktopState>() else {
        return;
    };
    let muted = !state.muted.load(Ordering::SeqCst);
    state.muted.store(muted, Ordering::SeqCst);
    if let Ok(guard) = state.mute_item.lock() {
        if let Some(item) = guard.as_ref() {
            let _ = item.set_text(mute_menu_label(muted));
        }
    };
}

pub fn install_tray(app: &AppHandle<Wry>) -> Result<(), String> {
    let show = MenuItem::with_id(app, MENU_SHOW, "显示主窗口", true, None::<&str>)
        .map_err(|error| error.to_string())?;
    let mute = MenuItem::with_id(app, MENU_MUTE, mute_menu_label(false), true, None::<&str>)
        .map_err(|error| error.to_string())?;
    let quit = MenuItem::with_id(app, MENU_QUIT, "退出", true, None::<&str>)
        .map_err(|error| error.to_string())?;
    let menu = Menu::with_items(app, &[&show, &mute, &quit]).map_err(|error| error.to_string())?;
    if let Some(state) = app.try_state::<DesktopState>() {
        if let Ok(mut slot) = state.mute_item.lock() {
            *slot = Some(mute.clone());
        };
    };
    let icon = Image::from_bytes(TRAY_NORMAL).map_err(|error| error.to_string())?;
    TrayIconBuilder::with_id(TRAY_ID)
        .icon(icon)
        .menu(&menu)
        .tooltip("OnlineIM")
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id.as_ref() {
            MENU_SHOW => show_main_window(app),
            MENU_MUTE => toggle_mute(app),
            MENU_QUIT => request_quit(app),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                show_main_window(tray.app_handle());
            }
        })
        .build(app)
        .map_err(|error| error.to_string())?;
    Ok(())
}

#[tauri::command]
pub fn set_unread_badge(app: AppHandle<Wry>, count: u32) -> Result<(), String> {
    if let Some(state) = app.try_state::<DesktopState>() {
        state.unread.store(count, Ordering::Relaxed);
    }
    apply_unread_badge(&app, count);
    Ok(())
}

#[tauri::command]
pub fn quit_app(app: AppHandle<Wry>) {
    request_quit(&app);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tooltip_and_icon_follow_unread_count() {
        assert_eq!(badge_update(0).tooltip, "OnlineIM");
        assert_eq!(badge_update(0).icon, TrayIconKind::Normal);
        let unread = badge_update(3);
        assert_eq!(unread.tooltip, "OnlineIM (3 条未读)");
        assert_eq!(unread.icon, TrayIconKind::Unread);
        assert_eq!(badge_update(1).icon, TrayIconKind::Unread);
    }

    #[test]
    fn close_hides_until_quit_is_requested() {
        assert!(should_hide_on_close(false));
        assert!(!should_hide_on_close(true));
        assert_eq!(mute_menu_label(false), "静音通知");
        assert_eq!(mute_menu_label(true), "取消静音");
    }
}
