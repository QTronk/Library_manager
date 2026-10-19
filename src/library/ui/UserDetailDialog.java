package library.ui;

import library.model.UserListResult;
import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * Hộp thoại chi tiết một người dùng (Thủ thư hoặc Độc giả) dành cho Admin.
 *
 * Về MẬT KHẨU: hệ thống chỉ lưu bản băm một chiều (BCrypt) và không có API nào trả mật khẩu về,
 * nên không thể (và không nên) hiển thị. Với Thủ thư, Admin có thể đặt lại mật khẩu mới
 * qua UPDATE_LIBRARIAN. API hiện chưa có chức năng đặt lại mật khẩu cho Độc giả.
 */
public class UserDetailDialog extends JDialog {

    private final UserListResult.Item item;
    private final boolean librarian;
    private final JPanel form = new JPanel(new GridBagLayout());
    private final GridBagConstraints c = new GridBagConstraints();
    private int row = 0;

    public UserDetailDialog(Window owner, UserListResult.Item item, boolean librarian) {
        super(owner, "Chi tiết " + (librarian ? "thủ thư" : "độc giả"), Dialog.ModalityType.APPLICATION_MODAL);
        this.item = item;
        this.librarian = librarian;

        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        addRow("Mã người dùng:", String.valueOf(item.maND));
        addRow("Họ tên:", item.hoTen);
        addRow("Email:", item.email);
        addRow("Số điện thoại:", item.soDienThoai);
        addRow("Vai trò:", librarian ? "Thủ thư" : "Độc giả");
        addRow("Trạng thái tài khoản:",
                AdminUsersPanel.accountStatusText(librarian ? item.trangThai : item.trangThaiTaiKhoan));

        if (!librarian) {
            addRow("Mã thẻ:", item.maThe == null ? "Chưa có thẻ" : String.valueOf(item.maThe));
            addRow("Ngày cấp thẻ:", UiUtil.formatDate(item.ngayCap));
            addRow("Hạn thẻ:", UiUtil.formatDate(item.ngayHetHan));
            addRow("Trạng thái thẻ:", cardStatusText(item.trangThaiThe));
            addRow("Đang mượn:", (item.dangMuon == null ? 0 : item.dangMuon) + " cuốn");
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (librarian) {
            JButton resetButton = new JButton("Đặt lại mật khẩu...");
            resetButton.addActionListener(e -> resetPassword(resetButton));
            buttons.add(resetButton);
        }
        JButton closeButton = new JButton("Đóng");
        closeButton.addActionListener(e -> dispose());
        buttons.add(closeButton);

        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(14, 4, 0, 4);
        form.add(buttons, c);

        setContentPane(form);
        getRootPane().setDefaultButton(closeButton);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Nhãn trạng thái thẻ độc giả bằng tiếng Việt. */
    static String cardStatusText(String status) {
        if (status == null) return "";
        switch (status) {
            case "ACTIVE":  return "Còn hiệu lực";
            case "LOCKED":  return "Bị khóa";
            case "EXPIRED": return "Hết hạn";
            case "NONE":    return "Chưa có thẻ";
            default:        return status;
        }
    }

    private void addRow(String label, String value) {
        // Dùng ô chữ không sửa được để người dùng bôi đen và sao chép (ví dụ email)
        JTextField field = new JTextField(value == null ? "" : value, 26);
        field.setEditable(false);
        field.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        field.setCaretPosition(0);
        addRow(label, field);
    }

    private void addRow(String label, JComponent value) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(4, 4, 4, 4);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        form.add(l, c);
        c.gridx = 1;
        form.add(value, c);
        row++;
    }

    /** Admin đặt lại mật khẩu cho Thủ thư (UPDATE_LIBRARIAN với matKhau). */
    private void resetPassword(JButton resetButton) {
        JPasswordField pw1 = new JPasswordField(18);
        JPasswordField pw2 = new JPasswordField(18);

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Mật khẩu mới (tối thiểu 6 ký tự):"));
        panel.add(pw1);
        panel.add(new JLabel("Nhập lại mật khẩu mới:"));
        panel.add(pw2);

        int choice = JOptionPane.showOptionDialog(this, panel,
                "Đặt lại mật khẩu cho " + (item.hoTen == null ? "thủ thư" : item.hoTen),
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, new Object[]{"Đặt lại", "Hủy"}, "Hủy");
        if (choice != 0) return; // 0 = nút "Đặt lại"

        final String newPassword = new String(pw1.getPassword());
        String confirm = new String(pw2.getPassword());

        if (newPassword.trim().isEmpty() || newPassword.length() < 6) {
            JOptionPane.showMessageDialog(this, "Mật khẩu không hợp lệ (tối thiểu 6 ký tự)!",
                    "Đặt lại mật khẩu", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!newPassword.equals(confirm)) {
            JOptionPane.showMessageDialog(this, "Mật khẩu nhập lại không khớp!",
                    "Đặt lại mật khẩu", JOptionPane.ERROR_MESSAGE);
            return;
        }

        resetButton.setEnabled(false);
        resetButton.setText("Đang xử lý...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().resetLibrarianPassword(item.maND, newPassword);
            }

            @Override
            protected void done() {
                resetButton.setEnabled(true);
                resetButton.setText("Đặt lại mật khẩu...");
                try {
                    get();
                    JOptionPane.showMessageDialog(UserDetailDialog.this,
                            "Đã đặt lại mật khẩu. Hãy báo mật khẩu mới cho thủ thư và nhắc họ đổi lại sau khi đăng nhập.",
                            "Đặt lại mật khẩu", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    JOptionPane.showMessageDialog(UserDetailDialog.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Đặt lại mật khẩu thất bại",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
