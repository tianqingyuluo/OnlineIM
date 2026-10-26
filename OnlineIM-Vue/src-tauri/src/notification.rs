use std::sync::atomic::Ordering;

use tauri::{AppHandle, Emitter, Manager, Wry};
use tauri_plugin_notification::{Action, ActionType, NotificationExt};

use crate::state::DesktopState;
use crate::tray::show_main_window;

pub const NOTIFICATION_ACTIVATED_EVENT: &str = "notification-activated";
pub const OPEN_ACTION: &str = "open";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NativeNotification {
    pub title: String,
    pub body: String,
    pub conversation_id: Option<String>,
}

fn clip(value: &str, max_chars: usize) -> String {
    value.trim().chars().take(max_chars).collect()
}

/// 静音时返回 `Ok(None)`，调用方不弹出系统通知。标题和正文都空则拒绝。
pub fn prepare_notification(
    title: &str,
    body: &str,
    conversation_id: Option<&str>,
    muted: bool,
) -> Result<Option<NativeNotification>, String> {
    if muted {
        return Ok(None);
    }
    let title = clip(title, 120);
    let body = clip(body, 500);
    if title.is_empty() && body.is_empty() {
        return Err("通知内容为空".to_string());
    }
    let conversation_id = conversation_id.and_then(|value| {
        let trimmed = value.trim();
        if trimmed.is_empty() {
            None
        } else {
            Some(clip(trimmed, 128))
        }
    });
    Ok(Some(NativeNotification {
        title: if title.is_empty() { "OnlineIM".to_string() } else { title },
        body,
        conversation_id,
    }))
}

pub fn notification_navigates(action_id: &str) -> bool {
    action_id != "dismiss"
}

pub fn install_notification_actions(app: &AppHandle<Wry>) -> Result<(), String> {
    let open = Action::builder(OPEN_ACTION, "打开").build();
    let action_type = ActionType::builder("message").actions(vec![open]).build();
    app.notification()
        .register_action_types(vec![action_type])
        .map_err(|error| error.to_string())?;
    let handle = app.clone();
    app.notification()
        .on_action(move |action| {
            if !notification_navigates(action.action_id()) {
                return;
            }
            let from_extra = action.notification().and_then(|item| {
                item.extra()
                    .get("conversation_id")
                    .and_then(|value| value.as_str())
                    .map(str::trim)
                    .filter(|text| !text.is_empty())
                    .map(ToString::to_string)
            });
            let conversation_id = from_extra.or_else(|| {
                handle
                    .try_state::<DesktopState>()
                    .and_then(|state| state.last_conversation())
            });
            show_main_window(&handle);
            let _ = handle.emit(
                NOTIFICATION_ACTIVATED_EVENT,
                serde_json::json!({ "conversation_id": conversation_id }),
            );
        })
        .map_err(|error| error.to_string())?;
    Ok(())
}

pub fn show_native_notification(app: &AppHandle<Wry>, note: &NativeNotification) -> Result<(), String> {
    let state = app.try_state::<DesktopState>().ok_or_else(|| "桌面状态未初始化".to_string())?;
    let id = state.next_notification_id.fetch_add(1, Ordering::Relaxed);
    state.remember_conversation(note.conversation_id.clone());
    let mut builder = app
        .notification()
        .builder()
        .id(id)
        .title(&note.title)
        .body(&note.body)
        .action_type_id("message");
    if let Some(conversation_id) = &note.conversation_id {
        builder = builder.extra("conversation_id", conversation_id.clone());
    }
    builder.show().map_err(|error| error.to_string())
}

#[tauri::command]
pub fn send_native_notification(
    app: AppHandle<Wry>,
    title: String,
    body: String,
    conversation_id: Option<String>,
) -> Result<bool, String> {
    let muted = app
        .try_state::<DesktopState>()
        .is_some_and(|state| state.muted.load(Ordering::SeqCst));
    let Some(note) = prepare_notification(&title, &body, conversation_id.as_deref(), muted)? else {
        return Ok(false);
    };
    show_native_notification(&app, &note)?;
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prepares_a_notification_and_keeps_conversation_id() {
        let prepared = prepare_notification("  小明  ", "  你好 ", Some(" conv_1 "), false)
            .unwrap()
            .unwrap();
        assert_eq!(prepared.title, "小明");
        assert_eq!(prepared.body, "你好");
        assert_eq!(prepared.conversation_id.as_deref(), Some("conv_1"));
    }

    #[test]
    fn mute_suppresses_and_empty_payload_is_rejected() {
        assert!(prepare_notification("来电", "邀请你语音通话", Some("conv_1"), true)
            .unwrap()
            .is_none());
        assert!(prepare_notification("  ", " ", None, false).is_err());
        let fallback = prepare_notification("", "有新消息", Some("   "), false).unwrap().unwrap();
        assert_eq!(fallback.title, "OnlineIM");
        assert_eq!(fallback.conversation_id, None);
    }

    #[test]
    fn dismiss_does_not_navigate() {
        assert!(notification_navigates("tap"));
        assert!(notification_navigates(OPEN_ACTION));
        assert!(!notification_navigates("dismiss"));
    }
}
