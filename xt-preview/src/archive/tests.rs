#[cfg(test)]
mod archive_security_tests {
    use std::sync::atomic::AtomicBool;
    use tempfile::tempdir;

    use crate::archive::atomic::AtomicStagingContext;
    use crate::archive::cancel::CancelManager;
    use crate::archive::security::PathValidator;
    use crate::archive::session::ArchiveSessionManager;
    use crate::archive::traits::{ArchiveEntryItem, OverwritePolicy};

    #[test]
    fn test_relative_dot_dot_traversal_rejected() {
        let dest = tempdir().unwrap();
        let result = PathValidator::validate_and_resolve_entry_path(dest.path(), "../foo.txt");
        assert!(result.is_err());
    }

    #[test]
    fn test_deep_parent_traversal_rejected() {
        let dest = tempdir().unwrap();
        let result =
            PathValidator::validate_and_resolve_entry_path(dest.path(), "../../etc/passwd");
        assert!(result.is_err());
    }

    #[test]
    fn test_absolute_path_rejected() {
        let dest = tempdir().unwrap();
        let result =
            PathValidator::validate_and_resolve_entry_path(dest.path(), "/etc/systemd/system");
        assert!(result.is_err());
    }

    #[test]
    fn test_windows_drive_path_rejected() {
        let dest = tempdir().unwrap();
        let result =
            PathValidator::validate_and_resolve_entry_path(dest.path(), "C:\\Windows\\System32");
        assert!(result.is_err());
    }

    #[test]
    fn test_backslash_traversal_rejected() {
        let dest = tempdir().unwrap();
        let result =
            PathValidator::validate_and_resolve_entry_path(dest.path(), "..\\..\\data\\app");
        assert!(result.is_err());
    }

    #[test]
    fn test_nul_byte_rejected() {
        let dest = tempdir().unwrap();
        let result =
            PathValidator::validate_and_resolve_entry_path(dest.path(), "foo.txt\0.exe");
        assert!(result.is_err());
    }

    #[test]
    fn test_valid_safe_nested_entry() {
        let dest = tempdir().unwrap();
        let target =
            PathValidator::validate_and_resolve_entry_path(dest.path(), "sub/folder/file.txt")
                .unwrap();
        assert_eq!(target, dest.path().join("sub/folder/file.txt"));
    }

    #[test]
    fn test_symlink_escaping_destination_rejected() {
        let dest = tempdir().unwrap();
        let link_parent = dest.path().join("sub");
        let result = PathValidator::validate_symlink_target(&dest.path(), &link_parent, "../../outside.txt");
        assert!(result.is_err());
    }

    #[test]
    fn test_cancellation_token_flag_behavior() {
        let (id, flag) = CancelManager::create_token();
        assert_eq!(flag.load(std::sync::atomic::Ordering::SeqCst), false);

        CancelManager::trigger_cancel(id);
        assert_eq!(flag.load(std::sync::atomic::Ordering::SeqCst), true);

        CancelManager::free_token(id);
    }

    #[test]
    fn test_atomic_staging_cleanup_on_cancel() {
        let dest = tempdir().unwrap();
        let mut staging = AtomicStagingContext::new(dest.path()).unwrap();
        let cancel_flag = AtomicBool::new(true);

        let target = staging.prepare_staging_target("test.txt", false).unwrap();
        std::fs::write(&target, "dummy data").unwrap();

        let res = staging.commit(
            OverwritePolicy::Overwrite,
            &cancel_flag,
            &|_| {},
            10,
            1,
        );

        assert!(res.is_err());
        assert!(!target.exists());
    }

    #[test]
    fn test_lazy_immediate_children_filtering() {
        let mut entries = Vec::new();
        entries.push(ArchiveEntryItem {
            name: "a.txt".to_string(),
            virtual_path: "src/a.txt".to_string(),
            is_dir: false,
            uncompressed_size: 100,
            last_modified: 0,
            is_encrypted: false,
        });
        entries.push(ArchiveEntryItem {
            name: "main".to_string(),
            virtual_path: "src/main/b.txt".to_string(),
            is_dir: true,
            uncompressed_size: 200,
            last_modified: 0,
            is_encrypted: false,
        });
        entries.push(ArchiveEntryItem {
            name: "deep".to_string(),
            virtual_path: "src/main/deep/c.txt".to_string(),
            is_dir: true,
            uncompressed_size: 300,
            last_modified: 0,
            is_encrypted: false,
        });

        let children = ArchiveSessionManager::filter_immediate_children(&entries, "src");
        assert_eq!(children.len(), 2);
        assert!(children.iter().any(|c| c.name == "a.txt" && !c.is_dir));
        assert!(children.iter().any(|c| c.name == "main" && c.is_dir));
    }

    #[test]
    fn test_10k_entries_performance() {
        let mut entries = Vec::new();
        for i in 0..10_000 {
            entries.push(ArchiveEntryItem {
                name: format!("file_{}.txt", i),
                virtual_path: format!("root/dir_{}/file_{}.txt", i % 10, i),
                is_dir: false,
                uncompressed_size: 1024,
                last_modified: 0,
                is_encrypted: false,
            });
        }

        let start = std::time::Instant::now();
        let children = ArchiveSessionManager::filter_immediate_children(&entries, "root");
        let duration = start.elapsed();

        assert_eq!(children.len(), 10);
        assert!(duration.as_millis() < 50);
    }
}
