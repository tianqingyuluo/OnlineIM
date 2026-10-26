fn main() {
    // 单元测试和首次 cargo check 时前端产物可能还不存在。缺的时候放一个占位页，
    // 不覆盖已经由 Vite 打出来的 dist。
    let dist = std::path::Path::new("../dist");
    let index = dist.join("index.html");
    if !index.exists() {
        let _ = std::fs::create_dir_all(dist);
        let _ = std::fs::write(
            index,
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>OnlineIM</title></head><body></body></html>",
        );
    }
    tauri_build::build()
}
