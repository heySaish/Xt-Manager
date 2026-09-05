use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use once_cell::sync::Lazy;

use super::traits::ArchiveEntryItem;

pub struct CachedArchiveIndex {
    pub last_accessed: Instant,
    pub entries: Vec<ArchiveEntryItem>,
}

static SESSION_CACHE: Lazy<Mutex<HashMap<PathBuf, CachedArchiveIndex>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

const SESSION_TTL: Duration = Duration::from_secs(60);

pub struct ArchiveSessionManager;

impl ArchiveSessionManager {
    pub fn get_or_insert<F>(archive_path: &Path, builder: F) -> Vec<ArchiveEntryItem>
    where
        F: FnOnce() -> Vec<ArchiveEntryItem>,
    {
        let mut cache = SESSION_CACHE.lock().unwrap();

        // Evict expired sessions
        let now = Instant::now();
        cache.retain(|_, v| now.duration_since(v.last_accessed) < SESSION_TTL);

        let entry = cache
            .entry(archive_path.to_path_buf())
            .or_insert_with(|| CachedArchiveIndex {
                last_accessed: now,
                entries: builder(),
            });

        entry.last_accessed = now;
        entry.entries.clone()
    }

    /// Filters cached archive entries to return ONLY immediate children of `virtual_prefix`
    pub fn filter_immediate_children(
        all_entries: &[ArchiveEntryItem],
        virtual_prefix: &str,
    ) -> Vec<ArchiveEntryItem> {
        let clean_prefix = virtual_prefix
            .trim_start_matches('/')
            .trim_end_matches('/');

        let prefix_slash = if clean_prefix.is_empty() {
            String::new()
        } else {
            format!("{}/", clean_prefix)
        };

        let mut direct_children_map: HashMap<String, ArchiveEntryItem> = HashMap::new();

        for entry in all_entries {
            let rel_path = entry.virtual_path.trim_start_matches('/');
            if !prefix_slash.is_empty() && !rel_path.starts_with(&prefix_slash) {
                continue;
            }

            let sub_path = if prefix_slash.is_empty() {
                rel_path
            } else {
                &rel_path[prefix_slash.len()..]
            };

            if sub_path.is_empty() {
                continue;
            }

            // Split by '/' to find immediate child component
            let parts: Vec<&str> = sub_path.split('/').filter(|s| !s.is_empty()).collect();
            if parts.is_empty() {
                continue;
            }

            let child_name = parts[0].to_string();
            let is_child_dir = parts.len() > 1 || entry.is_dir;

            let child_virtual_path = if prefix_slash.is_empty() {
                child_name.clone()
            } else {
                format!("{}{}", prefix_slash, child_name)
            };

            direct_children_map
                .entry(child_name.clone())
                .or_insert_with(|| ArchiveEntryItem {
                    name: child_name,
                    virtual_path: child_virtual_path,
                    is_dir: is_child_dir,
                    uncompressed_size: if is_child_dir { 0 } else { entry.uncompressed_size },
                    last_modified: entry.last_modified,
                    is_encrypted: entry.is_encrypted,
                });
        }

        let mut result: Vec<ArchiveEntryItem> = direct_children_map.into_values().collect();
        // Sort directories first, then natural alphanumeric order
        result.sort_by(|a, b| b.is_dir.cmp(&a.is_dir).then_with(|| a.name.cmp(&b.name)));
        result
    }

    pub fn invalidate(archive_path: &Path) {
        if let Ok(mut cache) = SESSION_CACHE.lock() {
            cache.remove(archive_path);
        }
    }
}
