pub mod atomic;
pub mod cab_backend;
pub mod cancel;
pub mod cpio_backend;
pub mod iso_backend;
pub mod security;
pub mod session;
pub mod sevenz_backend;
pub mod tar_backend;
pub mod traits;
pub mod zip_backend;

#[cfg(test)]
pub mod tests;

use std::path::Path;
pub use atomic::AtomicStagingContext;
pub use cab_backend::CabBackend;
pub use cancel::CancelManager;
pub use cpio_backend::CpioBackend;
pub use iso_backend::IsoBackend;
pub use security::PathValidator;
pub use session::ArchiveSessionManager;
pub use sevenz_backend::SevenZipBackend;
pub use tar_backend::TarBackend;
pub use traits::{
    ArchiveBackend, ArchiveEntryItem, ArchiveError, ArchiveProgress, BackendCapabilities,
    OverwritePolicy,
};
pub use zip_backend::ZipBackend;

pub fn get_backend_for_path(path: &Path) -> Result<Box<dyn ArchiveBackend>, ArchiveError> {
    let filename = path
        .file_name()
        .map(|s| s.to_string_lossy().to_lowercase())
        .unwrap_or_default();

    if filename.ends_with(".zip") || filename.ends_with(".apk") {
        Ok(Box::new(ZipBackend))
    } else if filename.ends_with(".7z") {
        Ok(Box::new(SevenZipBackend))
    } else if filename.ends_with(".cab") {
        Ok(Box::new(CabBackend))
    } else if filename.ends_with(".iso") {
        Ok(Box::new(IsoBackend))
    } else if filename.ends_with(".cpio") {
        Ok(Box::new(CpioBackend))
    } else if filename.ends_with(".tar")
        || filename.ends_with(".tar.gz")
        || filename.ends_with(".tgz")
        || filename.ends_with(".gz")
        || filename.ends_with(".tar.bz2")
        || filename.ends_with(".tbz2")
        || filename.ends_with(".tbz")
        || filename.ends_with(".bz2")
        || filename.ends_with(".tar.xz")
        || filename.ends_with(".txz")
        || filename.ends_with(".xz")
        || filename.ends_with(".tar.zst")
        || filename.ends_with(".tzst")
        || filename.ends_with(".zst")
        || filename.ends_with(".tar.lz4")
        || filename.ends_with(".tlz4")
        || filename.ends_with(".lz4")
    {
        Ok(Box::new(TarBackend))
    } else {
        Err(ArchiveError::UnsupportedFormat)
    }
}

pub fn get_backend_for_format(format: &str) -> Result<Box<dyn ArchiveBackend>, ArchiveError> {
    match format.to_lowercase().as_str() {
        "zip" | "apk" => Ok(Box::new(ZipBackend)),
        "7z" => Ok(Box::new(SevenZipBackend)),
        "cab" => Ok(Box::new(CabBackend)),
        "iso" => Ok(Box::new(IsoBackend)),
        "cpio" => Ok(Box::new(CpioBackend)),
        "tar" | "tar.gz" | "tgz" | "gz" | "tar.bz2" | "tbz2" | "bz2" | "tar.xz" | "txz" | "xz"
        | "tar.zst" | "tzst" | "zst" | "tar.lz4" | "tlz4" | "lz4" => Ok(Box::new(TarBackend)),
        _ => Err(ArchiveError::UnsupportedFormat),
    }
}
