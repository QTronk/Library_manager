package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Phần "data" của action UPDATE_CARD (API.md mục 3.7.1): { maThe, ngayCap, ngayHetHan, trangThai }. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardUpdateResult {
    public int maThe;
    public String ngayCap;     // yyyy-MM-dd
    public String ngayHetHan;  // yyyy-MM-dd
    public String trangThai;   // ACTIVE | LOCKED (trạng thái lưu trong CSDL)
}
