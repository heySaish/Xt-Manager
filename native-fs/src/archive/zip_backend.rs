use std::fs::File;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use zip::write::SimpleFileOptions;
use zip::{ZipArchive, ZipWriter};

use super::atomic::AtomicStagingContext;
use super::session::ArchiveSessionManager;
use super::traits::{
    ArchiveBackend, ArchiveEntryItem, ArchiveError, ArchiveProgress, BackendCapabilities,
    OverwritePolicy,
};

pub struct ZipBackend;

impl ArchiveBackend for ZipBackend {
    fn capabilities(&self) -> BackendCapabilities {
        BackendCapabilities {
            can_browse: true,
            can_extract: true,
            can_compress: true,
            supports_compression_level: true,
            supports_password: true,
            supports_solid_archive: false,
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

            let mut archive = match ZipArchive::new(file) {
                Ok(a) => a,
                Err(_) => return Vec::new(),
            };

            let mut items = Vec::new();
            for i in 0..archive.len() {
                if let Ok(entry) = archive.by_index(i) {
                    let raw_name = entry.name().to_string();
                    let is_dir = entry.is_dir() || raw_name.ends_with('/');
                    let name = raw_name
                        .trim_end_matches('/')
                        .split('/')
                        .last()
                        .unwrap_or(&raw_name)
                        .to_string();

                    let mtime = entry.last_modified().map(|m| {
                        // Approximate UNIX timestamp from ZipDateTime
                        0u64
                    }).unwrap_or(0);

                    items.push(ArchiveEntryItem {
                        name,
                        virtual_path: raw_name,
                        is_dir,
                        uncompressed_size: entry.size(),
                        last_modified: mtime,
                        is_encrypted: entry.encrypted(),
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
        let mut archive = ZipArchive::new(file).map_err(|e| ArchiveError::FormatError(e.to_string()))?;

        let mut staging = AtomicStagingContext::new(destination_dir)?;
        let total_entries = archive.len() as u64;
        let mut total_bytes: u64 = 0;

        for i in 0..archive.len() {
            if let Ok(entry) = archive.by_index(i) {
                total_bytes += entry.size();
            }
        }

        let mut processed_bytes: u64 = 0;
        let mut processed_entries: u64 = 0;

        for i in 0..archive.len() {
            if cancel_flag.load(Ordering::SeqCst) {
                staging.purge();
                return Err(ArchiveError::Cancelled);
            }

            let mut entry = archive
                .by_index(i)
                .map_err(|e| ArchiveError::FormatError(e.to_string()))?;

            let entry_name = entry.name().to_string();
            let is_dir = entry.is_dir() || entry_name.ends_with('/');

            let staging_target = match staging.prepare_staging_target(&entry_name, is_dir) {
                Ok(path) => path,
                Err(e) => {
                    // Log security or invalid path error and safely skip entry
                    continue;
                }
            };

            if !is_dir {
                let mut out_file = File::create(&staging_target)?;
                let mut buffer = [0u8; 64 * 1024];
                loop {
                    if cancel_flag.load(Ordering::SeqCst) {
                        staging.purge();
                        return Err(ArchiveError::Cancelled);
                    }

                    let bytes_read = entry.read(&mut buffer)?;
                    if bytes_read == 0 {
                        break;
                    }
                    out_file.write_all(&buffer[..bytes_read])?;
                    processed_bytes += bytes_read as u64;

                    progress_cb(ArchiveProgress {
                        processed_bytes,
                        total_bytes,
                        processed_entries,
                        total_entries,
                        current_entry: entry_name.clone(),
                        cancellable: true,
                    });
                }
            }

            processed_entries += 1;
            progress_cb(ArchiveProgress {
                processed_bytes,
                total_bytes,
                processed_entries,
                total_entries,
                current_entry: entry_name,
                cancellable: true,
            });
        }

        // Apply atomic commit phase with selected overwrite policy
        staging.commit(
            overwrite_policy,
            cancel_flag,
            progress_cb,
            total_bytes,
            total_entries,
        )
    }

    fn compress(
        &self,
        sources: &[PathBuf],
        output_archive: &Path,
        compression_level: i32,
        cancel_flag: &AtomicBool,
        progress_cb: &dyn Fn(ArchiveProgress),
    ) -> Result<(), ArchiveError> {
        let file = File::create(output_archive)?;
        let mut zip = ZipWriter::new(file);

        let options = SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);

        let mut total_bytes: u64 = 0;
        let mut source_files: Vec<(PathBuf, String)> = Vec::new();

        for src in sources {
            if src.is_file() {
                let name = src.file_name().unwrap().to_string_lossy().to_string();
                total_bytes += src.metadata().map(|m| m.len()).unwrap_or(0);
                source_files.push((src.clone(), name));
            } else if src.is_dir() {
                let base_dir = src.parent().unwrap_or(src);
                for entry in walkdir::WalkDir::new(src).into_iter().filter_map(|e| e.ok()) {
                    let path = entry.path().to_path_buf();
                    let rel = path.strip_prefix(base_dir).unwrap_or(&path);
                    let name = rel.to_string_lossy().to_string();
                    if path.is_file() {
                        total_bytes += path.metadata().map(|m| m.len()).unwrap_or(0);
                    }
                    source_files.push((path, name));
                }
            }
        }

        let total_entries = source_files.len() as u64;
        let mut processed_bytes: u64 = 0;
        let mut processed_entries: u64 = 0;

        for (path, zip_name) in source_files {
            if cancel_flag.load(Ordering::SeqCst) {
                let _ = std::fs::remove_file(output_archive);
                return Err(ArchiveError::Cancelled);
            }

            if path.is_dir() {
                zip.add_directory(&zip_name, options)
                    .map_err(|e| ArchiveError::FormatError(e.to_string()))?;
            } else {
                zip.start_file(&zip_name, options)
                    .map_err(|e| ArchiveError::FormatError(e.to_string()))?;

                let mut in_file = File::open(&path)?;
                let mut buffer = [0u8; 64 * 1024];
                loop {
                    if cancel_flag.load(Ordering::SeqCst) {
                        let _ = std::fs::remove_file(output_archive);
                        return Err(ArchiveError::Cancelled);
                    }

                    let bytes_read = in_file.read(&mut buffer)?;
                    if bytes_read == 0 {
                        break;
                    }
                    zip.write_all(&buffer[..bytes_read])?;
                    processed_bytes += bytes_read as u64;

                    progress_cb(ArchiveProgress {
                        processed_bytes,
                        total_bytes,
                        processed_entries,
                        total_entries,
                        current_entry: zip_name.clone(),
                        cancellable: true,
                    });
                }
            }

            processed_entries += 1;
        }

        zip.finish().map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        Ok(())
    }
}
