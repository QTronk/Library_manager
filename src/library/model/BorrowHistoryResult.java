package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần "data" của action GET_BORROW_HISTORY (API.md mục 3.4.6) với tab = BORROWING:
 * { totalResults, page, pageSize, totalPages, items: [ ... ] }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BorrowHistoryResult {
    public int totalResults;
    public int page;
    public int pageSize;
    public int totalPages;
    public List<Item> items = new ArrayList<>();

    /** Một cuốn sách đang mượn. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        public int maCTPM;
        public int maPhieuMuon;
        public int maThe;
        public String tenDocGia;
        public String maCuonSach;
        public String tenSach;
        public String ngayMuon;        // yyyy-MM-dd'T'HH:mm:ss
        public String ngayHenTra;      // yyyy-MM-dd
        public int soLanGiaHan;
        public boolean quaHan;
        public long soNgayTre;
        public double tienPhatDuKien;
        public boolean coTheGiaHan;
    }
}
