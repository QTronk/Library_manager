package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần "data" của GET_LIBRARIANS (API.md 3.7.2) và GET_READERS (API.md 3.7.1).
 * Hai action trả hai dạng phần tử hơi khác nhau nên gộp chung vào Item; trường nào không có thì để null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserListResult {
    public int totalResults;
    public int page;
    public int pageSize;
    public int totalPages;
    public List<Item> items = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        public int maND;
        public String hoTen;
        public String email;
        public String soDienThoai;

        // Chỉ có ở GET_LIBRARIANS
        public Boolean trangThai;            // true = hoạt động, false = bị khóa

        // Chỉ có ở GET_READERS
        public Boolean trangThaiTaiKhoan;    // true = hoạt động, false = bị khóa
        public Integer maThe;
        public String ngayCap;               // yyyy-MM-dd
        public String ngayHetHan;            // yyyy-MM-dd
        public String trangThaiThe;          // ACTIVE | LOCKED | EXPIRED | NONE
        public Long dangMuon;                // Số cuốn đang mượn
    }
}
