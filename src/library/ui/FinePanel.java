package library.ui;

import library.model.FineListResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.Collator;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Phiếu phạt" của Độc giả (UC-14, chỉ xem): danh sách phiếu phạt của chính mình.
 * Độc giả không có quyền lập hay thu tiền phạt; việc đó do Thủ thư làm tại quầy.
 *
 * - Backend tự lấy thẻ từ phiên đăng nhập nên Client không gửi maThe.
 * - Tải toàn bộ phiếu một lần; ô lọc (Tất cả / Chưa nộp / Đã nộp) và sắp xếp theo tiêu đề cột làm ở Client.
 * - Mặc định: phiếu chưa nộp lên trước, trong cùng nhóm thì phiếu mới nhất trước.
 * - Dữ liệu được tải lại mỗi lần mở màn hình và khi bấm "Làm mới".
 */
public class FinePanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã phiếu", "Tên sách", "Mã cuốn sách", "Ngày lập", "Lý do", "Số tiền phạt", "Trạng thái"
    };
    private static final int COL_MA_PHIEU = 0;
    private static final int COL_TEN_SACH = 1;
    private static final int COL_MA_CUON = 2;
    private static final int COL_NGAY_LAP = 3;
    private static final int COL_LY_DO = 4;
    private static final int COL_SO_TIEN = 5;
    private static final int COL_TRANG_THAI = 6;

    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột "Tên sách" co giãn để nhận phần dư khi phóng to cửa sổ.
    private static final int[][] COLUMN_WIDTHS = {
            {70, 75, 90},                  // Mã phiếu
            {140, 200, UiTable.FLEX},      // Tên sách
            {100, 105, 150},               // Mã cuốn sách
            {95, 95, 120},                 // Ngày lập
            {160, 220, 360},               // Lý do
            {110, 115, 150},               // Số tiền phạt
            {95, 100, 130}                 // Trạng thái
    };
    private static final int[] ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.LEFT,
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.RIGHT,
            SwingConstants.CENTER
    };

    private static final String FILTER_ALL = "Tất cả";
    private static final String FILTER_UNPAID = "Chưa nộp";
    private static final String FILTER_PAID = "Đã nộp";

    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color UNPAID_COLOR = new Color(239, 68, 68);   // Đỏ: còn nợ
    private static final Color PAID_COLOR = new Color(16, 185, 129);    // Xanh lá: đã nộp

    private JTable table;
    private DefaultTableModel model;
    private JButton refreshButton;
    private JComboBox<String> filterBox;
    private JLabel totalLabel;
    private JLabel statusLabel;

    private final List<FineListResult.Item> all = new ArrayList<>();   // Toàn bộ phiếu đã tải
    private final List<FineListResult.Item> shown = new ArrayList<>(); // Phiếu đang hiện (sau lọc + sắp xếp)
    private double tongChuaThanhToan = 0;
    private boolean loaded = false;   // Đã tải thành công ít nhất một lần kể từ lúc đăng nhập
    private boolean loading = false;

    private HeaderSorter sorter;

    private final Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));

    public FinePanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Phiếu phạt của tôi");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        filterBox = new JComboBox<>(new String[]{FILTER_ALL, FILTER_UNPAID, FILTER_PAID});
        filterBox.addActionListener(e -> {
            if (loaded) applyFilterAndSort();
        });

        refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadFines());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(new JLabel("Hiển thị:"));
        actions.add(filterBox);
        actions.add(refreshButton);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        totalLabel = new JLabel(" ");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.add(top, BorderLayout.NORTH);
        north.add(totalLabel, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // Bảng chỉ đọc: Độc giả chỉ được xem
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

        UiTable.setColumnWidths(table, COLUMN_WIDTHS);
        // Mặc định sắp theo Trạng thái tăng dần = chưa nộp trước (xem buildComparator)
        sorter = new HeaderSorter(table, COLUMN_NAMES, COL_TRANG_THAI, Set.of(), this::applyFilterAndSort);

        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần màn hình được mở lại thì tải mới (Thủ thư có thể vừa lập phiếu hoặc thu tiền ở quầy)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadFines();
            }
        });
    }

    /**
     * Xóa sạch dữ liệu đang hiển thị. MainFrame gọi khi đăng xuất để người đăng nhập sau
     * không nhìn thấy phiếu phạt của người trước.
     */
    public void clear() {
        all.clear();
        shown.clear();
        model.setRowCount(0);
        tongChuaThanhToan = 0;
        loaded = false;
        filterBox.setSelectedIndex(0);
        totalLabel.setText(" ");
        statusLabel.setText(" ");
    }

    // ==========================================================
    // TẢI DỮ LIỆU
    // ==========================================================
    private void loadFines() {
        if (loading) return;
        setLoading(true);

        SwingWorker<FineListResult, Void> worker = new SwingWorker<FineListResult, Void>() {
            @Override
            protected FineListResult doInBackground() throws Exception {
                return SocketClient.getInstance().getMyFines();
            }

            @Override
            protected void done() {
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
                    JOptionPane.showMessageDialog(FinePanel.this, UiUtil.messageOf(cause),
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
        refreshButton.setEnabled(!value);
        refreshButton.setText(value ? "Đang tải..." : "Làm mới");
        if (value) {
            statusLabel.setText("Đang tải dữ liệu từ máy chủ...");
        }
    }

    // ==========================================================
    // LỌC + SẮP XẾP (làm ở Client)
    // ==========================================================
    private void applyFilterAndSort() {
        String filter = String.valueOf(filterBox.getSelectedItem());

        shown.clear();
        for (FineListResult.Item it : all) {
            if (FILTER_UNPAID.equals(filter) && it.daThanhToan) continue;
            if (FILTER_PAID.equals(filter) && !it.daThanhToan) continue;
            shown.add(it);
        }
        shown.sort(buildComparator(sorter.getColumn(), sorter.isAscending()));

        model.setRowCount(0);
        for (FineListResult.Item it : shown) {
            model.addRow(new Object[]{
                    it.maPhieuPhat,
                    it.tenSach,
                    it.maCuonSach,
                    UiUtil.formatDate(it.ngayLapPhieu),
                    it.lyDo,
                    UiUtil.formatMoney(it.soTienPhat),
                    it.daThanhToan ? "Đã nộp" : "Chưa nộp"
            });
        }
        showSummary();
    }

    private Comparator<FineListResult.Item> buildComparator(int column, boolean asc) {
        Comparator<FineListResult.Item> primary;
        switch (column) {
            case COL_MA_PHIEU:
                primary = (a, b) -> Integer.compare(a.maPhieuPhat, b.maPhieuPhat);
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
            totalLabel.setText("Tổng tiền phạt chưa nộp: " + UiUtil.formatMoney(tongChuaThanhToan)
                    + "  (" + unpaid + " phiếu). Vui lòng đến quầy thủ thư để nộp phạt.");
            totalLabel.setForeground(UNPAID_COLOR);
        } else {
            totalLabel.setText("Bạn không có khoản phạt nào chưa nộp.");
            totalLabel.setForeground(PAID_COLOR);
        }

        String time = " (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")";
        if (all.isEmpty()) {
            statusLabel.setText("Bạn chưa có phiếu phạt nào." + time);
        } else if (shown.isEmpty()) {
            statusLabel.setText("Không có phiếu phạt nào ở mục \"" + filterBox.getSelectedItem() + "\"." + time);
        } else {
            statusLabel.setText("Hiển thị " + shown.size() + "/" + all.size() + " phiếu phạt." + time);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
