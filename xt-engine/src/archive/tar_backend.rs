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

fn create_tar_reader(file: File, path: &Path) -> Result<Box<dyn Read>, ArchiveError> {
    let name_str = path.to_string_lossy().to_lowercase();
    if name_str.ends_with(".gz") || name_str.ends_with(".tgz") {
        Ok(Box::new(GzDecoder::new(file)))
    } else if name_str.ends_with(".bz2") || name_str.ends_with(".tbz2") || name_str.ends_with(".tbz") {
        Ok(Box::new(bzip2::read::BzDecoder::new(file)))
    } else if name_str.ends_with(".zst") || name_str.ends_with(".tzst") {
        let dec = zstd::Decoder::new(file).map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        Ok(Box::new(dec))
    } else if name_str.ends_with(".lz4") || name_str.ends_with(".tlz4") {
        let dec = lz4_flex::frame::FrameDecoder::new(file);
        Ok(Box::new(dec))
    } else if name_str.ends_with(".xz") || name_str.ends_with(".txz") {
        let mut decompressed = Vec::new();
        let mut buf_file = std::io::BufReader::new(file);
        lzma_rs::xz_decompress(&mut buf_file, &mut decompressed)
            .map_err(|e| ArchiveError::FormatError(e.to_string()))?;
        Ok(Box::new(std::io::Cursor::new(decompressed)))
    } else {
        Ok(Box::new(file))
    }
}

fn create_tar_writer(output_archive: &Path, level: i32) -> Result<Box<dyn Write>, ArchiveError> {
    let file = File::create(output_archive)?;
    let name_str = output_archive.to_string_lossy().to_lowercase();

    if name_str.ends_with(".gz") || name_str.ends_with(".tgz") {
        let comp_level = Compression::new(level.clamp(0, 9) as u32);
        Ok(Box::new(GzEncoder::new(file, comp_level)))
    } else if name_str.ends_with(".bz2") || name_str.ends_with(".tbz2") || name_str.ends_with(".tbz") {
        let comp_level = bzip2::Compression::new(level.clamp(0, 9) as u32);
        Ok(Box::new(bzip2::write::BzEncoder::new(file, comp_level)))
    } else if name_str.ends_with(".zst") || name_str.ends_with(".tzst") {
        let enc = zstd::Encoder::new(file, level.clamp(1, 22))
            .map_err(|e| ArchiveError::FormatError(e.to_string()))?
            .auto_finish();
        Ok(Box::new(enc))
    } else if name_str.ends_with(".lz4") || name_str.ends_with(".tlz4") {
        Ok(Box::new(lz4_flex::frame::FrameEncoder::new(file)))
    } else {
        Ok(Box::new(file))
    }
}

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

            let reader = match create_tar_reader(file, archive_path) {
                Ok(r) => r,
                Err(_) => return Vec::new(),
            };

            let mut archive = Archive::new(reader);
            let mut items = Vec::new();

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
        let reader = create_tar_reader(file, archive_path)?;
        let mut archive = Archive::new(reader);
        let mut staging = AtomicStagingContext::new(destination_dir)?;

        let mut processed_bytes: u64 = 0;
        let mut processed_entries: u64 = 0;

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
        sources: &[PathBuf],
        output_archive: &Path,
        compression_level: i32,
        cancel_flag: &AtomicBool,
        progress_cb: &dyn Fn(ArchiveProgress),
    ) -> Result<(), ArchiveError> {
        let writer = create_tar_writer(output_archive, compression_level)?;
        let mut builder = Builder::new(writer);

        let mut processed_bytes: u64 = 0;
        let mut processed_entries: u64 = 0;

        for src in sources {
            if cancel_flag.load(Ordering::SeqCst) {
                let _ = std::fs::remove_file(output_archive);
                return Err(ArchiveError::Cancelled);
            }

            let base_name = src.file_name().unwrap_or_default().to_string_lossy();

            if src.is_dir() {
                for entry_res in walkdir::WalkDir::new(src) {
                    if cancel_flag.load(Ordering::SeqCst) {
                        let _ = std::fs::remove_file(output_archive);
                        return Err(ArchiveError::Cancelled);
                    }

                    let entry = entry_res.map_err(|e| ArchiveError::Io(std::io::Error::new(std::io::ErrorKind::Other, e)))?;
                    let path = entry.path();
                    let rel_path = path.strip_prefix(src.parent().unwrap_or(src)).unwrap_or(path);

                    if path.is_file() {
                        builder.append_path_with_name(path, rel_path)?;
                        let len = std::fs::metadata(path).map(|m| m.len()).unwrap_or(0);
                        processed_bytes += len;
                        processed_entries += 1;

                        progress_cb(ArchiveProgress {
                            processed_bytes,
                            total_bytes: 0,
                            processed_entries,
                            total_entries: 0,
                            current_entry: rel_path.to_string_lossy().to_string(),
                            cancellable: true,
                        });
                    } else if path.is_dir() {
                        builder.append_dir(rel_path, path)?;
                    }
                }
            } else if src.is_file() {
                builder.append_path_with_name(src, Path::new(base_name.as_ref()))?;
                let len = std::fs::metadata(src).map(|m| m.len()).unwrap_or(0);
                processed_bytes += len;
                processed_entries += 1;

                progress_cb(ArchiveProgress {
                    processed_bytes,
                    total_bytes: 0,
                    processed_entries,
                    total_entries: 0,
                    current_entry: base_name.to_string(),
                    cancellable: true,
                });
            }
        }

        builder.finish()?;
        Ok(())
    }
}
