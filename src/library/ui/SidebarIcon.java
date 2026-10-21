package library.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Icon vẽ bằng code (nét mảnh, bo đầu nét) cho các nút ở sidebar.
 *
 * Không dùng file ảnh nên không phụ thuộc thư mục resources, vẽ sắc nét ở mọi độ phân giải,
 * và luôn lấy đúng MÀU CHỮ của nút nên tự đổi theo giao diện sáng / tối và thành màu trắng khi nút đang Active.
 * Mỗi icon vẽ trong ô 18x18.
 */
public final class SidebarIcon implements Icon {

    public enum Kind {
        HOME, SEARCH, HISTORY, RESERVE, FINE,
        BORROW, CARD_CHECK, RETURN, RESERVE_MANAGE, FINE_MANAGE, STOCK, READERS,
        REPORT, ACCOUNT, CONFIG
    }

    private static final int SIZE = 18;

    private final Kind kind;

    public SidebarIcon(Kind kind) {
        this.kind = kind;
    }

    @Override public int getIconWidth() { return SIZE; }
    @Override public int getIconHeight() { return SIZE; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(c != null ? c.getForeground() : Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g2);
        } finally {
            g2.dispose();
        }
    }

    // ----- Các hàm vẽ nhỏ -----
    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void circle(Graphics2D g, double cx, double cy, double r) {
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private static void dot(Graphics2D g, double cx, double cy, double r) {
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    /** Đường nối các điểm (x0,y0,x1,y1,...); closed = khép kín. */
    private static void poly(Graphics2D g, boolean closed, double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) {
            path.lineTo(p[i], p[i + 1]);
        }
        if (closed) path.closePath();
        g.draw(path);
    }

    private void draw(Graphics2D g) {
        switch (kind) {
            case HOME:
                poly(g, false, 2, 9, 9, 2.5, 16, 9);                       // Mái
                poly(g, false, 4, 8, 4, 15.5, 14, 15.5, 14, 8);            // Thân nhà
                poly(g, false, 7.5, 15.5, 7.5, 11, 10.5, 11, 10.5, 15.5);  // Cửa
                break;

            case SEARCH:
                circle(g, 7.8, 7.8, 5);
                line(g, 11.6, 11.6, 15.6, 15.6);
                break;

            case HISTORY:                                                   // Đồng hồ
                circle(g, 9, 9, 6.6);
                poly(g, false, 9, 5, 9, 9, 12, 10.8);
                break;

            case RESERVE:                                                   // Dấu trang
                poly(g, true, 4.5, 2.5, 13.5, 2.5, 13.5, 15.5, 9, 12, 4.5, 15.5);
                break;

            case FINE:                                                      // Hóa đơn
                poly(g, true, 4, 2.5, 14, 2.5, 14, 15.5, 12, 14, 10, 15.5, 8, 14, 6, 15.5, 4, 14);
                line(g, 6.5, 6, 11.5, 6);
                line(g, 6.5, 9, 11.5, 9);
                line(g, 6.5, 12, 9, 12);
                break;

            case BORROW:                                                    // Trang giấy có dấu cộng
                poly(g, false, 11, 2, 4, 2, 4, 16, 14.5, 16, 14.5, 5.5, 11, 2, 11, 5.5, 14.5, 5.5);
                line(g, 6.8, 11, 12, 11);
                line(g, 9.4, 8.4, 9.4, 13.6);
                break;

            case CARD_CHECK:                                                // Thẻ
                g.draw(new RoundRectangle2D.Double(1.8, 4, 14.4, 10, 3, 3));
                circle(g, 6.2, 8.4, 1.6);
                g.draw(new java.awt.geom.Arc2D.Double(3.9, 10.2, 4.6, 4.4, 0, 180, java.awt.geom.Arc2D.OPEN));
                line(g, 10.4, 8, 14, 8);
                line(g, 10.4, 11, 13, 11);
                break;

            case RETURN:                                                    // Mũi tên xuống khay
                line(g, 9, 2.5, 9, 10.8);
                poly(g, false, 5.8, 7.8, 9, 11, 12.2, 7.8);
                poly(g, false, 3, 11.5, 3, 15.5, 15, 15.5, 15, 11.5);
                break;

            case RESERVE_MANAGE:                                            // Danh sách hàng chờ
                for (int i = 0; i < 3; i++) {
                    double yy = 4.5 + i * 4.5;
                    dot(g, 3.8, yy, 1.1);
                    line(g, 7, yy, 15.5, yy);
                }
                break;

            case FINE_MANAGE:                                               // Hóa đơn có dấu tích
                poly(g, true, 4, 2.5, 14, 2.5, 14, 15.5, 12, 14, 10, 15.5, 8, 14, 6, 15.5, 4, 14);
                poly(g, false, 6.4, 8.6, 8.4, 10.6, 11.8, 6.4);
                break;

            case STOCK:                                                     // Sách xếp trên kệ
                g.draw(new RoundRectangle2D.Double(2.8, 3, 3.2, 11.5, 1, 1));
                g.draw(new RoundRectangle2D.Double(7.2, 3, 3.2, 11.5, 1, 1));
                poly(g, true, 11.7, 4.6, 14.6, 3.8, 16.9, 13.6, 14, 14.4);
                line(g, 1.5, 16.5, 16.5, 16.5);
                break;

            case READERS:                                                   // Nhóm người
                circle(g, 7, 6, 2.6);
                g.draw(new java.awt.geom.QuadCurve2D.Double(2.5, 15, 2.5, 10.6, 7, 10.6));
                g.draw(new java.awt.geom.QuadCurve2D.Double(7, 10.6, 11.5, 10.6, 11.5, 15));
                circle(g, 12.6, 6.8, 2.1);
                g.draw(new java.awt.geom.QuadCurve2D.Double(12.8, 10.8, 15.8, 11, 15.8, 15));
                break;

            case REPORT:                                                    // Biểu đồ cột
                poly(g, false, 2.5, 2.5, 2.5, 15.5, 16, 15.5);
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                line(g, 6.2, 13, 6.2, 9.5);
                line(g, 9.8, 13, 9.8, 5.8);
                line(g, 13.4, 13, 13.4, 8);
                break;

            case ACCOUNT:                                                   // Một người
                circle(g, 9, 5.8, 3);
                g.draw(new java.awt.geom.QuadCurve2D.Double(3, 16, 3, 10.4, 9, 10.4));
                g.draw(new java.awt.geom.QuadCurve2D.Double(9, 10.4, 15, 10.4, 15, 16));
                break;

            case CONFIG:                                                    // Thanh trượt
                line(g, 2.5, 4.5, 15.5, 4.5);
                line(g, 2.5, 9, 15.5, 9);
                line(g, 2.5, 13.5, 15.5, 13.5);
                dot(g, 6, 4.5, 2.1);
                dot(g, 12, 9, 2.1);
                dot(g, 7.5, 13.5, 2.1);
                break;

            default:
                break;
        }
    }
}
