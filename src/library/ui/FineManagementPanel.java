package library.ui;

import library.model.FineListResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.Collator;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Quản lý phiếu phạt" của Thủ thư (UC-14): xem phiếu phạt của MỌI độc giả và xác nhận đã thu tiền.
 *
 * - Tải toàn bộ phiếu một lần (GET_FINES, Backend trả tất cả cho Thủ thư); tìm kiếm, lọc trạng thái và
 *   sắp xếp theo tiêu đề cột đều làm ở Client nên gõ đến đâu bảng lọc đến đó.
 * - Ô tìm kiếm khớp mã phiếu, mã thẻ, tên độc giả, tên sách, mã cuốn và lý do; không phân biệt hoa/thường
 *   và dấu tiếng Việt; nhiều từ khóa cách nhau bằng khoảng trắng thì phiếu phải khớp tất cả.
 * - Cột cuối có nút "Đã thu" cho phiếu chưa nộp (PAY_FINE), có hỏi xác nhận trước khi gửi.
 * - Việc LẬP phiếu phạt do luồng Trả sách (PROCESS_RETURN_BOOK) đảm nhiệm, màn hình này không lập phiếu.
 * - Mặc định: phiếu chưa nộp lên trước, trong cùng nhóm thì phiếu mới nhất trước.
 */
public class FineManagementPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã phiếu", "Độc giả", "Mã thẻ", "Tên sách", "Mã cuốn sách",
            "Ngày lập", "Lý do", "Số tiền phạt", "Trạng thái", "Thao tác"
    };
    private static final int COL_MA_PHIEU = 0;
    private static final int COL_DOC_GIA = 1;
    private static final int COL_MA_THE = 2;
    private static final int COL_TEN_SACH = 3;
    private static final int COL_MA_CUON = 4;
    private static final int COL_NGAY_LAP = 5;
    private static final int COL_LY_DO = 6;
    private static final int COL_SO_TIEN = 7;
    private static final int COL_TRANG_THAI = 8;
    private static final int COL_ACTION = 9; // Cột chứa nút "Đã thu" (không dùng để sắp xếp)

    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột "Tên sách" co giãn để nhận phần dư khi phóng to cửa sổ.
    private static final int[][] COLUMN_WIDTHS = {
            {60, 65, 80},                  // Mã phiếu
            {110, 125, 200},               // Độc giả
            {50, 55, 70},                  // Mã thẻ
            {110, 140, UiTable.FLEX},      // Tên sách
            {90, 95, 130},                 // Mã cuốn sách
            {85, 90, 110},                 // Ngày lập
            {110, 150, 280},               // Lý do
            {95, 100, 130},                // Số tiền phạt
            {80, 85, 105},                 // Trạng thái
            {80, 85, 105}                  // Thao tác
    };
    private static final int[] ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.CENTER,
            SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.CENTER,
            SwingConstants.LEFT, SwingConstants.RIGHT, SwingConstants.CENTER,
            SwingConstants.CENTER
    };

    private static final String FILTER_ALL = "Tất cả";
    private static final String FILTER_UNPAID = "Chưa nộp";
    private static final String FILTER_PAID = "Đã nộp";

    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color UNPAID_COLOR = new Color(239, 68, 68);   // Đỏ: còn nợ
    private static final Color PAID_COLOR = new Color(16, 185, 129);    // Xanh lá: đã nộp

    private final JTextField keywordField = new JTextField(30);
    private final JComboBox<String> filterBox = new JComboBox<>(new String[]{FILTER_ALL, FILTER_UNPAID, FILTER_PAID});
    private JTable table;
    private DefaultTableModel model;
    private JButton refreshButton;
    private JLabel totalLabel;
    private JLabel statusLabel;

    private final List<FineListResult.Item> all = new ArrayList<>();   // Toàn bộ phiếu đã tải
    private final List<FineListResult.Item> shown = new ArrayList<>(); // Phiếu đang hiện (sau lọc + sắp xếp)
    private double tongChuaThanhToan = 0;
    private boolean loaded = false;    // Đã tải thành công ít nhất một lần kể từ lúc đăng nhập
    private boolean loading = false;   // Đang tải danh sách
    private boolean paying = false;    // Đang gửi yêu cầu thu tiền
    private int latestRequestId = 0;   // Để bỏ qua kết quả của request đã cũ (ví dụ đăng xuất giữa chừng)

    private HeaderSorter sorter;

    private final Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));

    public FineManagementPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Quản lý phiếu phạt");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadFines());

        JPanel titleRow = new JPanel(new BorderLayout(10, 10));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(refreshButton, BorderLayout.EAST);

        // Hàng tìm kiếm: ô từ khóa, danh sách trạng thái nộp, nút Tìm kiếm
        keywordField.putClientProperty("JTextField.placeholderText",
                "Tìm theo mã phiếu, mã thẻ, độc giả, tên sách, mã cuốn, lý do");
        keywordField.addActionListener(e -> applyFilterAndSort()); // Enter trong ô từ khóa cũng tìm
        keywordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onKeywordChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onKeywordChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onKeywordChanged(); }
        });
        filterBox.addActionListener(e -> {
            if (loaded) applyFilterAndSort();
        });
        JButton searchButton = new JButton("Tìm kiếm");
        searchButton.addActionListener(e -> applyFilterAndSort());

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterRow.add(keywordField);
        filterRow.add(new JLabel("Hiển thị:"));
        filterRow.add(filterBox);
        filterRow.add(searchButton);

        totalLabel = new JLabel(" ");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        filterRow.setAlignmentX(LEFT_ALIGNMENT);
        totalLabel.setAlignmentX(LEFT_ALIGNMENT);
        north.add(titleRow);
        north.add(Box.createVerticalStrut(4));
        north.add(filterRow);
        north.add(totalLabel);
        add(north, BorderLayout.NORTH);

        // Bảng chỉ đọc: thao tác duy nhất là nút "Đã thu" ở cột cuối
        model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new UiTable(model);

        // Chữ đỏ cho phiếu chưa nộp; cột Trạng thái đậm chữ (dòng đang chọn giữ màu mặc định của giao diện)
        table.setDefaultRenderer(Object.class, new UiTable.CellRenderer(ALIGNMENT) {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                int modelColumn = t.convertColumnIndexToModel(column);
                if (!isSelected && row >= 0 && row < shown.size()) {
                    boolean paid = shown.get(row).daThanhToan;
                    if (modelColumn == COL_TRANG_THAI) {
                        c.setForeground(paid ? PAID_COLOR : UNPAID_COLOR);
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setForeground(paid ? t.getForeground() : UNPAID_COLOR);
                        c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    }
                }
                return c;
            }
        });

        // Cột "Thao tác": vẽ nút "Đã thu" trên từng dòng chưa nộp; việc bấm xử lý ở MouseListener của bảng
        table.getColumnModel().getColumn(COL_ACTION).setCellRenderer(new PayButtonRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = payableRowAt(e.getPoint());
                if (row >= 0) {
                    confirmAndPay(shown.get(row));
                }
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                table.setCursor(payableRowAt(e.getPoint()) >= 0
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });

        UiTable.setColumnWidths(table, COLUMN_WIDTHS);
        // Mặc định sắp theo Trạng thái tăng dần = chưa nộp trước (xem buildComparator)
        sorter = new HeaderSorter(table, COLUMN_NAMES, COL_TRANG_THAI, Set.of(COL_ACTION), this::applyFilterAndSort);

        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần màn hình được mở lại thì tải mới (phiếu có thể vừa được lập khi trả sách)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadFines();
            }
        });
    }

    /**
     * Xóa sạch dữ liệu đang hiển thị. MainFrame gọi khi đăng xuất để người đăng nhập sau
     * không nhìn thấy thông tin độc giả và phiếu phạt của phiên trước.
     */
    public void clear() {
        latestRequestId++; // Bỏ qua mọi request đang chạy dở
        all.clear();
        shown.clear();
        model.setRowCount(0);
        tongChuaThanhToan = 0;
        loaded = false;
        loading = false;
        paying = false;
        keywordField.setText("");
        filterBox.setSelectedIndex(0);
        refreshButton.setEnabled(true);
        refreshButton.setText("Làm mới");
        totalLabel.setText(" ");
        statusLabel.setText(" ");
    }

    // ==========================================================
    // TẢI DỮ LIỆU
    // ==========================================================
    private void loadFines() {
        if (loading || paying) return;
        setLoading(true);

        final int requestId = ++latestRequestId;

        SwingWorker<FineListResult, Void> worker = new SwingWorker<FineListResult, Void>() {
            @Override
            protected FineListResult doInBackground() throws Exception {
                return SocketClient.getInstance().getAllFines();
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất / có request mới hơn
                try {
                    FineListResult result = get();
                    all.clear();
                    if (result.items != null) all.addAll(result.items);
                    tongChuaThanhToan = result.tongChuaThanhToan;
                    loaded = true;
                    applyFilterAndSort();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được danh sách phiếu phạt.");
                    JOptionPane.showMessageDialog(FineManagementPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải phiếu phạt thất bại",
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
        refreshButton.setEnabled(!value && !paying);
        refreshButton.setText(value ? "Đang tải..." : "Làm mới");
        if (value) {
            statusLabel.setText("Đang tải dữ liệu từ máy chủ...");
        }
        table.repaint();
    }

    // ==========================================================
    // THU TIỀN PHẠT (PAY_FINE)
    // ==========================================================

    /** Trả về chỉ số dòng nếu điểm bấm nằm trên một nút "Đã thu" đang bật; ngược lại trả -1. */
    private int payableRowAt(Point point) {
        if (loading || paying) return -1;

        int viewRow = table.rowAtPoint(point);
        int viewColumn = table.columnAtPoint(point);
        if (viewRow < 0 || viewColumn < 0 || viewRow >= shown.size()) return -1;
        if (table.convertColumnIndexToModel(viewColumn) != COL_ACTION) return -1;

        return shown.get(viewRow).daThanhToan ? -1 : viewRow;
    }

    private void confirmAndPay(final FineListResult.Item fine) {
        if (loading || paying || fine.daThanhToan) return;

        // Thu tiền không hoàn tác được trên giao diện nên hỏi xác nhận, hiển thị đủ thông tin để đối chiếu
        int choice = JOptionPane.showOptionDialog(this,
                "Xác nhận đã thu tiền phạt của phiếu #" + fine.maPhieuPhat + "?\n\n"
                        + "Độc giả: " + nz(fine.tenDocGia) + " (thẻ " + fine.maThe + ")\n"
                        + "Sách: " + nz(fine.tenSach) + " (" + nz(fine.maCuonSach) + ")\n"
                        + "Lý do: " + nz(fine.lyDo) + "\n"
                        + "Số tiền: " + UiUtil.formatMoney(fine.soTienPhat),
                "Xác nhận thu tiền phạt", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new Object[]{"Đã thu", "Hủy"}, "Hủy");
        if (choice != 0) return; // 0 = nút "Đã thu"

        setPaying(true);
        final int requestId = latestRequestId;

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().payFine(fine.maPhieuPhat);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất giữa chừng

                String successMessage = null;
                String errorMessage = null;
                boolean connectionProblem = false;

                try {
                    successMessage = get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    connectionProblem = UiUtil.isConnectionProblem(cause);
                    errorMessage = UiUtil.messageOf(cause);
                }

                setPaying(false);

                if (successMessage != null) {
                    JOptionPane.showMessageDialog(FineManagementPanel.this,
                            successMessage + "\nPhiếu #" + fine.maPhieuPhat + " - "
                                    + UiUtil.formatMoney(fine.soTienPhat),
                            "Thu tiền phạt thành công", JOptionPane.INFORMATION_MESSAGE);
                } else if (errorMessage != null) {
                    JOptionPane.showMessageDialog(FineManagementPanel.this, errorMessage,
                            connectionProblem ? "Lỗi kết nối" : "Thu tiền phạt thất bại",
                            JOptionPane.ERROR_MESSAGE);
                }

                // Tải lại để cập nhật trạng thái và tổng nợ (kể cả khi lỗi, vì phiếu có thể đã được thu trước đó)
                loadFines();
            }
        };
        worker.execute();
    }

    private void setPaying(boolean value) {
        paying = value;
        refreshButton.setEnabled(!value && !loading);
        if (value) {
            statusLabel.setText("Đang ghi nhận thu tiền...");
        }
        table.repaint(); // Vẽ lại để các nút "Đã thu" bị khóa trong lúc đang gửi
    }

    /** Vẽ nút "Đã thu" trong ô (chỉ dòng chưa nộp); chỉ để hiển thị, việc bấm do MouseListener của bảng xử lý. */
    private class PayButtonRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JButton button = new JButton("Đã thu");

        PayButtonRenderer() {
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            button.setFocusable(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            panel.removeAll();
            boolean payable = row >= 0 && row < shown.size() && !shown.get(row).daThanhToan;
            if (payable) {
                button.setEnabled(!loading && !paying);
                panel.add(button, BorderLayout.CENTER);
            }
            panel.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            return panel;
        }
    }

    // ==========================================================
    // TÌM KIẾM + LỌC + SẮP XẾP (làm ở Client)
    // ==========================================================
    private void onKeywordChanged() {
        if (loaded) applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        String filter = String.valueOf(filterBox.getSelectedItem());
        String[] keywords = normalize(keywordField.getText()).trim().split("\\s+");

        shown.clear();
        for (FineListResult.Item it : all) {
            if (FILTER_UNPAID.equals(filter) && it.daThanhToan) continue;
            if (FILTER_PAID.equals(filter) && !it.daThanhToan) continue;
            if (!matches(it, keywords)) continue;
            shown.add(it);
        }
        shown.sort(buildComparator(sorter.getColumn(), sorter.isAscending()));

        model.setRowCount(0);
        for (FineListResult.Item it : shown) {
            model.addRow(new Object[]{
                    it.maPhieuPhat,
                    it.tenDocGia,
                    it.maThe,
                    it.tenSach,
                    it.maCuonSach,
                    UiUtil.formatDate(it.ngayLapPhieu),
                    it.lyDo,
                    UiUtil.formatMoney(it.soTienPhat),
                    it.daThanhToan ? "Đã nộp" : "Chưa nộp",
                    "Đã thu"
            });
        }
        showSummary();
    }

    /** Phiếu khớp khi MỌI từ khóa đều xuất hiện trong ít nhất một trong các trường tìm kiếm. */
    private static boolean matches(FineListResult.Item it, String[] keywords) {
        if (keywords.length == 0 || (keywords.length == 1 && keywords[0].isEmpty())) return true;

        String haystack = normalize(it.maPhieuPhat + " " + it.maThe + " " + nz(it.tenDocGia) + " "
                + nz(it.tenSach) + " " + nz(it.maCuonSach) + " " + nz(it.lyDo));
        for (String k : keywords) {
            if (!haystack.contains(k)) return false;
        }
        return true;
    }

    /** Chữ thường, bỏ dấu tiếng Việt ("Nguyễn" -> "nguyen", "đ" -> "d") để gõ không dấu vẫn tìm được. */
    private static String normalize(String s) {
        if (s == null) return "";
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    private Comparator<FineListResult.Item> buildComparator(int column, boolean asc) {
        Comparator<FineListResult.Item> primary;
        switch (column) {
            case COL_MA_PHIEU:
                primary = (a, b) -> Integer.compare(a.maPhieuPhat, b.maPhieuPhat);
                break;
            case COL_DOC_GIA:
                primary = (a, b) -> collator.compare(nz(a.tenDocGia), nz(b.tenDocGia));
                break;
            case COL_MA_THE:
                primary = (a, b) -> Integer.compare(a.maThe, b.maThe);
                break;
            case COL_TEN_SACH:
                primary = (a, b) -> collator.compare(nz(a.tenSach), nz(b.tenSach));
                break;
            case COL_MA_CUON:
                primary = (a, b) -> collator.compare(nz(a.maCuonSach), nz(b.maCuonSach));
                break;
            case COL_NGAY_LAP:
                // Định dạng ISO (yyyy-MM-ddTHH:mm:ss) so sánh chuỗi cũng đúng thứ tự thời gian
                primary = (a, b) -> nz(a.ngayLapPhieu).compareTo(nz(b.ngayLapPhieu));
                break;
            case COL_LY_DO:
                primary = (a, b) -> collator.compare(nz(a.lyDo), nz(b.lyDo));
                break;
            case COL_SO_TIEN:
                primary = (a, b) -> Double.compare(a.soTienPhat, b.soTienPhat);
                break;
            case COL_TRANG_THAI:
            default:
                // Tăng dần = Chưa nộp (false) trước, Đã nộp (true) sau
                primary = (a, b) -> Boolean.compare(a.daThanhToan, b.daThanhToan);
                break;
        }

        Comparator<FineListResult.Item> directed = asc ? primary : primary.reversed();

        // Bằng nhau thì phiếu mới lập xếp trước, rồi tới mã phiếu để thứ tự luôn ổn định
        Comparator<FineListResult.Item> tieBreak = (a, b) -> {
            int d = nz(b.ngayLapPhieu).compareTo(nz(a.ngayLapPhieu));
            return d != 0 ? d : Integer.compare(b.maPhieuPhat, a.maPhieuPhat);
        };
        return directed.thenComparing(tieBreak);
    }

    private void showSummary() {
        long unpaid = 0;
        for (FineListResult.Item it : all) {
            if (!it.daThanhToan) unpaid++;
        }

        if (tongChuaThanhToan > 0) {
            totalLabel.setText("Tổng tiền phạt chưa thu: " + UiUtil.formatMoney(tongChuaThanhToan)
                    + "  (" + unpaid + " phiếu)");
            totalLabel.setForeground(UNPAID_COLOR);
        } else {
            totalLabel.setText("Không có khoản phạt nào chưa thu.");
            totalLabel.setForeground(PAID_COLOR);
        }

        String time = " (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")";
        if (all.isEmpty()) {
            statusLabel.setText("Chưa có phiếu phạt nào." + time);
        } else if (shown.isEmpty()) {
            statusLabel.setText("Không có phiếu phạt phù hợp với điều kiện tìm kiếm." + time);
        } else {
            statusLabel.setText("Hiển thị " + shown.size() + "/" + all.size() + " phiếu phạt." + time);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
