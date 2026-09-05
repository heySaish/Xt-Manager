use std::fs::File;
use std::io::{Read, Write};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};

use super::atomic::AtomicStagingContext;
use super::session::ArchiveSessionManager;
use super::traits::{
    ArchiveBackend, ArchiveEntryItem, ArchiveError, ArchiveProgress, BackendCapabilities,
    OverwritePolicy,
};

pub struct CpioBackend;

impl ArchiveBackend for CpioBackend {
    fn capabilities(&self) -> BackendCapabilities {
        BackendCapabilities {
            can_browse: true,
            can_extract: true,
            can_compress: false,
            supports_compression_level: false,
            supports_password: false,
            supports_solid_archive: true,
        }
    }

    fn list_immediate_children(
        &self,
        archive_path: &Path,
        virtual_prefix: &str,
    ) -> Result<Vec<ArchiveEntryItem>, ArchiveError> {
        let all_entries = ArchiveSessionManager::get_or_insert(archive_path, || {
            let file = match File::open(archive_path) {
                Ok(f) => f,
                Err(_) => return Vec::new(),
            };
            let mut reader = match cpio::NewcReader::new(file) {
                Ok(r) => r,
                Err(_) => return Vec::new(),
            };
            let mut items = Vec::new();

            while let Ok(Some(entry)) = reader.next() {
                let name = entry.name().to_string();
                if name == "TRAILER!!!" {
                    break;
                }
                let is_dir = entry.mode() & 0o040000 != 0;
                let clean_name = name.trim_end_matches('/').to_string();
                let short_name = clean_name.split('/').last().unwrap_or(&clean_name).to_string();

                items.push(ArchiveEntryItem {
                    name: short_name,
                    virtual_path: clean_name,
                    is_dir,
                    uncompressed_size: entry.file_size() as u64,
                    last_modified: entry.mtime() as u64,
                    is_encrypted: false,
                });
                reader = match entry.finish() {
                    Ok(r) => r,
                    Err(_) => break,
                };
            }
            items
        });

        Ok(ArchiveSessionManager::filter_immediate_children(
            &all_entries,
            virtual_prefix,
        ))
    }

    fn extract(
        &self,
        archive_path: &Path,
        destination_dir: &Path,
        overwrite_policy: OverwritePolicy,
        cancel_flag: &AtomicBool,
        progress_cb: &dyn Fn(ArchiveProgress),
    ) -> Result<(), ArchiveError> {
        let file = File::open(archive_path)?;
        let mut reader = cpio::NewcReader::new(file).map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        let mut staging = AtomicStagingContext::new(destination_dir)?;

        let mut processed_bytes = 0u64;
        let mut processed_entries = 0u64;

        while let Ok(Some(mut entry)) = reader.next() {
            if cancel_flag.load(Ordering::SeqCst) {
                staging.purge();
                return Err(ArchiveError::Cancelled);
            }

            let name = entry.name().to_string();
            if name == "TRAILER!!!" {
                break;
            }

            let is_dir = entry.mode() & 0o040000 != 0;
            let target_path = match staging.prepare_staging_target(&name, is_dir) {
                Ok(p) => p,
                Err(_) => {
                    reader = match entry.finish() {
                        Ok(r) => r,
                        Err(_) => break,
                    };
                    continue;
                }
            };

            if !is_dir {
                let mut out_file = File::create(&target_path)?;
                let mut buf = [0u8; 64 * 1024];
                loop {
                    if cancel_flag.load(Ordering::SeqCst) {
                        staging.purge();
                        return Err(ArchiveError::Cancelled);
                    }
                    let bytes = entry.read(&mut buf)?;
                    if bytes == 0 {
                        break;
                    }
                    out_file.write_all(&buf[..bytes])?;
                    processed_bytes += bytes as u64;
                    progress_cb(ArchiveProgress {
                        processed_bytes,
                        total_bytes: 0,
                        processed_entries,
                        total_entries: 0,
                        current_entry: name.clone(),
                        cancellable: true,
                    });
                }
            }

            processed_entries += 1;
            reader = match entry.finish() {
                Ok(r) => r,
                Err(_) => break,
            };
        }

        staging.commit(
            overwrite_policy,
            cancel_flag,
            progress_cb,
            processed_bytes,
            processed_entries,
        )?;
        Ok(())
    }

    fn compress(
        &self,
        _sources: &[std::path::PathBuf],
        _output_archive: &Path,
        _compression_level: i32,
        _cancel_flag: &AtomicBool,
        _progress_cb: &dyn Fn(ArchiveProgress),
    ) -> Result<(), ArchiveError> {
        Err(ArchiveError::FormatError("CPIO compression not supported".to_string()))
    }
}
