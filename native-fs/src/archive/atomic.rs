use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use tempfile::Builder;

use super::security::PathValidator;
use super::traits::{ArchiveError, ArchiveProgress, OverwritePolicy};

pub struct StagingCommitAction {
    pub staging_path: PathBuf,
    pub final_target_path: PathBuf,
    pub is_dir: bool,
}

pub struct AtomicStagingContext {
    pub destination_dir: PathBuf,
    pub staging_dir: PathBuf,
    pub commit_actions: Vec<StagingCommitAction>,
}

impl AtomicStagingContext {
    /// Creates staging directory `.xt-tmp-*` inside destination_dir to guarantee same-filesystem mount point operations.
    pub fn new(destination_dir: &Path) -> Result<Self, ArchiveError> {
        if !destination_dir.exists() {
            fs::create_dir_all(destination_dir)?;
        }

        let staging_dir = Builder::new()
            .prefix(".xt-tmp-")
            .tempdir_in(destination_dir)
            .map_err(|e| ArchiveError::Io(e))?
            .into_path();

        Ok(Self {
            destination_dir: destination_dir.to_path_buf(),
            staging_dir,
            commit_actions: Vec::new(),
        })
    }

    /// Prepare safe staging file target path inside staging_dir for writing entry
    pub fn prepare_staging_target(
        &mut self,
        raw_entry_path: &str,
        is_dir: bool,
    ) -> Result<PathBuf, ArchiveError> {
        let final_target = PathValidator::validate_and_resolve_entry_path(
            &self.destination_dir,
            raw_entry_path,
        )?;

        // Relative path inside destination root
        let relative = final_target
            .strip_prefix(&self.destination_dir)
            .map_err(|_| {
                ArchiveError::SecurityError("Failed to calculate relative path".to_string())
            })?;

        let staging_target = self.staging_dir.join(relative);

        if is_dir {
            fs::create_dir_all(&staging_target)?;
        } else {
            if let Some(parent) = staging_target.parent() {
                if !parent.exists() {
                    fs::create_dir_all(parent)?;
                }
            }
        }

        self.commit_actions.push(StagingCommitAction {
            staging_path: staging_target.clone(),
            final_target_path: final_target,
            is_dir,
        });

        Ok(staging_target)
    }

    /// Commits all extracted files from staging_dir to final destination based on OverwritePolicy.
    /// Runs ONLY after full archive extraction completes without error or cancellation.
    pub fn commit(
        self,
        overwrite_policy: OverwritePolicy,
        cancel_flag: &AtomicBool,
        progress_cb: &dyn Fn(ArchiveProgress),
        total_bytes: u64,
        total_entries: u64,
    ) -> Result<(), ArchiveError> {
        let mut processed_entries: u64 = 0;

        for action in self.commit_actions {
            if cancel_flag.load(Ordering::SeqCst) {
                self.purge();
                return Err(ArchiveError::Cancelled);
            }

            if action.is_dir {
                if !action.final_target_path.exists() {
                    fs::create_dir_all(&action.final_target_path)?;
                }
            } else {
                if let Some(parent) = action.final_target_path.parent() {
                    if !parent.exists() {
                        fs::create_dir_all(parent)?;
                    }
                }

                let target = if action.final_target_path.exists() {
                    match overwrite_policy {
                        OverwritePolicy::Overwrite => action.final_target_path.clone(),
                        OverwritePolicy::Skip => {
                            // Discard staging file, leave existing file intact
                            let _ = fs::remove_file(&action.staging_path);
                            processed_entries += 1;
                            progress_cb(ArchiveProgress {
                                processed_bytes: total_bytes,
                                total_bytes,
                                processed_entries,
                                total_entries,
                                current_entry: action.final_target_path.display().to_string(),
                                cancellable: true,
                            });
                            continue;
                        }
                        OverwritePolicy::RenameNew => generate_unique_path(&action.final_target_path),
                    }
                } else {
                    action.final_target_path.clone()
                };

                // Move/rename file from staging to destination
                if let Err(_) = fs::rename(&action.staging_path, &target) {
                    // Fallback to copy and remove if cross-device move error occurs
                    fs::copy(&action.staging_path, &target)?;
                    let _ = fs::remove_file(&action.staging_path);
                }
            }

            processed_entries += 1;
            progress_cb(ArchiveProgress {
                processed_bytes: total_bytes,
                total_bytes,
                processed_entries,
                total_entries,
                current_entry: action.final_target_path.display().to_string(),
                cancellable: true,
            });
        }

        // Clean up empty staging dir
        self.purge();
        Ok(())
    }

    /// Recursively purge staging directory
    pub fn purge(&self) {
        if self.staging_dir.exists() {
            let _ = fs::remove_dir_all(&self.staging_dir);
        }
    }
}

fn generate_unique_path(base_path: &Path) -> PathBuf {
    let parent = base_path.parent().unwrap_or_else(|| Path::new(""));
    let file_stem = base_path
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default();
    let ext = base_path
        .extension()
        .map(|e| format!(".{}", e.to_string_lossy()))
        .unwrap_or_default();

    let mut counter = 1;
    loop {
        let new_name = format!("{} ({}){}", file_stem, counter, ext);
        let candidate = parent.join(new_name);
        if !candidate.exists() {
            return candidate;
        }
        counter += 1;
    }
}
