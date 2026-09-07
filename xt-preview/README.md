# 🚀 xt-engine

High-performance, Android-independent Rust core filesystem scanner and archive engine.

## 📖 Architecture & Features

`xt-engine` provides clean, reusable Rust modules for high-speed local filesystem operations and secure archive manipulation:

```
xt-engine/
├── src/
│   ├── fs/
│   │   ├── scanner.rs   # Fast natural sorting & read_dir scanning
│   │   └── metrics.rs   # Microsecond metrics tracking
│   ├── archive/
│   │   ├── traits.rs    # Format-neutral ArchiveBackend trait & capabilities
│   │   ├── security.rs  # Component-based path traversal (Zip Slip) protection
│   │   ├── cancel.rs    # Thread-safe atomic cancellation token manager
│   │   ├── atomic.rs    # Same-mount staging (.xt-tmp-*) and atomic commit phase
│   │   ├── session.rs   # Session index cache (60s TTL) with lazy child filtering
│   │   ├── zip_backend.rs     # ZIP & APK engine
│   │   ├── tar_backend.rs     # TAR, TAR.GZ, TGZ engine
│   │   ├── sevenz_backend.rs  # 7z engine
│   │   └── tests.rs     # Comprehensive Rust unit test suite
│   └── lib.rs           # Core Rust library API
```

## 🛠️ Usage Examples

### 1. Filesystem Directory Scanning & Natural Sorting
```rust
use xt_engine::fs::list_files;

let items = list_files("/path/to/directory");
for item in items {
    println!("Name: {}, is_dir: {}, size: {} B", item.name, item.is_dir, item.size);
}
```

### 2. Archive Extraction with Atomic Staging & Cancellation
```rust
use std::path::Path;
use std::sync::atomic::AtomicBool;
use xt_engine::archive::{get_backend_for_path, OverwritePolicy};

let archive_path = Path::new("bundle.zip");
let dest_dir = Path::new("/target/dir");
let backend = get_backend_for_path(archive_path).unwrap();

let cancel_flag = AtomicBool::new(false);
backend.extract(
    archive_path,
    dest_dir,
    OverwritePolicy::Overwrite,
    &cancel_flag,
    &|progress| {
        println!("Progress: {} / {} bytes", progress.processed_bytes, progress.total_bytes);
    },
).unwrap();
```

## 🧪 Testing & Verification
```bash
cargo check
cargo test
cargo clippy
cargo fmt --check
```