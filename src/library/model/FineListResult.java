package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần "data" của action GET_FINES (API.md mục 3.6.3):
 * { totalResults, page, pageSize, totalPages, tongChuaThanhToan, items: [ ... ] }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FineListResult {
    public int totalResults;
    public int page;
    public int pageSize;
    public int totalPages;
    public double tongChuaThanhToan;   // Tổng tiền các phiếu chưa nộp (của toàn bộ thẻ, không phụ thuộc trang)
    public List<Item> items = new ArrayList<>();

    /** Một phiếu phạt. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        public int maPhieuPhat;
        public int maCTPM;
        public String maCuonSach;
        public String tenSach;
        public int maThe;
        public String tenDocGia;
        public String ngayLapPhieu;    // yyyy-MM-dd'T'HH:mm:ss
        public String lyDo;
        public double soTienPhat;
        public boolean daThanhToan;
    }
}
