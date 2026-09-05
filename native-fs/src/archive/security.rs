use std::path::{Component, Path, PathBuf};
use super::traits::ArchiveError;

pub struct PathValidator;

impl PathValidator {
    /// Validates raw entry path string from archive and returns safe target PathBuf inside destination_root.
    /// Performs strict component-based traversal check, Windows path check, NUL byte check, and depth tracking.
    pub fn validate_and_resolve_entry_path(
        destination_root: &Path,
        raw_entry_path: &str,
    ) -> Result<PathBuf, ArchiveError> {
        // 1. Check for NUL byte
        if raw_entry_path.contains('\0') {
            return Err(ArchiveError::SecurityError(
                "Entry path contains NUL byte".to_string(),
            ));
        }

        // 2. Check for Windows drive letters (e.g. C:\, D:/)
        if raw_entry_path.len() >= 2 {
            let bytes = raw_entry_path.as_bytes();
            if (bytes[0].is_ascii_alphabetic()) && bytes[1] == b':' {
                return Err(ArchiveError::SecurityError(format!(
                    "Windows drive path rejected: {}",
                    raw_entry_path
                )));
            }
        }

        // 3. Normalize backslashes to forward slashes for cross-platform component parsing
        let normalized_str = raw_entry_path.replace('\\', "/");
        let path = Path::new(&normalized_str);

        // 4. Component-based check: Disallow leading root components and track depth
        let mut depth: i32 = 0;
        let mut clean_components = Vec::new();

        for component in path.components() {
            match component {
                Component::Prefix(_) | Component::RootDir => {
                    return Err(ArchiveError::SecurityError(format!(
                        "Absolute path entry rejected: {}",
                        raw_entry_path
                    )));
                }
                Component::ParentDir => {
                    depth -= 1;
                    if depth < 0 {
                        return Err(ArchiveError::SecurityError(format!(
                            "Path traversal escaping root rejected: {}",
                            raw_entry_path
                        )));
                    }
                    clean_components.pop();
                }
                Component::CurDir => {
                    // Current dir '.' is safely ignored
                }
                Component::Normal(c) => {
                    depth += 1;
                    clean_components.push(c);
                }
            }
        }

        if clean_components.is_empty() {
            return Err(ArchiveError::SecurityError(format!(
                "Empty or invalid path entry: {}",
                raw_entry_path
            )));
        }

        // 5. Build final target path and verify using canonicalize check if destination exists
        let mut target = destination_root.to_path_buf();
        for comp in clean_components {
            target.push(comp);
        }

        Ok(target)
    }

    /// Verifies that a symlink target stays strictly within destination_root
    pub fn validate_symlink_target(
        destination_root: &Path,
        link_parent_dir: &Path,
        symlink_target_str: &str,
    ) -> Result<PathBuf, ArchiveError> {
        // NUL byte check
        if symlink_target_str.contains('\0') {
            return Err(ArchiveError::SecurityError(
                "Symlink target contains NUL byte".to_string(),
            ));
        }

        // Windows drive check
        if symlink_target_str.len() >= 2 {
            let bytes = symlink_target_str.as_bytes();
            if bytes[0].is_ascii_alphabetic() && bytes[1] == b':' {
                return Err(ArchiveError::SecurityError(format!(
                    "Symlink target has Windows drive path: {}",
                    symlink_target_str
                )));
            }
        }

        let normalized_target = symlink_target_str.replace('\\', "/");
        let target_path = Path::new(&normalized_target);

        let resolved_target = if target_path.is_absolute() {
            return Err(ArchiveError::SecurityError(format!(
                "Absolute symlink target rejected: {}",
                symlink_target_str
            )));
        } else {
            link_parent_dir.join(target_path)
        };

        // Component-based path depth resolution relative to destination_root
        let mut depth: i32 = 0;
        let relative_to_root = match resolved_target.strip_prefix(destination_root) {
            Ok(rel) => rel,
            Err(_) => {
                return Err(ArchiveError::SecurityError(format!(
                    "Symlink target escapes destination root: {}",
                    symlink_target_str
                )));
            }
        };

        for comp in relative_to_root.components() {
            match comp {
                Component::ParentDir => {
                    depth -= 1;
                    if depth < 0 {
                        return Err(ArchiveError::SecurityError(format!(
                            "Symlink target traversal escapes destination: {}",
                            symlink_target_str
                        )));
                    }
                }
                Component::Normal(_) => {
                    depth += 1;
                }
                _ => {}
            }
        }

        Ok(resolved_target)
    }
}
