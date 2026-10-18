package library.ui;

import library.model.BorrowHistoryResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.Collator;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Lịch sử mượn" của Độc giả (UC-08): danh sách sách ĐANG MƯỢN, chỉ để xem (read-only).
 *
 * - Mặc định sắp xếp theo Hạn trả gần nhất. Bấm tiêu đề cột để sắp xếp theo cột đó (làm ở phía Client,
 *   không gọi lại Backend): bấm lần đầu = tăng dần (▼), bấm lại cùng cột = giảm dần (▲).
 * - Dữ liệu được tải lại mỗi lần mở màn hình và khi bấm "Làm mới".
 */
public class BorrowHistoryPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã phiếu", "Tên sách", "Mã cuốn sách", "Ngày mượn",
            "Hạn trả", "Lần gia hạn", "Tình trạng", "Tiền phạt dự kiến"
    };
    private static final int COL_MA_PHIEU = 0;
    private static final int COL_TEN_SACH = 1;
    private static final int COL_MA_CUON = 2;
    private static final int COL_NGAY_MUON = 3;
    private static final int COL_HAN_TRA = 4;
    private static final int COL_GIA_HAN = 5;
    private static final int COL_TINH_TRANG = 6;
    private static final int COL_TIEN_PHAT = 7;

    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color OVERDUE_COLOR = new Color(239, 68, 68);

    private JTable table;
    private DefaultTableModel model;
    private JButton refreshButton;
    private JLabel statusLabel;

    private final List<Row> rows = new ArrayList<>(); // Thứ tự trong list = thứ tự hiển thị trên bảng
    private boolean loading = false;

    // Trạng thái sắp xếp hiện tại: mặc định Hạn trả, tăng dần (gần nhất trước)
    private int sortColumn = COL_HAN_TRA;
    private boolean ascending = true;

    private final NumberFormat priceFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
    private final Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN")); // Sắp chữ theo tiếng Việt

    public BorrowHistoryPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Lịch sử mượn");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadHistory());

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.WEST);
        top.add(refreshButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Bảng chỉ đọc: không cho sửa ô
        model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setRowHeight(26);
        table.setAutoCreateRowSorter(false); // Tự sắp xếp theo luật riêng bên dưới

        // Sách quá hạn hiển thị chữ đỏ (dòng đang được chọn giữ màu mặc định của giao diện)
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    boolean overdue = row >= 0 && row < rows.size() && rows.get(row).item.quaHan;
                    c.setForeground(overdue ? OVERDUE_COLOR : t.getForeground());
                }
                return c;
            }
        });

        // Bấm vào tiêu đề cột để sắp xếp
        final JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false); // Không cho kéo đổi chỗ cột (để chỉ số cột luôn cố định)
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || header.getResizingColumn() != null) return;
                int viewColumn = header.columnAtPoint(e.getPoint());
                if (viewColumn < 0) return;
                onHeaderClicked(table.convertColumnIndexToModel(viewColumn));
            }
        });
        updateHeaderLabels();

        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần màn hình được mở lại thì tải mới (phiếu mượn có thể vừa thay đổi ở quầy thủ thư)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadHistory();
            }
        });
    }

    /**
     * Xóa sạch dữ liệu đang hiển thị. MainFrame gọi khi đăng xuất để người dùng đăng nhập sau
     * không nhìn thấy sách của người trước.
     */
    public void clear() {
        rows.clear();
        model.setRowCount(0);
        statusLabel.setText(" ");
    }

    // ==========================================================
    // TẢI DỮ LIỆU
    // ==========================================================
    private void loadHistory() {
        if (loading) return; // Đang có 1 request chạy rồi

        setLoading(true);

        SwingWorker<List<BorrowHistoryResult.Item>, Void> worker = new SwingWorker<List<BorrowHistoryResult.Item>, Void>() {
            @Override
            protected List<BorrowHistoryResult.Item> doInBackground() throws Exception {
                return SocketClient.getInstance().getMyBorrowingBooks();
            }

            @Override
            protected void done() {
                try {
                    List<BorrowHistoryResult.Item> items = get();

                    rows.clear();
                    for (BorrowHistoryResult.Item item : items) {
                        rows.add(new Row(item));
                    }
                    applySort();
                    showSummary();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    boolean connectionProblem = cause instanceof IOException;
                    String message = cause.getMessage() != null && !cause.getMessage().trim().isEmpty()
                            ? cause.getMessage()
                            : "Đã xảy ra lỗi không xác định.";

                    statusLabel.setText("Không tải được lịch sử mượn.");
                    JOptionPane.showMessageDialog(BorrowHistoryPanel.this, message,
                            connectionProblem ? "Lỗi kết nối" : "Tải lịch sử thất bại",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setLoading(false);
                }
            }
        };
        worker.execute();
    }

    private void setLoading(boolean value) {
        loading = value;
        refreshButton.setEnabled(!value);
        refreshButton.setText(value ? "Đang tải..." : "Làm mới");
        if (value) {
            statusLabel.setText("Đang tải dữ liệu từ máy chủ...");
        }
    }

    private void showSummary() {
        if (rows.isEmpty()) {
            statusLabel.setText("Bạn chưa mượn cuốn sách nào. (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")");
            return;
        }
        int overdue = 0;
        for (Row r : rows) {
            if (r.item.quaHan) overdue++;
        }
        String text = "Đang mượn " + rows.size() + " cuốn";
        if (overdue > 0) {
            text += ", trong đó " + overdue + " cuốn quá hạn";
        }
        statusLabel.setText(text + ". (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")");
    }

    // ==========================================================
    // SẮP XẾP (làm ở Client)
    // ==========================================================
    private void onHeaderClicked(int column) {
        if (column == sortColumn) {
            ascending = !ascending;   // Bấm lại cùng cột -> đảo chiều
        } else {
            sortColumn = column;      // Cột mới -> bắt đầu từ tăng dần
            ascending = true;
        }
        applySort();
    }

    private void applySort() {
        rows.sort(buildComparator(sortColumn, ascending));
        fillTable();
        updateHeaderLabels();
    }

    /** Gắn mũi tên vào tiêu đề cột đang sắp xếp: ▼ = tăng dần, ▲ = giảm dần. */
    private void updateHeaderLabels() {
        TableColumnModel columnModel = table.getColumnModel();
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn column = columnModel.getColumn(i);
            int modelIndex = column.getModelIndex();
            String text = COLUMN_NAMES[modelIndex];
            if (modelIndex == sortColumn) {
                text += ascending ? "  \u25BC" : "  \u25B2";
            }
            column.setHeaderValue(text);
        }
        table.getTableHeader().repaint();
    }

    private Comparator<Row> buildComparator(int column, boolean asc) {
        Comparator<Row> primary;
        switch (column) {
            case COL_MA_PHIEU:
                primary = (a, b) -> Integer.compare(a.item.maPhieuMuon, b.item.maPhieuMuon);
                break;
            case COL_TEN_SACH:
                primary = (a, b) -> collator.compare(nz(a.item.tenSach), nz(b.item.tenSach));
                break;
            case COL_MA_CUON:
                primary = (a, b) -> collator.compare(nz(a.item.maCuonSach), nz(b.item.maCuonSach));
                break;
            case COL_NGAY_MUON:
                // Định dạng ISO (yyyy-MM-ddTHH:mm:ss) so sánh chuỗi cũng đúng thứ tự thời gian
                primary = (a, b) -> nz(a.item.ngayMuon).compareTo(nz(b.item.ngayMuon));
                break;
            case COL_GIA_HAN:
                primary = (a, b) -> Integer.compare(a.item.soLanGiaHan, b.item.soLanGiaHan);
                break;
            case COL_TINH_TRANG:
                // Quá hạn nhiều ngày nhất -> ít ngày còn lại nhất -> còn nhiều ngày nhất
                primary = (a, b) -> Long.compare(a.statusKey, b.statusKey);
                break;
            case COL_TIEN_PHAT:
                primary = (a, b) -> Double.compare(a.item.tienPhatDuKien, b.item.tienPhatDuKien);
                break;
            case COL_HAN_TRA:
            default:
                primary = (a, b) -> nz(a.item.ngayHenTra).compareTo(nz(b.item.ngayHenTra));
                break;
        }

        final Comparator<Row> directed = asc ? primary : primary.reversed();

        // Bằng nhau thì xếp theo Hạn trả (gần trước) rồi tới mã chi tiết để thứ tự luôn ổn định
        final Comparator<Row> tieBreak = (a, b) -> {
            int d = nz(a.item.ngayHenTra).compareTo(nz(b.item.ngayHenTra));
            return d != 0 ? d : Integer.compare(a.item.maCTPM, b.item.maCTPM);
        };
        return directed.thenComparing(tieBreak);
    }

    private void fillTable() {
        model.setRowCount(0);
        for (Row r : rows) {
            BorrowHistoryResult.Item it = r.item;
            model.addRow(new Object[]{
                    it.maPhieuMuon,
                    it.tenSach,
                    it.maCuonSach,
                    formatDate(it.ngayMuon),
                    formatDate(it.ngayHenTra),
                    it.soLanGiaHan,
                    r.statusText,
                    priceFormat.format(it.tienPhatDuKien) + " đ"
            });
        }
    }

    // ==========================================================
    // TIỆN ÍCH
    // ==========================================================
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Đọc ngày từ chuỗi ISO (chấp nhận cả dạng có giờ "yyyy-MM-ddTHH:mm:ss"); sai định dạng thì trả null. */
    private static LocalDate parseDate(String s) {
        if (s == null || s.length() < 10) return null;
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String formatDate(String s) {
        LocalDate d = parseDate(s);
        if (d != null) return d.format(DATE_VN);
        return s == null ? "" : s;
    }

    /** Một dòng trên bảng: dữ liệu từ Backend cộng thêm vài giá trị đã tính sẵn để hiển thị và sắp xếp. */
    private static class Row {
        final BorrowHistoryResult.Item item;
        final long statusKey;      // < 0: quá hạn (âm số ngày trễ); >= 0: số ngày còn lại
        final String statusText;

        Row(BorrowHistoryResult.Item item) {
            this.item = item;

            if (item.quaHan) {
                // Tin theo Backend về chuyện quá hạn (tránh lệch do đồng hồ máy khách)
                statusKey = -item.soNgayTre;
                statusText = "Quá hạn " + item.soNgayTre + " ngày";
            } else {
                LocalDate han = parseDate(item.ngayHenTra);
                if (han == null) {
                    statusKey = Long.MAX_VALUE;
                    statusText = "";
                } else {
                    long left = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), han));
                    statusKey = left;
                    statusText = left == 0 ? "Đến hạn hôm nay" : "Còn " + left + " ngày";
                }
            }
        }
    }
}
