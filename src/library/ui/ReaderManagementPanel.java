package library.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Window;

/**
 * Màn hình "Quản lý độc giả" của Thủ thư (UC-11).
 *
 * Hiện mới có chức năng TẠO THẺ: bấm "+ Tạo thẻ", điền thông tin trong hộp thoại rồi Xác nhận (ADD_READER).
 * Bảng bên dưới chỉ liệt kê các thẻ vừa tạo TRONG PHIÊN LÀM VIỆC NÀY để thủ thư đối chiếu mã thẻ
 * (không phải danh sách độc giả từ Backend). Danh sách đầy đủ, sửa thông tin, gia hạn/khóa thẻ sẽ bổ sung sau.
 */
public class ReaderManagementPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã thẻ", "Mã ND", "Họ tên", "Email", "Số điện thoại", "Ngày cấp", "Hạn thẻ"
    };
    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột Email co giãn khi phóng to cửa sổ.
    private static final int[][] COLUMN_WIDTHS = {
            {70, 80, 100}, {70, 80, 100}, {140, 200, 260}, {180, 260, UiTable.FLEX},
            {110, 120, 150}, {95, 100, 120}, {95, 100, 120}
    };
    private static final int[] ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.LEFT,
            SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.CENTER
    };

    private static final String HINT_EMPTY =
            "Chưa tạo thẻ nào trong phiên này. Bấm \"+ Tạo thẻ\" để cấp thẻ cho độc giả mới.";

    private DefaultTableModel model;
    private JLabel statusLabel;

    public ReaderManagementPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Quản lý độc giả");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JButton createButton = new JButton("+ Tạo thẻ");
        createButton.addActionListener(e -> openCreateDialog());

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.WEST);
        top.add(createButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new UiTable(model);
        UiTable.setColumnWidths(table, COLUMN_WIDTHS);
        UiTable.setColumnAlignments(table, ALIGNMENT);
        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(HINT_EMPTY);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void openCreateDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        CreateReaderDialog dialog = new CreateReaderDialog(owner);
        dialog.setVisible(true); // Hộp thoại dạng modal: dòng này chạy tiếp khi hộp thoại đã đóng

        CreateReaderDialog.Created created = dialog.getCreated();
        if (created == null) return; // Người dùng hủy hoặc chưa tạo được

        // Thẻ mới nhất nằm trên cùng
        model.insertRow(0, new Object[]{
                created.maThe, created.maND, created.hoTen, created.email,
                created.soDienThoai, created.ngayCap, created.ngayHetHan
        });
        statusLabel.setText("Đã tạo " + model.getRowCount() + " thẻ trong phiên này.");

        JOptionPane.showMessageDialog(this,
                "Cấp thẻ độc giả thành công!\nMã thẻ: " + created.maThe + "\nMã người dùng: " + created.maND
                        + "\n\nĐộc giả đăng nhập bằng email hoặc số điện thoại với mật khẩu vừa đặt.",
                "Tạo thẻ", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Xóa danh sách thẻ vừa tạo (MainFrame gọi khi đăng xuất để không lộ thông tin cho người đăng nhập sau). */
    public void clear() {
        model.setRowCount(0);
        statusLabel.setText(HINT_EMPTY);
    }
}
