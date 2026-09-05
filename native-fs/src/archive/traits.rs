use std::path::{Path, PathBuf};
use std::sync::atomic::AtomicBool;

#[derive(Debug, Clone, Copy)]
pub struct BackendCapabilities {
    pub can_browse: bool,
    pub can_extract: bool,
    pub can_compress: bool,
    pub supports_compression_level: bool,
    pub supports_password: bool,
    pub supports_solid_archive: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OverwritePolicy {
    Overwrite = 0,
    Skip = 1,
    RenameNew = 2,
}

impl OverwritePolicy {
    pub fn from_i32(val: i32) -> Self {
        match val {
            1 => OverwritePolicy::Skip,
            2 => OverwritePolicy::RenameNew,
            _ => OverwritePolicy::Overwrite,
        }
    }
}

#[derive(Debug, Clone)]
pub struct ArchiveEntryItem {
    pub name: String,
    pub virtual_path: String,
    pub is_dir: Boolean,
    pub uncompressed_size: u64,
    pub last_modified: u64,
    pub is_encrypted: Boolean,
}

// Rename boolean to bool for Rust
pub type Boolean = bool;

#[derive(Debug, Clone)]
pub struct ArchiveProgress {
    pub processed_bytes: u64,
    pub total_bytes: u64,
    pub processed_entries: u64,
    pub total_entries: u64,
    pub current_entry: String,
    pub cancellable: bool,
}

#[derive(Debug)]
pub enum ArchiveError {
    Io(std::io::Error),
    FormatError(String),
    SecurityError(String),
    Cancelled,
    UnsupportedFormat,
}

impl From<std::io::Error> for ArchiveError {
    fn from(err: std::io::Error) -> Self {
        ArchiveError::Io(err)
    }
}

pub trait ArchiveBackend: Send + Sync {
    fn capabilities(&self) -> BackendCapabilities;

    fn list_immediate_children(
        &self,
        archive_path: &Path,
        virtual_prefix: &str,
    ) -> Result<Vec<ArchiveEntryItem>, ArchiveError>;

    fn extract(
        &self,
        archive_path: &Path,
        destination_dir: &Path,
        overwrite_policy: OverwritePolicy,
        cancel_flag: &AtomicBool,
        progress_cb: &dyn Fn(ArchiveProgress),
    ) -> Result<(), ArchiveError>;

    fn compress(
        &self,
        sources: &[PathBuf],
        output_archive: &Path,
        compression_level: i32,
        cancel_flag: &AtomicBool,
        progress_cb: &dyn Fn(ArchiveProgress),
    ) -> Result<(), ArchiveError>;
}
