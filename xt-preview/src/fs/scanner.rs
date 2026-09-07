use std::fs;
use std::os::unix::fs::MetadataExt;
use std::path::Path;
use std::time::Instant;
use super::metrics::MetricsTracker;

#[derive(Debug, Clone)]
pub struct RawFileItem {
    pub name: String,
    pub is_dir: bool,
    pub size: i64,
    pub last_mod: i64,
    pub is_archive: bool,
}

pub fn natural_cmp(s1: &str, s2: &str) -> std::cmp::Ordering {
    let mut c1 = s1.chars().peekable();
    let mut c2 = s2.chars().peekable();

    loop {
        match (c1.peek(), c2.peek()) {
            (None, None) => return std::cmp::Ordering::Equal,
            (None, Some(_)) => return std::cmp::Ordering::Less,
            (Some(_), None) => return std::cmp::Ordering::Greater,
            (Some(&ch1), Some(&ch2)) => {
                if ch1.is_ascii_digit() && ch2.is_ascii_digit() {
                    let mut n1: u64 = 0;
                    while let Some(&d) = c1.peek() {
                        if d.is_ascii_digit() {
                            n1 = n1.saturating_mul(10).saturating_add((d as u8 - b'0') as u64);
                            c1.next();
                        } else {
                            break;
                        }
                    }

                    let mut n2: u64 = 0;
                    while let Some(&d) = c2.peek() {
                        if d.is_ascii_digit() {
                            n2 = n2.saturating_mul(10).saturating_add((d as u8 - b'0') as u64);
                            c2.next();
                        } else {
                            break;
                        }
                    }

                    if n1 != n2 {
                        return n1.cmp(&n2);
                    }
                } else {
                    let l1 = ch1.to_lowercase().next().unwrap_or(ch1);
                    let l2 = ch2.to_lowercase().next().unwrap_or(ch2);
                    if l1 != l2 {
                        return l1.cmp(&l2);
                    }
                    c1.next();
                    c2.next();
                }
            }
        }
    }
}

pub fn list_files(path_str: &str) -> Vec<RawFileItem> {
    let start_total = Instant::now();
    let dir_path = Path::new(path_str);
    let mut raw_items = Vec::new();

    let start_scan = Instant::now();
    if let Ok(entries) = fs::read_dir(dir_path) {
        for entry in entries.flatten() {
            let file_name = entry.file_name().to_string_lossy().to_string();
            if file_name == "." || file_name == ".." {
                continue;
            }

            let metadata = entry.metadata();
            let (is_dir, size, last_mod) = match metadata {
                Ok(m) => {
                    let is_d = m.is_dir();
                    let sz = if is_d { 0 } else { m.len() as i64 };
                    let mtime = m.mtime() * 1000;
                    (is_d, sz, mtime)
                }
                Err(_) => (false, 0i64, 0i64),
            };

            let name_lower = file_name.to_lowercase();
            let is_archive = name_lower.ends_with(".zip")
                || name_lower.ends_with(".tar")
                || name_lower.ends_with(".tar.gz")
                || name_lower.ends_with(".tgz")
                || name_lower.ends_with(".tar.bz2")
                || name_lower.ends_with(".tar.xz")
                || name_lower.ends_with(".7z")
                || name_lower.ends_with(".apk");

            raw_items.push(RawFileItem {
                name: file_name,
                is_dir,
                size,
                last_mod,
                is_archive,
            });
        }
    }

    let clean_path = path_str.trim_end_matches('/');
    let known_paths: &[&str] = match clean_path {
        "" => &[
            "apex", "bin", "bugreports", "config", "data", "dev", "etc",
            "init", "linkerconfig", "mnt", "odm", "oem", "proc", "product",
            "res", "sdcard", "storage", "sys", "system", "vendor"
        ],
        "/storage" => &["emulated", "self", "sdcard0", "0"],
        "/storage/emulated" => &["0"],
        "/system" => &["app", "bin", "etc", "fonts", "framework", "lib", "lib64", "media", "priv-app", "usr"],
        _ => &[],
    };

    for name in known_paths {
        if !raw_items.iter().any(|item| item.name == *name) {
            let child = if clean_path.is_empty() {
                Path::new("/").join(name)
            } else {
                dir_path.join(name)
            };

            if child.exists() {
                let metadata = child.metadata();
                let (is_dir, size, last_mod) = match metadata {
                    Ok(m) => {
                        let is_d = m.is_dir();
                        let sz = if is_d { 0 } else { m.len() as i64 };
                        let mtime = m.mtime() * 1000;
                        (is_d, sz, mtime)
                    }
                    Err(_) => (true, 0i64, 0i64),
                };
                raw_items.push(RawFileItem {
                    name: name.to_string(),
                    is_dir,
                    size,
                    last_mod,
                    is_archive: false,
                });
            }
        }
    }

    let scan_dur = start_scan.elapsed();

    let start_sort = Instant::now();
    raw_items.sort_unstable_by(|a, b| {
        b.is_dir.cmp(&a.is_dir).then_with(|| natural_cmp(&a.name, &b.name))
    });
    let sort_dur = start_sort.elapsed();

    let total_dur = start_total.elapsed();
    MetricsTracker::update(
        scan_dur.as_micros() as u64,
        sort_dur.as_micros() as u64,
        total_dur.as_micros() as u64,
        raw_items.len(),
    );

    raw_items
}
