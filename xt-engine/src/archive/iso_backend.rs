use std::fs::File;
use std::io::{Read, Write};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use iso9660::{DirectoryEntry, ISO9660};

use super::atomic::AtomicStagingContext;
use super::session::ArchiveSessionManager;
use super::traits::{
    ArchiveBackend, ArchiveEntryItem, ArchiveError, ArchiveProgress, BackendCapabilities,
    OverwritePolicy,
};

pub struct IsoBackend;

impl ArchiveBackend for IsoBackend {
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
            let fs = match ISO9660::new(file) {
                Ok(fs) => fs,
                Err(_) => return Vec::new(),
            };

            let mut items = Vec::new();
            let mut stack = vec![(String::new(), fs.root)];

            while let Some((parent_path, dir)) = stack.pop() {
                if let Ok(entries) = dir.read() {
                    for entry_res in entries {
                        if let Ok(entry) = entry_res {
                            match entry {
                                DirectoryEntry::Directory(d) => {
                                    let name = d.identifier.clone();
                                    if name != "." && name != ".." {
                                        let vpath = if parent_path.is_empty() {
                                            name.clone()
                                        } else {
                                            format!("{}/{}", parent_path, name)
                                        };
                                        items.push(ArchiveEntryItem {
                                            name,
                                            virtual_path: vpath.clone(),
                                            is_dir: true,
                                            uncompressed_size: 0,
                                            last_modified: 0,
                                            is_encrypted: false,
                                        });
                                        stack.push((vpath, d));
                                    }
                                }
                                DirectoryEntry::File(f) => {
                                    let name = f.identifier.clone();
                                    let vpath = if parent_path.is_empty() {
                                        name.clone()
                                    } else {
                                        format!("{}/{}", parent_path, name)
                                    };
                                    items.push(ArchiveEntryItem {
                                        name,
                                        virtual_path: vpath,
                                        is_dir: false,
                                        uncompressed_size: f.size as u64,
                                        last_modified: 0,
                                        is_encrypted: false,
                                    });
                                }
                            }
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
        let fs = ISO9660::new(file).map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        let mut staging = AtomicStagingContext::new(destination_dir)?;

        let mut processed_bytes = 0u64;
        let mut processed_entries = 0u64;

        let mut stack = vec![(String::new(), fs.root)];

        while let Some((parent_path, dir)) = stack.pop() {
            if cancel_flag.load(Ordering::SeqCst) {
                staging.purge();
                return Err(ArchiveError::Cancelled);
            }

            if let Ok(entries) = dir.read() {
                for entry_res in entries {
                    if let Ok(entry) = entry_res {
                        match entry {
                            DirectoryEntry::Directory(d) => {
                                let name = d.identifier.clone();
                                if name != "." && name != ".." {
                                    let vpath = if parent_path.is_empty() {
                                        name
                                    } else {
                                        format!("{}/{}", parent_path, name)
                                    };
                                    let _ = staging.prepare_staging_target(&vpath, true);
                                    stack.push((vpath, d));
                                }
                            }
                            DirectoryEntry::File(f) => {
                                let name = f.identifier.clone();
                                let vpath = if parent_path.is_empty() {
                                    name
                                } else {
                                    format!("{}/{}", parent_path, name)
                                };
                                let target_path = match staging.prepare_staging_target(&vpath, false) {
                                    Ok(p) => p,
                                    Err(_) => continue,
                                };

                                if let Ok(mut file_reader) = f.read() {
                                    let mut out_file = File::create(&target_path)?;
                                    let mut buf = [0u8; 64 * 1024];
                                    loop {
                                        if cancel_flag.load(Ordering::SeqCst) {
                                            staging.purge();
                                            return Err(ArchiveError::Cancelled);
                                        }
                                        let bytes = file_reader.read(&mut buf)?;
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
                                            current_entry: vpath.clone(),
                                            cancellable: true,
                                        });
                                    }
                                }
                                processed_entries += 1;
                            }
                        }
                    }
                }
            }
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
        Err(ArchiveError::FormatError("ISO creation not supported".to_string()))
    }
}
