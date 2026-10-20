package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Phần "data" của action ADD_BOOK_TITLE (API.md mục 3.3.1): { maDauSach }. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddBookResult {
    public int maDauSach;   // Mã đầu sách vừa tạo
}
