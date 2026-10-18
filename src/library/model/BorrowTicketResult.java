package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả action CREATE_BORROW_TICKET (API.md mục 3.4.3):
 * { maPhieuMuon, maThe, ngayMuon, chiTiet: [ { maCTPM, maCuonSach, tenSach, ngayHenTra } ] }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BorrowTicketResult {
    public int maPhieuMuon;
    public int maThe;
    public String ngayMuon;                 // yyyy-MM-dd'T'HH:mm:ss
    public List<Line> chiTiet = new ArrayList<>();

    /** Một dòng sách trong phiếu mượn. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Line {
        public int maCTPM;
        public String maCuonSach;
        public String tenSach;
        public String ngayHenTra;           // yyyy-MM-dd
    }
}
