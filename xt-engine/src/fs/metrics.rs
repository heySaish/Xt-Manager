use std::sync::atomic::{AtomicU64, Ordering};

static LAST_SCAN_US: AtomicU64 = AtomicU64::new(0);
static LAST_SORT_US: AtomicU64 = AtomicU64::new(0);
static LAST_TOTAL_US: AtomicU64 = AtomicU64::new(0);
static LAST_ENTRY_COUNT: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Clone, Copy)]
pub struct FsScanMetrics {
    pub scan_us: u64,
    pub sort_us: u64,
    pub total_us: u64,
    pub count: usize,
}

pub struct MetricsTracker;

impl MetricsTracker {
    pub fn update(scan_us: u64, sort_us: u64, total_us: u64, count: usize) {
        LAST_SCAN_US.store(scan_us, Ordering::Relaxed);
        LAST_SORT_US.store(sort_us, Ordering::Relaxed);
        LAST_TOTAL_US.store(total_us, Ordering::Relaxed);
        LAST_ENTRY_COUNT.store(count as u64, Ordering::Relaxed);
    }

    pub fn get_last_metrics() -> FsScanMetrics {
        FsScanMetrics {
            scan_us: LAST_SCAN_US.load(Ordering::Relaxed),
            sort_us: LAST_SORT_US.load(Ordering::Relaxed),
            total_us: LAST_TOTAL_US.load(Ordering::Relaxed),
            count: LAST_ENTRY_COUNT.load(Ordering::Relaxed) as usize,
        }
    }
}
