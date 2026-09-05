use std::fs::File;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use flate2::read::GzDecoder;
use flate2::write::GzEncoder;
use flate2::Compression;
use tar::{Archive, Builder};

use super::atomic::AtomicStagingContext;
use super::session::ArchiveSessionManager;
use super::traits::{
    ArchiveBackend, ArchiveEntryItem, ArchiveError, ArchiveProgress, BackendCapabilities,
    OverwritePolicy,
};

pub struct TarBackend;

impl ArchiveBackend for TarBackend {
    fn capabilities(&self) -> BackendCapabilities {
        BackendCapabilities {
            can_browse: true,
            can_extract: true,
            can_compress: true,
            supports_compression_level: true,
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

            let name_str = archive_path.to_string_lossy().to_lowercase();
            let mut items = Vec::new();

            if name_str.ends_with(".gz") || name_str.ends_with(".tgz") {
                let gz = GzDecoder::new(file);
                let mut archive = Archive::new(gz);
                if let Ok(entries) = archive.entries() {
                    for entry in entries.flatten() {
                        if let Ok(path) = entry.path() {
                            let raw_name = path.to_string_lossy().to_string();
                            let is_dir = entry.header().entry_type().is_dir() || raw_name.ends_with('/');
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
                                uncompressed_size: entry.header().size().unwrap_or(0),
                                last_modified: entry.header().mtime().unwrap_or(0),
                                is_encrypted: false,
                            });
                        }
                    }
                }
            } else {
                let mut archive = Archive::new(file);
                if let Ok(entries) = archive.entries() {
                    for entry in entries.flatten() {
                        if let Ok(path) = entry.path() {
                            let raw_name = path.to_string_lossy().to_string();
                            let is_dir = entry.header().entry_type().is_dir() || raw_name.ends_with('/');
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
                                uncompressed_size: entry.header().size().unwrap_or(0),
                                last_modified: entry.header().mtime().unwrap_or(0),
                                is_encrypted: false,
                            });
                        }
                    }
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
        let name_str = archive_path.to_string_lossy().to_lowercase();
        let mut staging = AtomicStagingContext::new(destination_dir)?;

        let is_gz = name_str.ends_with(".gz") || name_str.ends_with(".tgz");

        let mut processed_bytes: u64 = 0;
        let mut processed_entries: u64 = 0;

        if is_gz {
            let gz = GzDecoder::new(file);
            let mut archive = Archive::new(gz);

            for entry_res in archive.entries().map_err(|e| ArchiveError::FormatError(e.to_string()))? {
                if cancel_flag.load(Ordering::SeqCst) {
                    staging.purge();
                    return Err(ArchiveError::Cancelled);
                }

                let mut entry = entry_res.map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                let entry_path = entry.path().map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                let entry_name = entry_path.to_string_lossy().to_string();
                let is_dir = entry.header().entry_type().is_dir() || entry_name.ends_with('/');

                let staging_target = match staging.prepare_staging_target(&entry_name, is_dir) {
                    Ok(p) => p,
                    Err(_) => continue,
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
                            total_bytes: 0,
                            processed_entries,
                            total_entries: 0,
                            current_entry: entry_name.clone(),
                            cancellable: true,
                        });
                    }
                }

                processed_entries += 1;
            }
        } else {
            let mut archive = Archive::new(file);

            for entry_res in archive.entries().map_err(|e| ArchiveError::FormatError(e.to_string()))? {
                if cancel_flag.load(Ordering::SeqCst) {
                    staging.purge();
                    return Err(ArchiveError::Cancelled);
                }

                let mut entry = entry_res.map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                let entry_path = entry.path().map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                let entry_name = entry_path.to_string_lossy().to_string();
                let is_dir = entry.header().entry_type().is_dir() || entry_name.ends_with('/');

                let staging_target = match staging.prepare_staging_target(&entry_name, is_dir) {
                    Ok(p) => p,
                    Err(_) => continue,
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
                            total_bytes: 0,
                            processed_entries,
                            total_entries: 0,
                            current_entry: entry_name.clone(),
                            cancellable: true,
                        });
                    }
                }

                processed_entries += 1;
            }
        }

        staging.commit(
            overwrite_policy,
            cancel_flag,
            progress_cb,
            processed_bytes,
            processed_entries,
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
        let file = File::create(output_archive)?;
        let name_str = output_archive.to_string_lossy().to_lowercase();
        let is_gz = name_str.ends_with(".gz") || name_str.ends_with(".tgz");

        if is_gz {
            let enc = GzEncoder::new(file, Compression::default());
            let mut tar = Builder::new(enc);
            for src in sources {
                if cancel_flag.load(Ordering::SeqCst) {
                    let _ = std::fs::remove_file(output_archive);
                    return Err(ArchiveError::Cancelled);
                }
                if src.is_file() {
                    tar.append_path(src).map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                } else if src.is_dir() {
                    tar.append_dir_all(src.file_name().unwrap_or(src.as_os_str()), src)
                        .map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                }
            }
            tar.finish().map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        } else {
            let mut tar = Builder::new(file);
            for src in sources {
                if cancel_flag.load(Ordering::SeqCst) {
                    let _ = std::fs::remove_file(output_archive);
                    return Err(ArchiveError::Cancelled);
                }
                if src.is_file() {
                    tar.append_path(src).map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                } else if src.is_dir() {
                    tar.append_dir_all(src.file_name().unwrap_or(src.as_os_str()), src)
                        .map_err(|e| ArchiveError::FormatError(e.to_string()))?;
                }
            }
            tar.finish().map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        }

        Ok(())
    }
}
