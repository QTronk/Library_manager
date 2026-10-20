package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phần "data" của action GET_BOOK_DETAIL (API.md mục 3.2.2), chỉ lấy các trường cần cho việc quản lý đầu sách:
 * { maDauSach, tenSach, giaTien, tacGia, theLoai, nhaXuatBan, namXuatBan, viTri, tongSoLuong, soLuongKhaDung, ... }
 *
 * Lưu ý: Backend trả TÊN tác giả / thể loại / NXB chứ không trả mã; muốn biết mã phải đối chiếu tên với {@link Categories}.
 * Danh sách cuốn sách vật lý (cuonSachList) chưa dùng nên bị bỏ qua.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookDetail {
    public int maDauSach;
    public String tenSach;
    public double giaTien;
    public String tacGia;
    public String theLoai;
    public String nhaXuatBan;
    public Integer namXuatBan;     // Năm của nhà xuất bản, có thể null
    public String viTri;
    public int tongSoLuong;        // Số cuốn hiện có (không tính cuốn mất)
    public int soLuongKhaDung;     // Số cuốn đang sẵn sàng cho mượn
}
