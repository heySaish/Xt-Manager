use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::jobjectArray;
use jni::JNIEnv;
use std::fs;
use std::os::unix::fs::MetadataExt;
use std::path::Path;

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

    let dir_path = Path::new(&path_str);
    let mut raw_items = Vec::new();

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
                || name_lower.ends_with(".rar")
                || name_lower.ends_with(".apk");

            raw_items.push((file_name, is_dir, size, last_mod, is_archive));
        }
    }

    // Sort folders first, then alphabetical
    raw_items.sort_by(|a, b| {
        b.1.cmp(&a.1).then_with(|| a.0.to_lowercase().cmp(&b.0.to_lowercase()))
    });

    let array_size = raw_items.len() as i32;
    let result_array = match env.new_object_array(array_size, &item_class, JObject::null()) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };

    for (idx, (name, is_dir, size, last_mod, is_archive)) in raw_items.into_iter().enumerate() {
        let name_jstr = match env.new_string(name) {
            Ok(s) => s,
            Err(_) => continue,
        };

        let args = [
            JValue::Object(&name_jstr.into()),
            JValue::Bool(if is_dir { 1 } else { 0 }),
            JValue::Long(size),
            JValue::Long(last_mod),
            JValue::Bool(if is_archive { 1 } else { 0 }),
        ];

        let item_obj = match env.new_object(&item_class, "(Ljava/lang/String;ZJJZ)V", &args) {
            Ok(o) => o,
            Err(_) => continue,
        };

        let _ = env.set_object_array_element(&result_array, idx as i32, item_obj);
    }

    result_array.into_raw()
}
