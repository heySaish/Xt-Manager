use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use sevenz_rust::SevenZReader;

use super::atomic::AtomicStagingContext;
use super::session::ArchiveSessionManager;
use super::traits::{
    ArchiveBackend, ArchiveEntryItem, ArchiveError, ArchiveProgress, BackendCapabilities,
    OverwritePolicy,
};

pub struct SevenZipBackend;

impl ArchiveBackend for SevenZipBackend {
    fn capabilities(&self) -> BackendCapabilities {
        BackendCapabilities {
            can_browse: true,
            can_extract: true,
            can_compress: true,
            supports_compression_level: true,
            supports_password: true,
            supports_solid_archive: true,
        }
    }

    fn list_immediate_children(
        &self,
        archive_path: &Path,
        virtual_prefix: &str,
    ) -> Result<Vec<ArchiveEntryItem>, ArchiveError> {
        let all_entries = ArchiveSessionManager::get_or_insert(archive_path, || {
            let mut items = Vec::new();
            if let Ok(reader) = SevenZReader::open(archive_path, sevenz_rust::Password::empty()) {
                for entry in &reader.archive().files {
                    let raw_name = entry.name.clone();
                    let is_dir = entry.is_directory;
                    let name = raw_name
                        .trim_end_matches('/')
                        .split('/')
                        .last()
                        .unwrap_or(&raw_name)
                        .to_string();

                    items.push(ArchiveEntryItem {
                        name,
                        virtual_path: raw_name,
                        is_dir,
                        uncompressed_size: entry.size,
                        last_modified: 0,
                        is_encrypted: entry.has_stream,
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
        let mut staging = AtomicStagingContext::new(destination_dir)?;

        if cancel_flag.load(Ordering::SeqCst) {
            staging.purge();
            return Err(ArchiveError::Cancelled);
        }

        match sevenz_rust::decompress_file(archive_path, &staging.staging_dir) {
            Ok(_) => {}
            Err(e) => {
                staging.purge();
                return Err(ArchiveError::FormatError(e.to_string()));
            }
        }

        if cancel_flag.load(Ordering::SeqCst) {
            staging.purge();
            return Err(ArchiveError::Cancelled);
        }

        for entry in walkdir::WalkDir::new(&staging.staging_dir).into_iter().filter_map(|e| e.ok()) {
            if entry.path() == staging.staging_dir {
                continue;
            }
            let rel = entry.path().strip_prefix(&staging.staging_dir).unwrap_or(entry.path());
            let rel_str = rel.to_string_lossy().to_string();
            let is_dir = entry.path().is_dir();
            let _ = staging.prepare_staging_target(&rel_str, is_dir);
        }

        staging.commit(
            overwrite_policy,
            cancel_flag,
            progress_cb,
            0,
            0,
        )
    }

    fn compress(
        &self,
        sources: &[PathBuf],
        output_archive: &Path,
        _compression_level: i32,
        cancel_flag: &AtomicBool,
        _progress_cb: &dyn Fn(ArchiveProgress),
    ) -> Result<(), ArchiveError> {
        if cancel_flag.load(Ordering::SeqCst) {
            return Err(ArchiveError::Cancelled);
        }

        if sources.len() == 1 && sources[0].is_dir() {
            sevenz_rust::compress_to_path(&sources[0], output_archive)
                .map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        } else {
            for src in sources {
                if cancel_flag.load(Ordering::SeqCst) {
                    let _ = std::fs::remove_file(output_archive);
                    return Err(ArchiveError::Cancelled);
                }
                sevenz_rust::compress_to_path(src, output_archive)
                    .map_err(|e| ArchiveError::FormatError(e.to_string()))?;
            }
        }
        Ok(())
    }
}
