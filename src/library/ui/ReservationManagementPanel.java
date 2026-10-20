package library.ui;

import library.model.AssignReservationResult;
import library.model.WaitingReservationResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Quản lý đặt trước" của Thủ thư (UC-07, giai đoạn 2 "Khóa mục tiêu sách").
 *
 * - Xem lượt đặt trước của MỌI độc giả theo trạng thái (mặc định "Đang chờ"), sắp theo ngày đặt tăng dần
 *   = đúng thứ tự hàng chờ. Đổi trạng thái ở danh sách chọn thì tải lại từ máy chủ (GET_WAITING_RESERVATIONS).
 * - Ô tìm kiếm lọc ở Client (mã đặt, mã thẻ, độc giả, tên sách; không phân biệt hoa/thường và dấu).
 * - Với lượt "Đang chờ", cột cuối có nút "Khóa sách" (ASSIGN_RESERVATION): khóa cuốn đang được mượn có hạn trả gần nhất
 *   (cột "Cuốn gợi ý") cho độc giả, hoặc giữ thẳng tại quầy nếu đầu sách đã có cuốn sẵn sàng. Khi cuốn đó được trả,
 *   luồng Trả sách tự báo cho độc giả (UC-10).
 * - Backend bắt buộc xử lý lượt cũ hơn của cùng một đầu sách trước, nên chỉ lượt cũ nhất của mỗi đầu sách có nút bật.
 */
public class ReservationManagementPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã đặt", "Độc giả", "Mã thẻ", "Tên sách", "Ngày đặt",
            "Trạng thái", "Cuốn gợi ý", "Hẹn trả", "Thao tác"
    };
    private static final int COL_MA_DAT = 0;
    private static final int COL_DOC_GIA = 1;
    private static final int COL_MA_THE = 2;
    private static final int COL_TEN_SACH = 3;
    private static final int COL_NGAY_DAT = 4;
    private static final int COL_TRANG_THAI = 5;
    private static final int COL_CUON_GOI_Y = 6;
    private static final int COL_HEN_TRA = 7;
    private static final int COL_ACTION = 8; // Cột chứa nút "Khóa sách" (không dùng để sắp xếp)

    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột "Tên sách" co giãn để nhận phần dư khi phóng to cửa sổ.
    private static final int[][] COLUMN_WIDTHS = {
            {60, 65, 80},                  // Mã đặt
            {110, 135, 200},               // Độc giả
            {50, 55, 70},                  // Mã thẻ
            {110, 150, UiTable.FLEX},      // Tên sách
            {95, 125, 140},                // Ngày đặt
            {95, 105, 130},                // Trạng thái
            {90, 95, 130},                 // Cuốn gợi ý
            {85, 90, 110},                 // Hẹn trả
            {120, 125, 150}                // Thao tác (đủ rộng để nút "Khóa sách" không bị cắt chữ)
    };
    private static final int[] ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.CENTER,
            SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.CENTER,
            SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.CENTER
    };

    // Danh sách chọn trạng thái: nhãn hiển thị và giá trị gửi Backend
    private static final String[] STATUS_LABELS = {
            "Đang chờ", "Đã giữ sách", "Đã báo sách về", "Đã nhận sách", "Đã hủy"
    };
    private static final String[] STATUS_VALUES = {"WAITING", "RESERVED", "NOTIFIED", "COMPLETED", "CANCELLED"};

    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color WAITING_COLOR = new Color(217, 119, 6);   // Cam: đang chờ xử lý
    private static final Color DONE_COLOR = new Color(16, 185, 129);     // Xanh lá: đã giữ / đã báo
    private static final Color CANCELLED_COLOR = new Color(239, 68, 68); // Đỏ: đã hủy

    private final JTextField keywordField = new JTextField(30);
    private final JComboBox<String> statusBox = new JComboBox<>(STATUS_LABELS);
    private JTable table;
    private DefaultTableModel model;
    private JButton refreshButton;
    private JLabel summaryLabel;
    private JLabel statusLabel;

    private final List<WaitingReservationResult.Item> all = new ArrayList<>();   // Lượt đã tải theo trạng thái đang chọn
    private final List<WaitingReservationResult.Item> shown = new ArrayList<>(); // Lượt đang hiện (sau lọc + sắp xếp)
    private final Set<Integer> assignable = new java.util.HashSet<>();           // Mã đặt được phép khóa (cũ nhất của đầu sách)
    private boolean loaded = false;    // Đã tải thành công ít nhất một lần kể từ lúc đăng nhập
    private boolean loading = false;   // Đang tải danh sách
    private boolean assigning = false; // Đang gửi yêu cầu khóa sách
    private int latestRequestId = 0;   // Để bỏ qua kết quả của request đã cũ (ví dụ đăng xuất giữa chừng)

    private HeaderSorter sorter;

    private final Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));

    public ReservationManagementPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Quản lý đặt trước");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadReservations());

        JPanel titleRow = new JPanel(new BorderLayout(10, 10));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(refreshButton, BorderLayout.EAST);

        // Hàng tìm kiếm: ô từ khóa, danh sách trạng thái, nút Tìm kiếm
        keywordField.putClientProperty("JTextField.placeholderText", "Tìm theo mã đặt, mã thẻ, độc giả, tên sách");
        keywordField.addActionListener(e -> applyFilterAndSort());
        keywordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onKeywordChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onKeywordChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onKeywordChanged(); }
        });
        statusBox.addActionListener(e -> {
            if (loaded) loadReservations(); // Trạng thái do máy chủ lọc nên đổi là phải tải lại
        });
        JButton searchButton = new JButton("Tìm kiếm");
        searchButton.addActionListener(e -> applyFilterAndSort());

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterRow.add(keywordField);
        filterRow.add(new JLabel("Trạng thái:"));
        filterRow.add(statusBox);
        filterRow.add(searchButton);

        summaryLabel = new JLabel(" ");
        summaryLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        filterRow.setAlignmentX(LEFT_ALIGNMENT);
        summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
        north.add(titleRow);
        north.add(Box.createVerticalStrut(4));
        north.add(filterRow);
        north.add(summaryLabel);
        add(north, BorderLayout.NORTH);

        // Bảng chỉ đọc: thao tác duy nhất là nút "Khóa sách" ở cột cuối
        model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new UiTable(model);

        // Cột Trạng thái tô màu theo trạng thái (dòng đang chọn giữ màu mặc định của giao diện)
        table.setDefaultRenderer(Object.class, new UiTable.CellRenderer(ALIGNMENT) {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                int modelColumn = t.convertColumnIndexToModel(column);
                if (!isSelected) {
                    c.setForeground(t.getForeground());
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                    if (modelColumn == COL_TRANG_THAI && row >= 0 && row < shown.size()) {
                        c.setForeground(statusColor(shown.get(row).trangThai));
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    }
                }
                return c;
            }
        });

        // Cột "Thao tác": vẽ nút "Khóa sách" trên từng lượt đang chờ; việc bấm xử lý ở MouseListener của bảng
        table.getColumnModel().getColumn(COL_ACTION).setCellRenderer(new AssignButtonRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = assignableRowAt(e.getPoint());
                if (row >= 0) {
                    confirmAndAssign(shown.get(row));
                }
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                table.setCursor(assignableRowAt(e.getPoint()) >= 0
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });

        UiTable.setColumnWidths(table, COLUMN_WIDTHS);
        // Mặc định sắp theo Ngày đặt tăng dần = đúng thứ tự hàng chờ (người đặt trước đứng trước)
        sorter = new HeaderSorter(table, COLUMN_NAMES, COL_NGAY_DAT, Set.of(COL_ACTION), this::applyFilterAndSort);

        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần màn hình được mở lại thì tải mới (độc giả có thể vừa đặt trước, sách có thể vừa được trả)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadReservations();
            }
        });
    }

    /**
     * Xóa sạch dữ liệu đang hiển thị. MainFrame gọi khi đăng xuất để người đăng nhập sau
     * không nhìn thấy thông tin độc giả của phiên trước.
     */
    public void clear() {
        latestRequestId++; // Bỏ qua mọi request đang chạy dở
        all.clear();
        shown.clear();
        assignable.clear();
        model.setRowCount(0);
        loaded = false;
        loading = false;
        assigning = false;
        keywordField.setText("");
        statusBox.setSelectedIndex(0);
        refreshButton.setEnabled(true);
        refreshButton.setText("Làm mới");
        summaryLabel.setText(" ");
        statusLabel.setText(" ");
    }

    // ==========================================================
    // TẢI DỮ LIỆU
    // ==========================================================
    private String selectedStatus() {
        int i = statusBox.getSelectedIndex();
        return STATUS_VALUES[i < 0 ? 0 : i];
    }

    private void loadReservations() {
        if (loading || assigning) return;
        setLoading(true);

        final int requestId = ++latestRequestId;
        final String status = selectedStatus();

        SwingWorker<List<WaitingReservationResult.Item>, Void> worker =
                new SwingWorker<List<WaitingReservationResult.Item>, Void>() {
            @Override
            protected List<WaitingReservationResult.Item> doInBackground() throws Exception {
                return SocketClient.getInstance().getWaitingReservations(status);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất / có request mới hơn
                try {
                    List<WaitingReservationResult.Item> items = get();
                    all.clear();
                    all.addAll(items);
                    computeAssignable();
                    loaded = true;
                    applyFilterAndSort();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được danh sách đặt trước.");
                    JOptionPane.showMessageDialog(ReservationManagementPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải danh sách đặt trước thất bại",
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
        refreshButton.setEnabled(!value && !assigning);
        refreshButton.setText(value ? "Đang tải..." : "Làm mới");
        if (value) {
            statusLabel.setText("Đang tải dữ liệu từ máy chủ...");
        }
        table.repaint();
    }

    /**
     * Backend bắt buộc xử lý lượt cũ hơn của cùng đầu sách trước, nên chỉ lượt WAITING cũ nhất
     * (theo ngày đặt, hòa thì theo mã đặt) của mỗi đầu sách được phép bấm "Khóa sách".
     */
    private void computeAssignable() {
        assignable.clear();
        Map<Integer, WaitingReservationResult.Item> oldest = new HashMap<>();
        for (WaitingReservationResult.Item it : all) {
            if (!"WAITING".equals(it.trangThai)) continue;
            WaitingReservationResult.Item cur = oldest.get(it.maDauSach);
            if (cur == null || compareQueue(it, cur) < 0) oldest.put(it.maDauSach, it);
        }
        for (WaitingReservationResult.Item it : oldest.values()) assignable.add(it.maDatTruoc);
    }

    private static int compareQueue(WaitingReservationResult.Item a, WaitingReservationResult.Item b) {
        int d = nz(a.ngayDat).compareTo(nz(b.ngayDat)); // Định dạng ISO nên so chuỗi cũng đúng thứ tự thời gian
        return d != 0 ? d : Integer.compare(a.maDatTruoc, b.maDatTruoc);
    }

    // ==========================================================
    // KHÓA SÁCH CHO LƯỢT ĐẶT (ASSIGN_RESERVATION)
    // ==========================================================

    /** Trả về chỉ số dòng nếu điểm bấm nằm trên một nút "Khóa sách" đang bật; ngược lại trả -1. */
    private int assignableRowAt(Point point) {
        if (loading || assigning) return -1;

        int viewRow = table.rowAtPoint(point);
        int viewColumn = table.columnAtPoint(point);
        if (viewRow < 0 || viewColumn < 0 || viewRow >= shown.size()) return -1;
        if (table.convertColumnIndexToModel(viewColumn) != COL_ACTION) return -1;

        return assignable.contains(shown.get(viewRow).maDatTruoc) ? viewRow : -1;
    }

    private void confirmAndAssign(final WaitingReservationResult.Item r) {
        if (loading || assigning) return;

        WaitingReservationResult.CuonGoiY g = r.cuonSachGoiY;
        String goiY = g != null
                ? "Cuốn sẽ khóa: " + nz(g.maCuonSach) + " (đang được mượn, hẹn trả " + UiUtil.formatDate(g.ngayHenTra) + ")"
                : "Đầu sách chưa có cuốn nào đang được mượn: hệ thống sẽ giữ thẳng cuốn đã sẵn sàng (nếu có)\nhoặc báo lỗi.";

        int choice = JOptionPane.showOptionDialog(this,
                "Khóa sách cho lượt đặt trước #" + r.maDatTruoc + "?\n\n"
                        + "Độc giả: " + nz(r.tenDocGia) + " (thẻ " + r.maThe + ")\n"
                        + "Sách: " + nz(r.tenSach) + "\n"
                        + goiY + "\n\n"
                        + "Cuốn được khóa sẽ không gia hạn được nữa; khi được trả, độc giả sẽ được báo đến nhận.",
                "Xác nhận khóa sách", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new Object[]{"Khóa sách", "Hủy"}, "Hủy");
        if (choice != 0) return; // 0 = nút "Khóa sách"

        setAssigning(true);
        final int requestId = latestRequestId;

        SwingWorker<AssignReservationResult, Void> worker = new SwingWorker<AssignReservationResult, Void>() {
            @Override
            protected AssignReservationResult doInBackground() throws Exception {
                return SocketClient.getInstance().assignReservation(r.maDatTruoc);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất giữa chừng

                AssignReservationResult result = null;
                String errorMessage = null;
                boolean connectionProblem = false;

                try {
                    result = get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    connectionProblem = UiUtil.isConnectionProblem(cause);
                    errorMessage = UiUtil.messageOf(cause);
                }

                setAssigning(false);

                if (result != null) {
                    StringBuilder text = new StringBuilder(result.message != null ? result.message : "Đã khóa cuốn sách");
                    text.append("\n\nLượt đặt #").append(result.maDatTruoc)
                        .append(" - Độc giả: ").append(nz(r.tenDocGia))
                        .append("\nCuốn sách: ").append(nz(result.maCuonSach));
                    if (result.ngayHenTraCuaCuon != null) {
                        text.append(" (hẹn trả ").append(UiUtil.formatDate(result.ngayHenTraCuaCuon)).append(")");
                    } else {
                        text.append("\nCuốn đã sẵn sàng: hãy cất ở quầy chờ độc giả đến nhận.");
                    }
                    JOptionPane.showMessageDialog(ReservationManagementPanel.this, text.toString(),
                            "Khóa sách thành công", JOptionPane.INFORMATION_MESSAGE);
                } else if (errorMessage != null) {
                    JOptionPane.showMessageDialog(ReservationManagementPanel.this, errorMessage,
                            connectionProblem ? "Lỗi kết nối" : "Khóa sách thất bại", JOptionPane.ERROR_MESSAGE);
                }

                // Tải lại để cập nhật danh sách (kể cả khi lỗi, vì lượt có thể đã được xử lý hoặc bị hủy trước đó)
                loadReservations();
            }
        };
        worker.execute();
    }

    private void setAssigning(boolean value) {
        assigning = value;
        refreshButton.setEnabled(!value && !loading);
        if (value) {
            statusLabel.setText("Đang khóa sách...");
        }
        table.repaint(); // Vẽ lại để các nút "Khóa sách" bị khóa trong lúc đang gửi
    }

    /** Vẽ nút "Khóa sách" trong ô (chỉ lượt đang chờ); chỉ để hiển thị, việc bấm do MouseListener của bảng xử lý. */
    private class AssignButtonRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JButton button = new JButton("Khóa sách");

        AssignButtonRenderer() {
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            button.setFocusable(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            panel.removeAll();
            if (row >= 0 && row < shown.size() && "WAITING".equals(shown.get(row).trangThai)) {
                // Lượt chưa tới lượt (còn lượt cũ hơn của cùng đầu sách) vẫn hiện nút nhưng bị khóa
                button.setEnabled(!loading && !assigning && assignable.contains(shown.get(row).maDatTruoc));
                panel.add(button, BorderLayout.CENTER);
            }
            panel.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            return panel;
        }
    }

    // ==========================================================
    // TÌM KIẾM + SẮP XẾP (làm ở Client)
    // ==========================================================
    private void onKeywordChanged() {
        if (loaded) applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        String[] keywords = normalize(keywordField.getText()).trim().split("\\s+");

        shown.clear();
        for (WaitingReservationResult.Item it : all) {
            if (matches(it, keywords)) shown.add(it);
        }
        shown.sort(buildComparator(sorter.getColumn(), sorter.isAscending()));

        model.setRowCount(0);
        for (WaitingReservationResult.Item it : shown) {
            WaitingReservationResult.CuonGoiY g = it.cuonSachGoiY;
            model.addRow(new Object[]{
                    it.maDatTruoc,
                    it.tenDocGia,
                    it.maThe,
                    it.tenSach,
                    formatDateTime(it.ngayDat),
                    statusText(it.trangThai),
                    g != null ? nz(g.maCuonSach) : "",
                    g != null ? UiUtil.formatDate(g.ngayHenTra) : "",
                    "Khóa sách"
            });
        }
        showSummary();
    }

    /** Lượt khớp khi MỌI từ khóa đều xuất hiện trong ít nhất một trong các trường tìm kiếm. */
    private static boolean matches(WaitingReservationResult.Item it, String[] keywords) {
        if (keywords.length == 0 || (keywords.length == 1 && keywords[0].isEmpty())) return true;

        String haystack = normalize(it.maDatTruoc + " " + it.maThe + " " + nz(it.tenDocGia) + " " + nz(it.tenSach));
        for (String k : keywords) {
            if (!haystack.contains(k)) return false;
        }
        return true;
    }

    /** Chữ thường, bỏ dấu tiếng Việt ("Nguyễn" -> "nguyen", "đ" -> "d") để gõ không dấu vẫn tìm được. */
    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private Comparator<WaitingReservationResult.Item> buildComparator(int column, boolean asc) {
        Comparator<WaitingReservationResult.Item> primary;
        switch (column) {
            case COL_MA_DAT:
                primary = (a, b) -> Integer.compare(a.maDatTruoc, b.maDatTruoc);
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
            case COL_TRANG_THAI:
                primary = (a, b) -> collator.compare(statusText(a.trangThai), statusText(b.trangThai));
                break;
            case COL_CUON_GOI_Y:
                primary = (a, b) -> collator.compare(goiYCode(a), goiYCode(b));
                break;
            case COL_HEN_TRA:
                primary = (a, b) -> goiYDate(a).compareTo(goiYDate(b));
                break;
            case COL_NGAY_DAT:
            default:
                primary = ReservationManagementPanel::compareQueue;
                break;
        }

        Comparator<WaitingReservationResult.Item> directed = asc ? primary : primary.reversed();
        // Bằng nhau thì theo thứ tự hàng chờ (đặt trước đứng trước) để thứ tự luôn ổn định
        return directed.thenComparing(ReservationManagementPanel::compareQueue);
    }

    private static String goiYCode(WaitingReservationResult.Item it) {
        return it.cuonSachGoiY == null ? "" : nz(it.cuonSachGoiY.maCuonSach);
    }

    private static String goiYDate(WaitingReservationResult.Item it) {
        return it.cuonSachGoiY == null ? "" : nz(it.cuonSachGoiY.ngayHenTra);
    }

    private void showSummary() {
        String status = String.valueOf(statusBox.getSelectedItem());
        if ("WAITING".equals(selectedStatus())) {
            summaryLabel.setText(all.size() + " lượt đang chờ. Khóa sách theo thứ tự đặt: chỉ lượt cũ nhất của mỗi đầu sách "
                    + "được bấm \"Khóa sách\".");
            summaryLabel.setForeground(all.isEmpty() ? DONE_COLOR : WAITING_COLOR);
        } else {
            summaryLabel.setText(all.size() + " lượt ở trạng thái \"" + status + "\".");
            summaryLabel.setForeground(UIManager.getColor("Label.foreground"));
        }

        String time = " (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")";
        if (all.isEmpty()) {
            statusLabel.setText("Không có lượt đặt trước nào ở trạng thái \"" + status + "\"." + time);
        } else if (shown.isEmpty()) {
            statusLabel.setText("Không có lượt đặt trước phù hợp với điều kiện tìm kiếm." + time);
        } else {
            statusLabel.setText("Hiển thị " + shown.size() + "/" + all.size() + " lượt đặt trước." + time);
        }
    }

    // ==========================================================
    // TIỆN ÍCH
    // ==========================================================
    private static String statusText(String status) {
        for (int i = 0; i < STATUS_VALUES.length; i++) {
            if (STATUS_VALUES[i].equals(status)) return STATUS_LABELS[i];
        }
        return nz(status);
    }

    private static Color statusColor(String status) {
        if ("WAITING".equals(status)) return WAITING_COLOR;
        if ("CANCELLED".equals(status)) return CANCELLED_COLOR;
        if ("RESERVED".equals(status) || "NOTIFIED".equals(status)) return DONE_COLOR;
        return UIManager.getColor("Label.foreground");
    }

    /** "2026-10-15T10:05:00" -> "15/10/2026 10:05". */
    private static String formatDateTime(String iso) {
        if (iso == null) return "";
        if (iso.length() >= 16 && iso.charAt(10) == 'T') {
            return UiUtil.formatDate(iso) + " " + iso.substring(11, 16);
        }
        return UiUtil.formatDate(iso);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
