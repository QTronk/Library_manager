package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phần "data" của action ADD_BOOK_COPIES (API.md mục 3.3.4):
 * { maDauSach, soCuonDaThem, tongSoLuong, soLuongKhaDung }.
 * tongSoLuong / soLuongKhaDung là số liệu MỚI của đầu sách sau khi nhập.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddCopiesResult {
    public int maDauSach;
    public int soCuonDaThem;
    public int tongSoLuong;
    public int soLuongKhaDung;

    public String message;   // Thông báo của Backend (không nằm trong data, do SocketClient gán)
}
