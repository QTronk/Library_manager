package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Một ĐẦU SÁCH, khớp JSON mà Backend trả về trong action SEARCH_BOOKS:
 * { maDauSach, tenSach, giaTien, tacGia, theLoai, nhaXuatBan, viTri, tongSoLuong, soLuongKhaDung }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Book {
    private int maDauSach;
    private String tenSach;
    private double giaTien;
    private String tacGia;
    private String theLoai;
    private String nhaXuatBan;
    private String viTri;          // Vị trí kệ
    private int tongSoLuong;       // Số cuốn hiện có (không tính cuốn mất)
    private int soLuongKhaDung;    // Số cuốn đang sẵn sàng cho mượn

    public Book() {}

    public Book(int maDauSach, String tenSach, String tacGia,
                String theLoai, String nhaXuatBan, double giaTien) {
        this.maDauSach = maDauSach;
        this.tenSach = tenSach;
        this.tacGia = tacGia;
        this.theLoai = theLoai;
        this.nhaXuatBan = nhaXuatBan;
        this.giaTien = giaTien;
    }

    public int getMaDauSach() { return maDauSach; }
    public void setMaDauSach(int maDauSach) { this.maDauSach = maDauSach; }

    public String getTenSach() { return tenSach; }
    public void setTenSach(String tenSach) { this.tenSach = tenSach; }

    public double getGiaTien() { return giaTien; }
    public void setGiaTien(double giaTien) { this.giaTien = giaTien; }

    public String getTacGia() { return tacGia; }
    public void setTacGia(String tacGia) { this.tacGia = tacGia; }

    public String getTheLoai() { return theLoai; }
    public void setTheLoai(String theLoai) { this.theLoai = theLoai; }

    public String getNhaXuatBan() { return nhaXuatBan; }
    public void setNhaXuatBan(String nhaXuatBan) { this.nhaXuatBan = nhaXuatBan; }

    public int getTongSoLuong() { return tongSoLuong; }
    public void setTongSoLuong(int tongSoLuong) { this.tongSoLuong = tongSoLuong; }

    public int getSoLuongKhaDung() { return soLuongKhaDung; }
    public void setSoLuongKhaDung(int soLuongKhaDung) { this.soLuongKhaDung = soLuongKhaDung; }

    public String getViTri() { return viTri; }
    public void setViTri(String viTri) { this.viTri = viTri; }
}
