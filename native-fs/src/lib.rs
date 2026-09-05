use jni::objects::{JClass, JObject, JObjectArray, JString, JValue};
use jni::sys::{jint, jlong, jobjectArray};
use jni::JNIEnv;
use std::path::{Path, PathBuf};

use xt_engine::archive::{
    get_backend_for_format, get_backend_for_path, ArchiveError, CancelManager, OverwritePolicy,
};
use xt_engine::fs::{list_files, MetricsTracker};

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeGetLastMetrics<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    let metrics_class = match env.find_class("com/xtmanager/core/filesystem/LocalFileSystem$FsScanMetrics") {
        Ok(c) => c,
        Err(_) => return JObject::null(),
    };

    let metrics = MetricsTracker::get_last_metrics();
    let args = [
        JValue::Long(metrics.scan_us as i64),
        JValue::Long(metrics.sort_us as i64),
        JValue::Long(metrics.total_us as i64),
        JValue::Int(metrics.count as i32),
    ];

    match env.new_object(&metrics_class, "(JJJI)V", &args) {
        Ok(obj) => obj,
        Err(_) => JObject::null(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeListFiles<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jobjectArray {
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let item_class = match env.find_class("com/xtmanager/core/filesystem/LocalFileSystem$RawFileItem") {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let raw_items = list_files(&path_str);
    let result_array = match env.new_object_array(raw_items.len() as i32, &item_class, JObject::null()) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };

    for (idx, item) in raw_items.into_iter().enumerate() {
        let name_jstr = match env.new_string(&item.name) {
            Ok(s) => s,
            Err(_) => continue,
        };

        let name_obj = JObject::from(name_jstr);
        let args = [
            JValue::Object(&name_obj),
            JValue::Bool(if item.is_dir { 1 } else { 0 }),
            JValue::Long(item.size),
            JValue::Long(item.last_mod),
            JValue::Bool(if item.is_archive { 1 } else { 0 }),
        ];

        let item_obj = match env.new_object(&item_class, "(Ljava/lang/String;ZJJZ)V", &args) {
            Ok(o) => o,
            Err(_) => continue,
        };

        let _ = env.set_object_array_element(&result_array, idx as i32, item_obj);
    }

    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeCreateCancelToken(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let (id, _) = CancelManager::create_token();
    id as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeTriggerCancel(
    _env: JNIEnv,
    _class: JClass,
    token_id: jlong,
) {
    CancelManager::trigger_cancel(token_id as u64);
}

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeFreeCancelToken(
    _env: JNIEnv,
    _class: JClass,
    token_id: jlong,
) {
    CancelManager::free_token(token_id as u64);
}

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeListArchiveEntries<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    archive_path: JString<'local>,
    virtual_prefix: JString<'local>,
) -> jobjectArray {
    let path_str: String = match env.get_string(&archive_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let prefix_str: String = match env.get_string(&virtual_prefix) {
        Ok(s) => s.into(),
        Err(_) => "".to_string(),
    };

    let item_class = match env.find_class("com/xtmanager/core/filesystem/LocalFileSystem$RawFileItem") {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let backend = match get_backend_for_path(Path::new(&path_str)) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    let items = match backend.list_immediate_children(Path::new(&path_str), &prefix_str) {
        Ok(it) => it,
        Err(_) => return std::ptr::null_mut(),
    };

    let result_array = match env.new_object_array(items.len() as jint, &item_class, JObject::null()) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };

    for (idx, item) in items.into_iter().enumerate() {
        let name_jstr = match env.new_string(&item.name) {
            Ok(s) => s,
            Err(_) => continue,
        };

        let name_obj = JObject::from(name_jstr);
        let args = [
            JValue::Object(&name_obj),
            JValue::Bool(if item.is_dir { 1 } else { 0 }),
            JValue::Long(item.uncompressed_size as i64),
            JValue::Long(item.last_modified as i64),
            JValue::Bool(0),
        ];

        let item_obj = match env.new_object(&item_class, "(Ljava/lang/String;ZJJZ)V", &args) {
            Ok(o) => o,
            Err(_) => continue,
        };

        let _ = env.set_object_array_element(&result_array, idx as jint, item_obj);
    }

    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeExtractArchive<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    archive_path: JString<'local>,
    destination_dir: JString<'local>,
    overwrite_policy: jint,
    _token_id: jlong,
    _progress_listener: JObject<'local>,
) -> jint {
    let archive_str: String = match env.get_string(&archive_path) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    let dest_str: String = match env.get_string(&destination_dir) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };

    let backend = match get_backend_for_path(Path::new(&archive_str)) {
        Ok(b) => b,
        Err(_) => return -1,
    };

    let flag = match CancelManager::create_token().1 {
        f => f,
    };

    let policy = OverwritePolicy::from_i32(overwrite_policy);

    let res = backend.extract(
        Path::new(&archive_str),
        Path::new(&dest_str),
        policy,
        &flag,
        &|_prog| {},
    );

    match res {
        Ok(_) => 0,
        Err(ArchiveError::Cancelled) => -2,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_xtmanager_core_filesystem_LocalFileSystem_nativeCompressArchive<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    sources: jobjectArray,
    output_archive: JString<'local>,
    format: JString<'local>,
    level: jint,
    _token_id: jlong,
    _progress_listener: JObject<'local>,
) -> jint {
    let out_str: String = match env.get_string(&output_archive) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };

    let fmt_str: String = match env.get_string(&format) {
        Ok(s) => s.into(),
        Err(_) => "zip".to_string(),
    };

    let sources_obj = unsafe { JObjectArray::from_raw(sources) };
    let count = match env.get_array_length(&sources_obj) {
        Ok(l) => l,
        Err(_) => return -1,
    };

    let mut source_paths = Vec::new();
    for i in 0..count {
        let item = match env.get_object_array_element(&sources_obj, i) {
            Ok(o) => o,
            Err(_) => continue,
        };
        let jstr = JString::from(item);
        let path_str: String = match env.get_string(&jstr) {
            Ok(s) => s.into(),
            Err(_) => continue,
        };
        source_paths.push(PathBuf::from(path_str));
    }

    let backend = match get_backend_for_format(&fmt_str) {
        Ok(b) => b,
        Err(_) => match get_backend_for_path(Path::new(&out_str)) {
            Ok(b) => b,
            Err(_) => return -1,
        },
    };

    let flag = match CancelManager::create_token().1 {
        f => f,
    };

    let res = backend.compress(
        &source_paths,
        Path::new(&out_str),
        level,
        &flag,
        &|_prog| {},
    );

    match res {
        Ok(_) => 0,
        Err(ArchiveError::Cancelled) => -2,
        Err(_) => -1,
    }
}
