use std::fs::File;
use std::io::{Read, Write};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use cab::Cabinet;

use super::atomic::AtomicStagingContext;
use super::session::ArchiveSessionManager;
use super::traits::{
    ArchiveBackend, ArchiveEntryItem, ArchiveError, ArchiveProgress, BackendCapabilities,
    OverwritePolicy,
};

pub struct CabBackend;

impl ArchiveBackend for CabBackend {
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
            let mut cabinet = match Cabinet::new(file) {
                Ok(c) => c,
                Err(_) => return Vec::new(),
            };

            let mut items = Vec::new();
            for folder in cabinet.folder_entries() {
                for file in folder.file_entries() {
                    let name = file.name().to_string();
                    let clean_name = name.replace('\\', "/");
                    items.push(ArchiveEntryItem {
                        name: clean_name.split('/').last().unwrap_or(&clean_name).to_string(),
                        virtual_path: clean_name,
                        is_dir: false,
                        uncompressed_size: file.uncompressed_size() as u64,
                        last_modified: 0,
                        is_encrypted: false,
                    });
                }
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
        let mut cabinet = Cabinet::new(file).map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        let mut staging = AtomicStagingContext::new(destination_dir)?;

        let mut processed_bytes = 0u64;
        let mut processed_entries = 0u64;

        let file_names: Vec<String> = cabinet
            .folder_entries()
            .flat_map(|f| f.file_entries().map(|entry| entry.name().to_string()))
            .collect();

        for name in file_names {
            if cancel_flag.load(Ordering::SeqCst) {
                staging.purge();
                return Err(ArchiveError::Cancelled);
            }

            let clean_name = name.replace('\\', "/");
            let target_path = match staging.prepare_staging_target(&clean_name, false) {
                Ok(p) => p,
                Err(_) => continue,
            };

            let mut reader = cabinet.read_file(&name).map_err(|e| ArchiveError::FormatError(e.to_string()))?;
            let mut out_file = File::create(&target_path)?;
            let bytes_copied = std::io::copy(&mut reader, &mut out_file)?;
            processed_bytes += bytes_copied;
            processed_entries += 1;

            progress_cb(ArchiveProgress {
                processed_bytes,
                total_bytes: 0,
                processed_entries,
                total_entries: 0,
                current_entry: clean_name,
                cancellable: true,
            });
        }

        staging.commit(overwrite_policy)?;
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
        Err(ArchiveError::FormatError("CAB creation not supported".to_string()))
    }
}
