package library.service;

import library.model.User;

public class MockDatabase {

    public static User authenticate(String username, String password) throws Exception {
        // Giả lập thời gian gói tin di chuyển qua Socket mạng khoảng 1 giây[cite: 1]
        Thread.sleep(1000); 

        if ("admin".equals(username) && "123".equals(password)) {
            return new User("admin", "Quản trị viên", "ADMIN");
        } else if ("thuthu".equals(username) && "123".equals(password)) {
            return new User("thuthu", "Nguyễn Văn A", "LIBRARIAN");
        } else if ("docgia".equals(username) && "123".equals(password)) {
            return new User("docgia", "Cao Quốc Trọng", "READER");
        }

        return null; // Trả về null nếu sai tài khoản/mật khẩu
    }
}