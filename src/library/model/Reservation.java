package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Một lượt đặt trước (API.md mục 3.5): dùng cho "data" của RESERVE_BOOK và từng phần tử của GET_MY_RESERVATIONS.
 * RESERVE_BOOK chỉ trả 6 trường đầu; các trường còn lại chỉ có ở GET_MY_RESERVATIONS.
 *
 * trangThai: WAITING | RESERVED | NOTIFIED | COMPLETED | CANCELLED
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Reservation {
    public int maDatTruoc;
    public int maDauSach;
    public String tenSach;
    public String ngayDat;          // yyyy-MM-dd'T'HH:mm:ss
    public String trangThai;
    public Integer viTriHangCho;    // Chỉ có giá trị khi WAITING

    // Chỉ có giá trị khi NOTIFIED (sách đã về quầy)
    public String maCuonSach;
    public String ngayThongBao;     // yyyy-MM-dd'T'HH:mm:ss
    public String hanNhanSach;      // yyyy-MM-dd
    public String thongBao;
}
