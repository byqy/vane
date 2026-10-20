// Văn bản trợ giúp cấp trường cho biểu mẫu quản trị (vi). Tập khóa phải khớp với mọi ngôn ngữ khác (field-help.test.ts kiểm tra).
// Được lưu riêng với trợ giúp cấp trang trong locales/help/<locale>.ts; quy tắc gộp nằm ở locales/help/index.ts.

const message = {
  "fieldHelp.open": "Trợ giúp trường",
  "field.navs.labelKey.help": "Khóa thông điệp cho tên menu, ví dụ nav.about. Khóa phải đã tồn tại trong tài nguyên ngôn ngữ, nếu không cổng sẽ hiển thị khóa thô.",
  "field.navs.target.help": "Đường dẫn tương đối để mở khi nhấp, ví dụ /, /about, /articles. Giao diện tự thêm tiền tố ngôn ngữ, không nhập tên miền đầy đủ.",
  "field.navs.sortNo.help": "Giá trị nhỏ hơn đứng trước; menu cùng cấp được sắp xếp theo giá trị này. Có thể để trống (mặc định 0).",
  "field.pages.slug.help": "Một phần URL trang (/{locale}/{slug}). Hạn chế thay đổi sau khi xuất bản, liên kết cũ sẽ trả 404.",
  "field.articles.slug.help": "Một phần URL bài viết (/{locale}/articles/{slug}); cũng ảnh hưởng đến liên kết hiện có và việc lập chỉ mục tìm kiếm.",
  "field.articles.category.help": "Quyết định bài viết xuất hiện ở trang danh mục nào (/{locale}/categories/{code}). Danh mục một cấp, có thể để trống.",
  "field.articles.tags.help": "Thẻ của bài viết, có thể chọn nhiều; cổng có thể lọc theo thẻ. Nếu chưa có thẻ phù hợp, có thể tạo mới ngay tại đây.",
  "field.articles.newTag.help": "Nhập mã thẻ rồi nhấn “Tạo thẻ”: thẻ được tạo và tự động chọn cho bài viết này.",
  "field.categories.code.help": "Mã danh mục xuất hiện trong URL /categories/{code}; không thay đổi sau khi tạo.",
  "field.tags.code.help": "Mã thẻ duy nhất dùng để lọc và chống trùng; thẻ không vào URL nên đổi tên không làm hỏng liên kết.",
  "field.users.username.help": "Tên đăng nhập, không thể thay đổi sau khi tạo. Nên dùng tiền tố email hoặc mã nhân viên, tránh thông tin có thể thay đổi.",
  "field.users.password.help": "Mật khẩu ban đầu chỉ dùng khi tạo mới; lần đăng nhập đầu tiên sẽ yêu cầu đổi. Để trống để hệ thống tự sinh theo chính sách mật khẩu.",
  "field.users.dept.help": "Tổ chức của tài khoản; quyết định phạm vi dữ liệu “tổ chức này” và “tổ chức này và cấp dưới”. Chọn sai sẽ không thấy dữ liệu hoặc thấy dữ liệu không nên thấy.",
  "field.users.roles.help": "Vai trò quyết định quyền menu và nút; có thể chọn nhiều, quyền được hợp nhất. Phải chọn ít nhất một vai trò.",
  "field.users.status.help": "Tài khoản bị vô hiệu hóa không thể đăng nhập; dữ liệu và nhật ký vẫn được giữ.",
  "field.roles.code.help": "Mã vai trò dùng cho phân quyền API và kiểm tra vai trò tích hợp; không thể thay đổi sau khi tạo, nên dùng chữ in hoa và dấu gạch dưới.",
  "field.roles.dataScope.help": "Quyết định vai trò thấy dữ liệu của những tổ chức nào. Khi chọn “Tùy chỉnh”, cần đánh dấu tổ chức cụ thể trong “Phạm vi tổ chức”.",
  "field.configs.type.help": "Loại tham số chỉ đọc, quyết định cách phân tích giá trị (chuỗi, số, boolean, JSON). Loại không khớp giá trị sẽ khiến đọc thất bại.",
  "field.configs.value.help": "Nhập giá trị theo loại; JSON phải là JSON hợp lệ. Tham số bí mật chỉ nên dùng chỗ giữ chỗ hoặc tham chiếu biến môi trường, không lưu dạng văn bản thuần.",
  "field.dicts.typeCode.help": "Chương trình đọc từ điển theo mã này; nên dùng chữ thường và dấu gạch dưới, không thay đổi sau khi tạo.",
  "field.dicts.nameKey.help": "Khóa thông điệp của tên hiển thị (ví dụ dict.someName); phải đã tồn tại trong tài nguyên ngôn ngữ.",
  "field.dicts.itemValue.help": "Giá trị mục từ điển được lưu và trả về qua API; không nên thay đổi sau khi tạo để dữ liệu cũ khớp.",
  "field.dicts.labelZh.help": "Nhãn hiển thị trên giao diện tiếng Trung; nhãn tiếng Anh được quản lý riêng ở trường tiếp theo.",
  "field.jobs.cron.help": "Biểu thức 6 trường: giây phút giờ ngày tháng thứ. Ví dụ 0 0 3 * * * chạy hằng ngày lúc 03:00; có thể dùng “Chạy một lần” để kiểm tra.",
  "field.jobs.params.help": "Đối tượng JSON truyền cho bộ xử lý tác vụ; để trống nếu không có tham số.",
  "field.jobs.concurrency.help": "Khi cấm chạy đồng thời, lần chạy sẽ bị bỏ qua nếu lần trước chưa kết thúc; khi cho phép, các lần chạy có thể chồng lên nhau.",
  "field.banners.image.help": "Tải tệp lên “Quản lý tệp” trước, rồi chọn tại đây; nên dùng ảnh ngang 16:9, mỗi ảnh không quá 500 KB.",
  "field.banners.link.help": "Địa chỉ mở khi nhấp: đường dẫn tương đối hoặc URL đầy đủ. Để trống nghĩa là không thể nhấp.",
  "field.banners.startAt.help": "Tự động xuất hiện trên cổng vào thời điểm đã đặt; để trống nghĩa là có hiệu lực ngay.",
  "field.banners.endAt.help": "Tự động gỡ xuống vào thời điểm đã đặt; để trống nghĩa là có hiệu lực lâu dài.",
  "field.mail.variables.help": "Biến mẫu nhập bằng đối tượng JSON; khóa phải khớp với chỗ giữ chỗ {name} trong nội dung. Chỉ dùng cho gửi thử.",
  "field.notices.publishAt.help": "Tự động xuất hiện trên cổng vào thời điểm đã đặt; để trống nghĩa là có hiệu lực ngay.",
  "field.notices.expireAt.help": "Tự động gỡ xuống vào thời điểm đã đặt; để trống nghĩa là có hiệu lực lâu dài."
}

export default message
