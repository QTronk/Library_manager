package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần "data" của action GET_CATEGORIES (API.md mục 3.2.3): toàn bộ Tác giả, NXB, Thể loại để đổ vào ô chọn.
 * { tacGiaList: [ { maTG, tenTacGia } ], nhaXuatBanList: [ { maNXB, tenNXB, namXuatBan } ], theLoaiList: [ { maTL, tenTL } ] }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Categories {
    public List<Author> tacGiaList = new ArrayList<>();
    public List<Publisher> nhaXuatBanList = new ArrayList<>();
    public List<Category> theLoaiList = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        public int maTG;
        public String tenTacGia;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Publisher {
        public int maNXB;
        public String tenNXB;
        public Integer namXuatBan;   // Có thể null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Category {
        public int maTL;
        public String tenTL;
    }
}
