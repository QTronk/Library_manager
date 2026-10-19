package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần "data" của action PROCESS_RETURN_BOOK (API.md mục 3.4.4):
 * { tongTienPhat, coPhat, ketQua: [ { maCTPM, maCuonSach, tenSach, soNgayTre, trangThaiCuonSach,
 *                                      phieuPhat: [ { maPhieuPhat, lyDo, soTienPhat } ],
 *                                      datTruocDuocThongBao } ] }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReturnBookResult {
    public double tongTienPhat;
    public boolean coPhat;
    public List<Item> ketQua = new ArrayList<>();

    /** Kết quả trả của một cuốn sách. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        public int maCTPM;
        public String maCuonSach;
        public String tenSach;
        public long soNgayTre;
        public String trangThaiCuonSach;          // AVAILABLE | RESERVED | DAMAGED | LOST
        public List<Fine> phieuPhat = new ArrayList<>();
        public Integer datTruocDuocThongBao;      // Mã đặt trước vừa được thông báo, null nếu không có
    }

    /** Một phiếu phạt vừa được tạo khi trả sách. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fine {
        public int maPhieuPhat;
        public String lyDo;
        public double soTienPhat;
    }
}
