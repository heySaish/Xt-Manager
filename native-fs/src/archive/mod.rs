pub mod atomic;
pub mod cancel;
pub mod security;
pub mod session;
pub mod sevenz_backend;
pub mod tar_backend;
pub mod traits;
pub mod zip_backend;

#[cfg(test)]
pub mod tests;

use std::path::Path;
use traits::{ArchiveBackend, ArchiveError};
use sevenz_backend::SevenZipBackend;
use tar_backend::TarBackend;
use zip_backend::ZipBackend;

pub fn get_backend_for_path(path: &Path) -> Result<Box<dyn ArchiveBackend>, ArchiveError> {
    let filename = path
        .file_name()
        .map(|s| s.to_string_lossy().to_lowercase())
        .unwrap_or_default();

    if filename.ends_with(".zip") || filename.ends_with(".apk") {
        Ok(Box::new(ZipBackend))
    } else if filename.ends_with(".tar")
        || filename.ends_with(".tar.gz")
        || filename.ends_with(".tgz")
    {
        Ok(Box::new(TarBackend))
    } else if filename.ends_with(".7z") {
        Ok(Box::new(SevenZipBackend))
    } else {
        Err(ArchiveError::UnsupportedFormat)
    }
}

pub fn get_backend_for_format(format: &str) -> Result<Box<dyn ArchiveBackend>, ArchiveError> {
    match format.to_lowercase().as_str() {
        "zip" | "apk" => Ok(Box::new(ZipBackend)),
        "tar" | "tar.gz" | "tgz" => Ok(Box::new(TarBackend)),
        "7z" => Ok(Box::new(SevenZipBackend)),
        _ => Err(ArchiveError::UnsupportedFormat),
    }
}
