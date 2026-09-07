use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use once_cell::sync::Lazy;

static NEXT_TOKEN_ID: AtomicU64 = AtomicU64::new(1);
static CANCEL_REGISTRY: Lazy<Mutex<HashMap<u64, Arc<AtomicBool>>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

pub struct CancelManager;

impl CancelManager {
    pub fn create_token() -> (u64, Arc<AtomicBool>) {
        let id = NEXT_TOKEN_ID.fetch_add(1, Ordering::SeqCst);
        let flag = Arc::new(AtomicBool::new(false));
        if let Ok(mut registry) = CANCEL_REGISTRY.lock() {
            registry.insert(id, flag.clone());
        }
        (id, flag)
    }

    pub fn trigger_cancel(id: u64) -> bool {
        if let Ok(registry) = CANCEL_REGISTRY.lock() {
            if let Some(flag) = registry.get(&id) {
                flag.store(true, Ordering::SeqCst);
                return true;
            }
        }
        false
    }

    pub fn free_token(id: u64) {
        if let Ok(mut registry) = CANCEL_REGISTRY.lock() {
            registry.remove(&id);
        }
    }
}
