package library.ui;

import library.model.UserListResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutionException;

/**
 * Hộp thoại chi tiết một người dùng (Thủ thư hoặc Độc giả) dành cho Admin.
 *
 * - Độc giả: chỉ xem (Thủ thư mới là người quản lý độc giả, UC-11).
 * - Thủ thư (UC-15): mặc định chỉ đọc, có nút "Cập nhật thông tin" để sửa Họ tên, Email, Số điện thoại
 *   và Trạng thái tài khoản (Hoạt động / Bị khóa) rồi "Xác nhận" (UPDATE_LIBRARIAN) hoặc "Hủy".
 *   Chỉ gửi những trường thật sự bị đổi. Sau khi đóng, gọi isModified() để biết có cần tải lại danh sách không.
 *
 * Về MẬT KHẨU: hệ thống chỉ lưu bản băm một chiều (BCrypt) và không có API nào trả mật khẩu về,
 * nên không thể (và không nên) hiển thị. Với Thủ thư, Admin có thể đặt lại mật khẩu mới
 * qua UPDATE_LIBRARIAN. API hiện chưa có chức năng đặt lại mật khẩu cho Độc giả.
 */
public class UserDetailDialog extends JDialog {

    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private final UserListResult.Item item;
    private final boolean librarian;

    private final JTextField hoTenField = newField();
    private final JTextField emailField = newField();
    private final JTextField phoneField = newField();
    private final JTextField accountStatusField = newField();
    private final JComboBox<String> accountStatusBox = new JComboBox<>(new String[]{"Hoạt động", "Bị khóa"});
    private final JPanel accountStatusHolder = new JPanel(new CardLayout());

    private final JButton infoButton = new JButton("Cập nhật thông tin");
    private final JButton resetButton = new JButton("Đặt lại mật khẩu...");
    private final JButton closeButton = new JButton("Đóng");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Xác nhận");
    private final JLabel statusLabel = new JLabel(" ");

    private boolean editing = false;  // Đang ở chế độ sửa thông tin?
    private boolean busy = false;     // Đang chờ Backend trả lời
    private boolean modified = false; // Đã có thay đổi được lưu thành công

    public UserDetailDialog(Window owner, UserListResult.Item item, boolean librarian) {
        super(owner, "Chi tiết " + (librarian ? "thủ thư" : "độc giả"), Dialog.ModalityType.APPLICATION_MODAL);
        this.item = item;
        this.librarian = librarian;

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        accountStatusHolder.add(accountStatusField, "view");
        accountStatusHolder.add(accountStatusBox, "edit");

        int row = 0;
        addRow(form, c, row++, "Mã người dùng:", readOnlyField(String.valueOf(item.maND)));
        addRow(form, c, row++, "Họ tên:", hoTenField);
        addRow(form, c, row++, "Email:", emailField);
        addRow(form, c, row++, "Số điện thoại:", phoneField);
        addRow(form, c, row++, "Vai trò:", readOnlyField(librarian ? "Thủ thư" : "Độc giả"));
        addRow(form, c, row++, "Trạng thái tài khoản:", accountStatusHolder);

        if (!librarian) {
            addRow(form, c, row++, "Mã thẻ:", readOnlyField(item.maThe == null ? "Chưa có thẻ" : String.valueOf(item.maThe)));
            addRow(form, c, row++, "Ngày cấp thẻ:", readOnlyField(UiUtil.formatDate(item.ngayCap)));
            addRow(form, c, row++, "Hạn thẻ:", readOnlyField(UiUtil.formatDate(item.ngayHetHan)));
            addRow(form, c, row++, "Trạng thái thẻ:", readOnlyField(cardStatusText(item.trangThaiThe)));
            addRow(form, c, row++, "Đang mượn:", readOnlyField((item.dangMuon == null ? 0 : item.dangMuon) + " cuốn"));
        }

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(statusLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (librarian) {
            buttons.add(infoButton);
            buttons.add(resetButton);
        }
        buttons.add(closeButton);
        buttons.add(cancelButton);
        buttons.add(confirmButton);
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(14, 4, 0, 4);
        form.add(buttons, c);

        setContentPane(form);

        infoButton.addActionListener(e -> startEdit());
        resetButton.addActionListener(e -> resetPassword());
        closeButton.addActionListener(e -> closeIfIdle());
        cancelButton.addActionListener(e -> cancelEdit());
        confirmButton.addActionListener(e -> submit());

        // Enter chuyển ô tuần tự khi đang sửa
        hoTenField.addActionListener(e -> { if (editing) emailField.requestFocusInWindow(); });
        emailField.addActionListener(e -> { if (editing) phoneField.requestFocusInWindow(); });
        phoneField.addActionListener(e -> { if (editing) accountStatusBox.requestFocusInWindow(); });

        for (JButton button : new JButton[]{infoButton, resetButton, closeButton, cancelButton, confirmButton}) {
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        }

        // Esc: đang sửa thì hủy sửa, còn không thì đóng hộp thoại
        getRootPane().registerKeyboardAction(e -> {
            if (editing) {
                cancelEdit();
            } else {
                closeIfIdle();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Không cho đóng cửa sổ khi đang gửi yêu cầu (nếu không sẽ không biết kết quả)
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfIdle();
            }
        });

        showItem();
        applyMode();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Có thay đổi nào đã được lưu thành công không (để màn hình danh sách tải lại). */
    public boolean isModified() {
        return modified;
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

    // ==========================================================
    // DỰNG GIAO DIỆN
    // ==========================================================

    /**
     * Ô chữ không sửa được (để bôi đen và sao chép). Có sẵn viền gốc và viền "phẳng" với CÙNG khoảng đệm,
     * nhờ vậy chuyển giữa chỉ đọc và cho sửa mà chiều cao ô không đổi (đổi chiều cao sẽ làm vỡ bố cục).
     */
    private static JTextField newField() {
        JTextField field = new JTextField(26);

        Border original = field.getBorder();
        Insets in = original != null ? original.getBorderInsets(field) : new Insets(2, 4, 2, 4);
        Border plain = BorderFactory.createEmptyBorder(in.top, in.left, in.bottom, in.right);

        field.putClientProperty("origBorder", original);
        field.putClientProperty("plainBorder", plain);
        field.setEditable(false);
        field.setBorder(plain);
        return field;
    }

    private static JTextField readOnlyField(String value) {
        JTextField field = newField();
        field.setText(value == null ? "" : value);
        field.setCaretPosition(0);
        return field;
    }

    private static void setFieldEditable(JTextField field, boolean editable) {
        field.setEditable(editable);
        field.setBorder((Border) field.getClientProperty(editable ? "origBorder" : "plainBorder"));
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent value) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(4, 4, 4, 4);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        form.add(l, c);
        c.gridx = 1;
        form.add(value, c);
    }

    /** Đổ dữ liệu của người dùng lên các ô sửa được. */
    private void showItem() {
        hoTenField.setText(nz(item.hoTen));
        emailField.setText(nz(item.email));
        phoneField.setText(nz(item.soDienThoai));

        Boolean active = librarian ? item.trangThai : item.trangThaiTaiKhoan;
        accountStatusField.setText(AdminUsersPanel.accountStatusText(active));
        accountStatusBox.setSelectedIndex(Boolean.FALSE.equals(active) ? 1 : 0);

        for (JTextField field : new JTextField[]{hoTenField, emailField, phoneField, accountStatusField}) {
            field.setCaretPosition(0);
        }
    }

    /** Cập nhật hiển thị theo chế độ hiện tại (xem / sửa) và trạng thái đang gửi. */
    private void applyMode() {
        boolean view = !editing;

        infoButton.setVisible(view);
        resetButton.setVisible(view);
        closeButton.setVisible(view);
        cancelButton.setVisible(editing);
        confirmButton.setVisible(editing);

        infoButton.setEnabled(!busy);
        resetButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        confirmButton.setEnabled(!busy);
        confirmButton.setText(busy ? "Đang lưu..." : "Xác nhận");

        boolean edit = editing && !busy;
        setFieldEditable(hoTenField, edit);
        setFieldEditable(emailField, edit);
        setFieldEditable(phoneField, edit);
        accountStatusBox.setEnabled(edit);
        ((CardLayout) accountStatusHolder.getLayout()).show(accountStatusHolder, editing ? "edit" : "view");

        UiUtil.refit(this); // Phòng khi nội dung cần chỗ lớn hơn cửa sổ hiện tại
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text == null || text.isEmpty() ? " " : text);
        statusLabel.setForeground(color != null ? color : UIManager.getColor("Label.foreground"));
        UiUtil.refit(this); // Thông báo dài có thể làm hộp thoại cần rộng hơn
    }

    private void setBusy(boolean value) {
        busy = value;
        applyMode();
    }

    private void closeIfIdle() {
        if (!busy) {
            dispose();
        }
    }

    // ==========================================================
    // SỬA THÔNG TIN THỦ THƯ (UPDATE_LIBRARIAN)
    // ==========================================================
    private void startEdit() {
        if (busy || !librarian) return;
        editing = true;
        setStatus(" ", null);
        applyMode();
        hoTenField.requestFocusInWindow();
        hoTenField.selectAll();
    }

    /** Hủy: bỏ mọi thay đổi chưa lưu, hiển thị lại dữ liệu cũ ở chế độ chỉ đọc. */
    private void cancelEdit() {
        if (busy) return;
        editing = false;
        showItem();
        setStatus(" ", null);
        applyMode();
    }

    private void fail(String message, JComponent field) {
        setStatus(message, ERROR_COLOR);
        field.requestFocusInWindow();
    }

    private void submit() {
        if (busy || !editing) return;

        final String hoTen = hoTenField.getText().trim();
        final String email = emailField.getText().trim();
        final String phone = phoneField.getText().trim();

        // Kiểm tra theo cùng luật với Backend
        if (hoTen.isEmpty() || hoTen.length() > 100) {
            fail("Họ tên không hợp lệ (không để trống, tối đa 100 ký tự).", hoTenField);
            return;
        }
        if (!email.contains("@") || email.length() > 100) {
            fail("Email không hợp lệ.", emailField);
            return;
        }
        if (!phone.matches("\\d{8,15}")) {
            fail("Số điện thoại không hợp lệ (8 đến 15 chữ số).", phoneField);
            return;
        }

        // Chỉ gửi trường nào thật sự đổi
        final String sendHoTen = hoTen.equals(nz(item.hoTen)) ? null : hoTen;
        final String sendEmail = email.equals(nz(item.email)) ? null : email;
        final String sendPhone = phone.equals(nz(item.soDienThoai)) ? null : phone;

        final boolean currentActive = !Boolean.FALSE.equals(item.trangThai);
        final boolean selectedActive = accountStatusBox.getSelectedIndex() == 0;
        final Boolean sendStatus = (selectedActive == currentActive) ? null : Boolean.valueOf(selectedActive);

        if (sendHoTen == null && sendEmail == null && sendPhone == null && sendStatus == null) {
            editing = false;
            showItem();
            setStatus("Không có thay đổi nào.", null);
            applyMode();
            return;
        }

        // Khóa tài khoản ảnh hưởng trực tiếp tới người khác nên hỏi xác nhận trước
        if (Boolean.FALSE.equals(sendStatus)) {
            int choice = JOptionPane.showOptionDialog(this,
                    "Khóa tài khoản của thủ thư \"" + nz(item.hoTen) + "\"?\n"
                            + "Họ sẽ không đăng nhập được cho đến khi được mở khóa.",
                    "Xác nhận khóa tài khoản", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, new Object[]{"Khóa", "Hủy"}, "Hủy");
            if (choice != 0) return; // 0 = nút "Khóa"
        }

        setStatus(" ", null);
        setBusy(true);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().updateLibrarian(
                        item.maND, sendHoTen, sendEmail, sendPhone, sendStatus);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String message = get();
                    if (sendHoTen != null) item.hoTen = sendHoTen;
                    if (sendEmail != null) item.email = sendEmail;
                    if (sendPhone != null) item.soDienThoai = sendPhone;
                    if (sendStatus != null) item.trangThai = sendStatus;
                    modified = true;
                    editing = false;
                    showItem();
                    setStatus(message, SUCCESS_COLOR);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    // Giữ nguyên chế độ sửa để chỉnh lại (vd. trùng email / số điện thoại)
                    setStatus((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                            + UiUtil.messageOf(cause), ERROR_COLOR);
                }
                applyMode();
            }
        };
        worker.execute();
    }

    // ==========================================================
    // ĐẶT LẠI MẬT KHẨU THỦ THƯ (UPDATE_LIBRARIAN với matKhau)
    // ==========================================================
    private void resetPassword() {
        if (busy || !librarian) return;

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

        setBusy(true);
        resetButton.setText("Đang xử lý...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().resetLibrarianPassword(item.maND, newPassword);
            }

            @Override
            protected void done() {
                busy = false;
                resetButton.setText("Đặt lại mật khẩu...");
                applyMode();
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
