package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phần "data" của action CHECK_CARD_ELIGIBILITY (API.md mục 3.4.1):
 * { eligible, reasonCode, docGia: {...}, dangMuon, soSachToiDa, conDuocMuon }.
 *
 * Lý do (hoặc "Thẻ đủ điều kiện mượn sách") nằm ở "message" của Response chứ không nằm trong data,
 * nên SocketClient chép sang trường {@link #message} để giao diện chỉ cần giữ một đối tượng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardEligibilityResult {
    public boolean eligible;
    public String reasonCode;   // null khi đủ điều kiện; CARD_LOCKED | CARD_EXPIRED | HAS_UNPAID_FINE | HAS_OVERDUE_BOOK | BORROW_LIMIT_REACHED
    public DocGia docGia;
    public int dangMuon;
    public int soSachToiDa;
    public int conDuocMuon;

    public String message;      // Thông báo của Backend (không có trong data, do SocketClient gán)

    /** Thông tin độc giả sở hữu thẻ. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocGia {
        public int maND;
        public String hoTen;
        public String email;
        public String soDienThoai;
        public int maThe;
        public String ngayCap;       // yyyy-MM-dd
        public String ngayHetHan;    // yyyy-MM-dd
        public String trangThaiThe;  // ACTIVE | LOCKED | EXPIRED
    }
}
