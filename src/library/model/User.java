package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * Thông tin người dùng sau khi đăng nhập.
 * Khớp với JSON mà Backend trả về cho action LOGIN:
 * { maND, fullName, email, phone, maVaiTro, role, maThe, trangThaiThe, ngayHetHanThe }
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
    private Integer maVaiTro;     // 1 = Admin, 2 = Thủ thư, 3 = Độc giả (nguồn chính để xác định role)
    private String role;          // Đã chuẩn hóa về "ADMIN", "LIBRARIAN", "READER" (xem SocketClient.normalizeRole)
    private Integer maThe;        // -1 nếu người dùng chưa có thẻ độc giả
    private String trangThaiThe;  // "NONE" nếu chưa có thẻ
    private String ngayHetHanThe; // yyyy-MM-dd, null nếu chưa có thẻ

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

    public Integer getMaVaiTro() { return maVaiTro; }
    public void setMaVaiTro(Integer maVaiTro) { this.maVaiTro = maVaiTro; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getMaThe() { return maThe; }
    public void setMaThe(Integer maThe) { this.maThe = maThe; }

    public String getTrangThaiThe() { return trangThaiThe; }
    public void setTrangThaiThe(String trangThaiThe) { this.trangThaiThe = trangThaiThe; }

    public String getNgayHetHanThe() { return ngayHetHanThe; }
    public void setNgayHetHanThe(String ngayHetHanThe) { this.ngayHetHanThe = ngayHetHanThe; }
}
