package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Phần "data" của action ADD_READER (API.md mục 3.7.1): { maND, maThe }. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddReaderResult {
    public int maND;   // Mã người dùng (tài khoản) vừa tạo
    public int maThe;  // Mã thẻ độc giả vừa cấp
}
