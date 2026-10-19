package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cấu hình quy định của hệ thống (UC-04), khớp "data" của GET_SYSTEM_CONFIG / UPDATE_SYSTEM_CONFIG:
 * { soSachToiDa, soNgayMuonToiDa, tienPhatNgay, soLanGiaHanToiDa, soNgayGiuSachDatTruoc }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemConfig {
    public int soSachToiDa;            // Số cuốn tối đa một độc giả được mượn cùng lúc
    public int soNgayMuonToiDa;        // Số ngày mỗi lượt mượn (cũng là số ngày cộng thêm mỗi lần gia hạn)
    public double tienPhatNgay;        // Tiền phạt mỗi ngày trễ hạn (VNĐ)
    public int soLanGiaHanToiDa;       // Số lần gia hạn tối đa cho mỗi cuốn
    public int soNgayGiuSachDatTruoc;  // Số ngày giữ sách ở quầy cho người đặt trước

    /** So sánh giá trị với một cấu hình khác (dùng để biết người dùng có thật sự sửa gì không). */
    public boolean sameValues(SystemConfig other) {
        return other != null
                && soSachToiDa == other.soSachToiDa
                && soNgayMuonToiDa == other.soNgayMuonToiDa
                && Double.compare(tienPhatNgay, other.tienPhatNgay) == 0
                && soLanGiaHanToiDa == other.soLanGiaHanToiDa
                && soNgayGiuSachDatTruoc == other.soNgayGiuSachDatTruoc;
    }
}
