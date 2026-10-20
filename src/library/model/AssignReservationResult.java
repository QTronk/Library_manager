package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phần "data" của action ASSIGN_RESERVATION (API.md mục 3.5.5):
 * { maDatTruoc, trangThai, maCuonSach, ngayHenTraCuaCuon }.
 *
 * trangThai = RESERVED: đã khóa một cuốn ĐANG ĐƯỢC MƯỢN cho lượt đặt (ngayHenTraCuaCuon là hạn trả của cuốn đó).
 * trangThai = NOTIFIED: đầu sách đã có cuốn sẵn sàng nên giữ thẳng tại quầy và báo độc giả (ngayHenTraCuaCuon = null).
 * Thông báo của Backend nằm ở "message" của Response, được SocketClient chép sang {@link #message}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssignReservationResult {
    public int maDatTruoc;
    public String trangThai;
    public String maCuonSach;
    public String ngayHenTraCuaCuon;   // yyyy-MM-dd hoặc null

    public String message;             // Do SocketClient gán
}
