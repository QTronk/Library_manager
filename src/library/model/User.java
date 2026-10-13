package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * Thông tin người dùng sau khi đăng nhập.
 * Khớp với JSON mà Backend trả về cho action LOGIN:
 * { maND, fullName, email, phone, role, maThe, trangThaiThe }
 *
 * ignoreUnknown = true: nếu Backend thêm field mới thì giao diện không bị lỗi parse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User implements Serializable {
    private Integer maND;
    private String username;      // Backend không trả field này; chỉ dùng cho chế độ Mock
    private String fullName;
    private String email;
    private String phone;
    private String role;          // Đã chuẩn hóa về "ADMIN", "LIBRARIAN", "READER" (xem SocketClient.normalizeRole)
    private Integer maThe;        // -1 nếu người dùng chưa có thẻ độc giả
    private String trangThaiThe;  // "NONE" nếu chưa có thẻ

    public User() {}

    public User(String username, String fullName, String role) {
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public Integer getMaND() { return maND; }
    public void setMaND(Integer maND) { this.maND = maND; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getMaThe() { return maThe; }
    public void setMaThe(Integer maThe) { this.maThe = maThe; }

    public String getTrangThaiThe() { return trangThaiThe; }
    public void setTrangThaiThe(String trangThaiThe) { this.trangThaiThe = trangThaiThe; }
}
