package library.ui;

import library.model.UserListResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
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
 * Màn hình "Thống kê người dùng" của Admin: danh sách người dùng cấp Thủ thư trở xuống
 * (Thủ thư và Độc giả). Bấm vào một dòng để xem thông tin chi tiết (UserDetailDialog).
 *
 * - Thủ thư: GET_LIBRARIANS (quyền ADMIN). Có nút "+ Thêm thủ thư" (ADD_LIBRARIAN) và trong hộp thoại chi tiết
 *   có nút "Cập nhật thông tin" (UPDATE_LIBRARIAN). Các nút này chỉ hiện khi đang xem danh sách Thủ thư (UC-15).
 * - Độc giả: GET_READERS, chỉ để xem; việc quản lý độc giả do Thủ thư làm (UC-11).
 *   Lưu ý: theo bảng phân quyền hiện tại của Backend, action này chỉ dành cho Thủ thư,
 *   nên Admin sẽ nhận lỗi "không có quyền" cho đến khi Backend mở thêm quyền ADMIN cho action này.
 */
public class AdminUsersPanel extends JPanel {

    private static final String[] LIBRARIAN_COLUMNS = {"Mã ND", "Họ tên", "Email", "Số điện thoại", "Trạng thái"};
    private static final String[] READER_COLUMNS = {
            "Mã ND", "Họ tên", "Email", "Số điện thoại", "Mã thẻ", "Hạn thẻ", "Trạng thái thẻ", "Đang mượn"
    };
    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột Email co giãn để nhận phần dư khi phóng to cửa sổ.
    private static final int[][] LIBRARIAN_WIDTHS = {
            {70, 80, 100}, {140, 200, 280}, {180, 260, UiTable.FLEX}, {110, 120, 150}, {100, 110, 140}
    };
    private static final int[] LIBRARIAN_ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.CENTER
    };
    private static final int[][] READER_WIDTHS = {
            {70, 80, 100}, {140, 180, 240}, {180, 230, UiTable.FLEX}, {110, 120, 150},
            {70, 80, 100}, {95, 100, 120}, {110, 120, 150}, {80, 90, 110}
    };
    private static final int[] READER_ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.LEFT,
            SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER
    };
    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JComboBox<String> roleBox = new JComboBox<>(new String[]{"Thủ thư", "Độc giả"});
    private final JTextField keywordField = new JTextField(22);
    private final JButton addButton = new JButton("+ Thêm thủ thư");
    private JTable table;
    private DefaultTableModel model;
    private JLabel statusLabel;

    private final List<UserListResult.Item> items = new ArrayList<>(); // Thứ tự = thứ tự dòng trên bảng
    private boolean displayedLibrarian = true; // Loại người dùng đang hiển thị trên bảng
    private int latestRequestId = 0;           // Để bỏ qua kết quả của request đã cũ

    public AdminUsersPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Thống kê người dùng");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JButton refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> {
            keywordField.setText("");
            loadUsers();
        });
        addButton.addActionListener(e -> openAddDialog());

        // Góc phải: nút Thêm thủ thư (chỉ hiện khi đang xem Thủ thư) và nút Làm mới
        JPanel titleActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        titleActions.add(addButton);
        titleActions.add(refreshButton);

        JPanel titleRow = new JPanel(new BorderLayout(10, 10));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(titleActions, BorderLayout.EAST);

        keywordField.putClientProperty("JTextField.placeholderText", "Tìm theo họ tên, email, số điện thoại");
        JButton searchButton = new JButton("Tìm");
        searchButton.addActionListener(e -> loadUsers());
        keywordField.addActionListener(e -> loadUsers());
        roleBox.addActionListener(e -> loadUsers());

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterRow.add(new JLabel("Loại người dùng:"));
        filterRow.add(roleBox);
        filterRow.add(keywordField);
        filterRow.add(searchButton);

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(titleRow, BorderLayout.NORTH);
        north.add(filterRow, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        // Bảng chỉ đọc
        model = new DefaultTableModel(LIBRARIAN_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new UiTable(model); // Cột cố định, dòng cao, tooltip khi chữ bị cắt, cuộn ngang khi cửa sổ hẹp
        applyColumnStyle(true);

        // Bấm vào một dòng -> xem chi tiết (giống màn Tra cứu sách)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < items.size()) {
                    showDetail(items.get(row));
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần mở màn hình thì tải lại
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadUsers();
            }
        });
    }

    /** Xóa dữ liệu đang hiển thị (MainFrame gọi khi đăng xuất). */
    public void clear() {
        latestRequestId++; // Bỏ qua mọi request đang chạy dở
        items.clear();
        model.setRowCount(0);
        statusLabel.setText(" ");
    }

    // ==========================================================
    // CHI TIẾT / THÊM THỦ THƯ
    // ==========================================================
    private void showDetail(UserListResult.Item item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        UserDetailDialog dialog = new UserDetailDialog(owner, item, displayedLibrarian);
        dialog.setVisible(true); // Hộp thoại modal: dòng tiếp theo chạy khi hộp thoại đã đóng

        if (dialog.isModified()) {
            loadUsers(); // Có sửa thành công -> tải lại danh sách theo bộ lọc hiện tại
        }
    }

    private void openAddDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        AddLibrarianDialog dialog = new AddLibrarianDialog(owner);
        dialog.setVisible(true);

        if (!dialog.isCreated()) return; // Người dùng hủy hoặc chưa thêm được

        JOptionPane.showMessageDialog(this,
                "Thêm thủ thư thành công!\n\nThủ thư đăng nhập bằng email hoặc số điện thoại với mật khẩu vừa đặt.",
                "Thêm thủ thư", JOptionPane.INFORMATION_MESSAGE);

        keywordField.setText(""); // Xóa từ khóa để thấy ngay thủ thư vừa thêm
        loadUsers();
    }

    // ==========================================================
    // TẢI DANH SÁCH
    // ==========================================================
    private void loadUsers() {
        final boolean librarian = roleBox.getSelectedIndex() == 0;
        final String keyword = keywordField.getText().trim();
        final int requestId = ++latestRequestId;

        // Đổi cột theo loại người dùng và xóa dòng cũ để không lẫn dữ liệu của loại kia
        displayedLibrarian = librarian;
        addButton.setVisible(librarian); // Chỉ thủ thư mới thêm được ở màn hình này
        items.clear();
        model.setColumnIdentifiers(librarian ? LIBRARIAN_COLUMNS : READER_COLUMNS);
        applyColumnStyle(librarian); // Đổi cột thì JTable dựng lại cột nên phải đặt lại độ rộng và căn lề
        model.setRowCount(0);
        statusLabel.setText("Đang tải dữ liệu từ máy chủ...");

        SwingWorker<List<UserListResult.Item>, Void> worker = new SwingWorker<List<UserListResult.Item>, Void>() {
            @Override
            protected List<UserListResult.Item> doInBackground() throws Exception {
                return librarian
                        ? SocketClient.getInstance().getLibrarians(keyword)
                        : SocketClient.getInstance().getReaders(keyword);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã có request mới hơn (hoặc đã đăng xuất)

                try {
                    items.addAll(get());
                    fillTable(librarian);
                    statusLabel.setText("Có " + items.size() + (librarian ? " thủ thư" : " độc giả")
                            + (keyword.isEmpty() ? "" : " khớp từ khóa \"" + keyword + "\"")
                            + ". Bấm vào một dòng để xem chi tiết. (Cập nhật lúc "
                            + LocalTime.now().format(TIME_VN) + ")");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được danh sách.");
                    JOptionPane.showMessageDialog(AdminUsersPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải danh sách thất bại",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void applyColumnStyle(boolean librarian) {
        UiTable.setColumnWidths(table, librarian ? LIBRARIAN_WIDTHS : READER_WIDTHS);
        UiTable.setColumnAlignments(table, librarian ? LIBRARIAN_ALIGNMENT : READER_ALIGNMENT);
    }

    private void fillTable(boolean librarian) {
        model.setRowCount(0);
        for (UserListResult.Item it : items) {
            if (librarian) {
                model.addRow(new Object[]{
                        it.maND, it.hoTen, it.email, it.soDienThoai,
                        accountStatusText(it.trangThai)
                });
            } else {
                model.addRow(new Object[]{
                        it.maND, it.hoTen, it.email, it.soDienThoai,
                        it.maThe == null ? "" : it.maThe,
                        UiUtil.formatDate(it.ngayHetHan),
                        UserDetailDialog.cardStatusText(it.trangThaiThe),
                        it.dangMuon == null ? 0 : it.dangMuon
                });
            }
        }
    }

    static String accountStatusText(Boolean active) {
        if (active == null) return "Không rõ";
        return active ? "Hoạt động" : "Bị khóa";
    }
}
