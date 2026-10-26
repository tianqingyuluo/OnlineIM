use std::sync::atomic::{AtomicBool, AtomicI32, AtomicU32};
use std::sync::Mutex;

use tauri::menu::MenuItem;
use tauri::Wry;

pub struct DesktopState {
    pub quitting: AtomicBool,
    pub muted: AtomicBool,
    pub unread: AtomicU32,
    pub next_notification_id: AtomicI32,
    pub last_conversation_id: Mutex<Option<String>>,
    pub mute_item: Mutex<Option<MenuItem<Wry>>>,
}

impl Default for DesktopState {
    fn default() -> Self {
        Self {
            quitting: AtomicBool::new(false),
            muted: AtomicBool::new(false),
            unread: AtomicU32::new(0),
            next_notification_id: AtomicI32::new(1),
            last_conversation_id: Mutex::new(None),
            mute_item: Mutex::new(None),
        }
    }
}

impl DesktopState {
    pub fn remember_conversation(&self, conversation_id: Option<String>) {
        if let Ok(mut slot) = self.last_conversation_id.lock() {
            *slot = conversation_id;
        }
    }

    pub fn last_conversation(&self) -> Option<String> {
        self.last_conversation_id.lock().ok().and_then(|slot| slot.clone())
    }
}
