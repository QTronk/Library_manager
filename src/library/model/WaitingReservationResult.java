package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần "data" của action GET_WAITING_RESERVATIONS (API.md mục 3.5.4): { danhSach: [ ... ] }.
 * Mỗi phần tử là một lượt đặt trước của một độc giả bất kỳ (Thủ thư xem toàn bộ).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WaitingReservationResult {
    public List<Item> danhSach = new ArrayList<>();

    /** Một lượt đặt trước trong danh sách chờ. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        public int maDatTruoc;
        public int maThe;
        public String tenDocGia;
        public int maDauSach;
        public String tenSach;
        public String ngayDat;            // yyyy-MM-dd'T'HH:mm:ss
        public String trangThai;          // WAITING | RESERVED | NOTIFIED | COMPLETED | CANCELLED
        public CuonGoiY cuonSachGoiY;     // Chỉ có khi WAITING và đầu sách có cuốn đang được mượn; còn lại null
    }

    /** Cuốn đang được mượn có ngày hẹn trả gần nhất của đầu sách đó (gợi ý để khóa cho lượt chờ). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CuonGoiY {
        public String maCuonSach;
        public String ngayHenTra;         // yyyy-MM-dd
    }
}
