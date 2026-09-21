package library.model;

public class Book {
    private String maSach;
    private String tenSach;
    private String tacGia;
    private String theLoai;
    private int soLuong;

    public Book(String maSach, String tenSach, String tacGia,
                String theLoai, int soLuong) {
        this.maSach = maSach;
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.theLoai = theLoai;
        this.soLuong = soLuong;
    }

    public String getMaSach() {
        return maSach;
    }

    public String getTenSach() {
        return tenSach;
    }

    public String getTacGia() {
        return tacGia;
    }

    public String getTheLoai() {
        return theLoai;
    }

    public int getSoLuong() {
        return soLuong;
    }
}