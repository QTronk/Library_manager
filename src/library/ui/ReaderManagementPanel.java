package library.ui;

import library.model.UserListResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Quản lý độc giả" của Thủ thư (UC-11).
 *
 * - Mở ra là tự tải TOÀN BỘ danh sách độc giả (GET_READERS).
 * - Tìm kiếm: ô từ khóa (họ tên / email / số điện thoại) + danh sách chọn trạng thái thẻ + nút "Tìm kiếm".
 * - Danh sách chỉ đọc. Bấm vào một dòng để xem chi tiết (ReaderDetailDialog), trong đó có
 *   "Cập nhật thông tin" và "Cập nhật thẻ".
 * - Nút "+ Tạo thẻ" mở hộp thoại cấp thẻ cho độc giả mới (ADD_READER).
 */
public class ReaderManagementPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã ND", "Họ tên", "Email", "Số điện thoại", "Mã thẻ", "Hạn thẻ", "Trạng thái thẻ", "Đang mượn"
    };
    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột Email co giãn khi phóng to cửa sổ.
    private static final int[][] COLUMN_WIDTHS = {
            {70, 80, 100}, {140, 180, 240}, {180, 230, UiTable.FLEX}, {110, 120, 150},
            {70, 80, 100}, {95, 100, 120}, {110, 120, 150}, {80, 90, 110}
    };
    private static final int[] ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.LEFT,
            SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER
    };

    // Danh sách chọn trạng thái thẻ: nhãn hiển thị và giá trị gửi Backend (null = tất cả)
    private static final String[] STATUS_LABELS = {"Tất cả trạng thái thẻ", "Còn hiệu lực", "Bị khóa", "Hết hạn"};
    private static final String[] STATUS_VALUES = {null, "ACTIVE", "LOCKED", "EXPIRED"};

    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField keywordField = new JTextField(24);
    private final JComboBox<String> statusBox = new JComboBox<>(STATUS_LABELS);
    private JTable table;
    private DefaultTableModel model;
    private JLabel statusLabel;

    private final List<UserListResult.Item> items = new ArrayList<>(); // Thứ tự = thứ tự dòng trên bảng
    private int latestRequestId = 0;  // Để bỏ qua kết quả của request đã cũ
    private String lastKeyword = "";  // Bộ lọc của lần tải gần nhất (để tải lại sau khi sửa)
    private String lastStatus = null;

    public ReaderManagementPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Quản lý độc giả");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JButton createButton = new JButton("+ Tạo thẻ");
        createButton.addActionListener(e -> openCreateDialog());

        JPanel titleRow = new JPanel(new BorderLayout(10, 10));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(createButton, BorderLayout.EAST);

        // Hàng tìm kiếm: ô từ khóa, danh sách trạng thái thẻ, nút Tìm kiếm
        keywordField.putClientProperty("JTextField.placeholderText", "Tìm theo họ tên, email, số điện thoại");
        JButton searchButton = new JButton("Tìm kiếm");
        searchButton.addActionListener(e -> search());
        keywordField.addActionListener(e -> search()); // Enter trong ô từ khóa cũng tìm

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterRow.add(keywordField);
        filterRow.add(statusBox);
        filterRow.add(searchButton);

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(titleRow, BorderLayout.NORTH);
        north.add(filterRow, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        // Bảng chỉ đọc
        model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new UiTable(model);
        UiTable.setColumnWidths(table, COLUMN_WIDTHS);
        UiTable.setColumnAlignments(table, ALIGNMENT);

        // Bấm vào một dòng -> xem chi tiết
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < items.size()) {
                    openDetail(items.get(row));
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần mở màn hình: xóa bộ lọc và tải toàn bộ danh sách
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadAll();
            }
        });
    }

    /** Xóa dữ liệu đang hiển thị (MainFrame gọi khi đăng xuất để không lộ thông tin cho người đăng nhập sau). */
    public void clear() {
        latestRequestId++; // Bỏ qua mọi request đang chạy dở
        items.clear();
        model.setRowCount(0);
        statusLabel.setText(" ");
    }

    // ==========================================================
    // TẢI / TÌM KIẾM
    // ==========================================================
    private void loadAll() {
        keywordField.setText("");
        statusBox.setSelectedIndex(0);
        loadReaders("", null);
    }

    private void search() {
        loadReaders(keywordField.getText().trim(), STATUS_VALUES[statusBox.getSelectedIndex()]);
    }

    private void reloadCurrent() {
        loadReaders(lastKeyword, lastStatus);
    }

    private void loadReaders(final String keyword, final String trangThaiThe) {
        final int requestId = ++latestRequestId;
        lastKeyword = keyword;
        lastStatus = trangThaiThe;

        items.clear();
        model.setRowCount(0);
        statusLabel.setText("Đang tải dữ liệu từ máy chủ...");

        SwingWorker<List<UserListResult.Item>, Void> worker = new SwingWorker<List<UserListResult.Item>, Void>() {
            @Override
            protected List<UserListResult.Item> doInBackground() throws Exception {
                return SocketClient.getInstance().getReaders(keyword, trangThaiThe);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã có request mới hơn (hoặc đã đăng xuất)

                try {
                    items.addAll(get());
                    fillTable();

                    boolean filtering = !keyword.isEmpty() || trangThaiThe != null;
                    if (items.isEmpty()) {
                        statusLabel.setText(filtering
                                ? "Không tìm thấy độc giả phù hợp."
                                : "Chưa có độc giả nào.");
                    } else {
                        statusLabel.setText((filtering ? "Tìm thấy " : "Có ") + items.size()
                                + " độc giả. Bấm vào một dòng để xem chi tiết. (Cập nhật lúc "
                                + LocalTime.now().format(TIME_VN) + ")");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được danh sách độc giả.");
                    JOptionPane.showMessageDialog(ReaderManagementPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải danh sách thất bại",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void fillTable() {
        model.setRowCount(0);
        for (UserListResult.Item it : items) {
            model.addRow(new Object[]{
                    it.maND, it.hoTen, it.email, it.soDienThoai,
                    it.maThe == null ? "" : it.maThe,
                    UiUtil.formatDate(it.ngayHetHan),
                    UserDetailDialog.cardStatusText(it.trangThaiThe),
                    it.dangMuon == null ? 0 : it.dangMuon
            });
        }
    }

    // ==========================================================
    // CHI TIẾT / TẠO THẺ
    // ==========================================================
    private void openDetail(UserListResult.Item item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        ReaderDetailDialog dialog = new ReaderDetailDialog(owner, item);
        dialog.setVisible(true); // Hộp thoại modal: dòng tiếp theo chạy khi hộp thoại đã đóng

        if (dialog.isModified()) {
            reloadCurrent(); // Có sửa thành công -> tải lại danh sách theo bộ lọc hiện tại
        }
    }

    private void openCreateDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        CreateReaderDialog dialog = new CreateReaderDialog(owner);
        dialog.setVisible(true);

        CreateReaderDialog.Created created = dialog.getCreated();
        if (created == null) return; // Người dùng hủy hoặc chưa tạo được

        JOptionPane.showMessageDialog(this,
                "Cấp thẻ độc giả thành công!\nMã thẻ: " + created.maThe + "\nMã người dùng: " + created.maND
                        + "\n\nĐộc giả đăng nhập bằng email hoặc số điện thoại với mật khẩu vừa đặt.",
                "Tạo thẻ", JOptionPane.INFORMATION_MESSAGE);

        loadAll(); // Tải lại để thấy ngay thẻ vừa tạo
    }
}
