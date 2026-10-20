package library.ui;

import library.model.Categories;

import java.util.List;

/**
 * Một lựa chọn trong ô chọn Tác giả / Thể loại / Nhà xuất bản của các hộp thoại quản lý đầu sách.
 * id = null là lựa chọn "chưa chọn" (khi thêm) hoặc "giữ nguyên" (khi sửa).
 */
final class CatalogOption {
    final Integer id;
    final String label;

    CatalogOption(Integer id, String label) {
        this.id = id;
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    static CatalogOption[] authors(Categories c, String firstLabel) {
        CatalogOption[] result = new CatalogOption[c.tacGiaList.size() + 1];
        result[0] = new CatalogOption(null, firstLabel);
        for (int i = 0; i < c.tacGiaList.size(); i++) {
            Categories.Author a = c.tacGiaList.get(i);
            result[i + 1] = new CatalogOption(a.maTG, nz(a.tenTacGia));
        }
        return result;
    }

    static CatalogOption[] categories(Categories c, String firstLabel) {
        CatalogOption[] result = new CatalogOption[c.theLoaiList.size() + 1];
        result[0] = new CatalogOption(null, firstLabel);
        for (int i = 0; i < c.theLoaiList.size(); i++) {
            Categories.Category t = c.theLoaiList.get(i);
            result[i + 1] = new CatalogOption(t.maTL, nz(t.tenTL));
        }
        return result;
    }

    /** NXB hiển thị kèm năm (nếu có) vì có thể có hai NXB trùng tên khác năm. */
    static CatalogOption[] publishers(Categories c, String firstLabel) {
        CatalogOption[] result = new CatalogOption[c.nhaXuatBanList.size() + 1];
        result[0] = new CatalogOption(null, firstLabel);
        for (int i = 0; i < c.nhaXuatBanList.size(); i++) {
            Categories.Publisher p = c.nhaXuatBanList.get(i);
            result[i + 1] = new CatalogOption(p.maNXB, publisherLabel(p.tenNXB, p.namXuatBan));
        }
        return result;
    }

    static String publisherLabel(String name, Integer year) {
        return nz(name) + (year != null ? " (" + year + ")" : "");
    }

    /** Parse giá tiền người dùng gõ ("150.000", "150,000", "150000 đ"); không hợp lệ thì trả null. */
    static Double parseMoney(String text) {
        String digits = text == null ? "" : text.replaceAll("[\\s.,đĐ]", "");
        if (!digits.matches("\\d{1,12}")) return null;
        return Double.valueOf(digits);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
