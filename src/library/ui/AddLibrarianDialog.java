package library.ui;

import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutionException;

/**
 * Hộp thoại "Thêm thủ thư" (UC-15, action ADD_LIBRARIAN) dành cho Admin.
 *
 * Điền đủ thông tin rồi bấm "Xác nhận" để gửi. Các luật kiểm tra ở đây trùng với Backend để báo lỗi ngay;
 * việc trùng email/số điện thoại chỉ Backend biết nên lỗi đó hiện ngay trong hộp thoại để sửa lại.
 * Sau khi hộp thoại đóng, gọi isCreated(): true nghĩa là đã thêm thành công.
 */
public class AddLibrarianDialog extends JDialog {

    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final JTextField hoTenField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JTextField phoneField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JPasswordField confirmField = new JPasswordField(24);
    private final JCheckBox showCheck = new JCheckBox("Hiện mật khẩu");
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Xác nhận");
    private final char defaultEchoChar;

    private boolean created = false; // Đã thêm thành công?
    private boolean busy = false;    // Đang chờ Backend trả lời

    public AddLibrarianDialog(Window owner) {
        super(owner, "Thêm thủ thư", Dialog.ModalityType.APPLICATION_MODAL);
        defaultEchoChar = passwordField.getEchoChar();

        hoTenField.putClientProperty("JTextField.placeholderText", "VD: Nguyễn Văn A");
        emailField.putClientProperty("JTextField.placeholderText", "VD: thuthu@thuvien.vn");
        phoneField.putClientProperty("JTextField.placeholderText", "8 đến 15 chữ số");
        passwordField.putClientProperty("JTextField.placeholderText",
                "Mật khẩu ban đầu, tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Họ tên:", hoTenField);
        addRow(form, c, row++, "Email:", emailField);
        addRow(form, c, row++, "Số điện thoại:", phoneField);
        addRow(form, c, row++, "Mật khẩu:", passwordField);
        addRow(form, c, row++, "Nhập lại mật khẩu:", confirmField);

        c.gridx = 1; c.gridy = row++; c.fill = GridBagConstraints.NONE;
        form.add(showCheck, c);

        errorLabel.setForeground(ERROR_COLOR);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(errorLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(confirmButton);
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 5, 0, 5);
        form.add(buttons, c);

        setContentPane(form);

        showCheck.addActionListener(e -> {
            char echo = showCheck.isSelected() ? (char) 0 : defaultEchoChar;
            passwordField.setEchoChar(echo);
            confirmField.setEchoChar(echo);
        });
        cancelButton.addActionListener(e -> closeIfIdle());
        confirmButton.addActionListener(e -> submit());

        // Enter chuyển ô tuần tự; ở ô cuối thì nhảy tới nút Xác nhận (Enter lần nữa để gửi)
        JTextField[] order = {hoTenField, emailField, phoneField, passwordField, confirmField};
        for (int i = 0; i < order.length; i++) {
            final JComponent next = (i < order.length - 1) ? order[i + 1] : confirmButton;
            order[i].addActionListener(e -> next.requestFocusInWindow());
        }
        for (JButton button : new JButton[]{cancelButton, confirmButton}) {
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        }

        getRootPane().registerKeyboardAction(e -> closeIfIdle(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Không cho đóng cửa sổ khi đang gửi yêu cầu (nếu không sẽ không biết kết quả)
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfIdle();
            }
        });

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Sau khi hộp thoại đóng: true nếu đã thêm thủ thư thành công. */
    public boolean isCreated() {
        return created;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        form.add(field, c);
    }

    private void closeIfIdle() {
        if (!busy) {
            dispose();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null || message.isEmpty() ? " " : message);
        UiUtil.refit(this); // Dòng lỗi dài có thể làm hộp thoại cần rộng hơn
    }

    private void fail(String message, JComponent field) {
        showError(message);
        field.requestFocusInWindow();
    }

    private void setBusy(boolean value) {
        busy = value;
        hoTenField.setEditable(!value);
        emailField.setEditable(!value);
        phoneField.setEditable(!value);
        passwordField.setEditable(!value);
        confirmField.setEditable(!value);
        showCheck.setEnabled(!value);
        cancelButton.setEnabled(!value);
        confirmButton.setEnabled(!value);
        confirmButton.setText(value ? "Đang gửi..." : "Xác nhận");
    }

    private void submit() {
        if (busy) return;

        final String hoTen = hoTenField.getText().trim();
        final String email = emailField.getText().trim();
        final String phone = phoneField.getText().trim();
        final String password = new String(passwordField.getPassword());
        final String confirm = new String(confirmField.getPassword());

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
        if (password.length() < MIN_PASSWORD_LENGTH) {
            fail("Mật khẩu không hợp lệ (tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự)!", passwordField);
            return;
        }
        if (!password.equals(confirm)) {
            fail("Mật khẩu nhập lại không khớp!", confirmField);
            return;
        }

        showError(null);
        setBusy(true);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().addLibrarian(hoTen, email, phone, password);
            }

            @Override
            protected void done() {
                try {
                    get();
                    setBusy(false);
                    created = true;
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setBusy(false);
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    String message = UiUtil.messageOf(cause);

                    setBusy(false); // Cho phép sửa và gửi lại
                    // Lỗi từ Backend (vd. trùng email/số điện thoại) hiện ngay trong hộp thoại
                    fail((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "") + message,
                            message.contains("sử dụng") || message.contains("tồn tại") ? emailField : hoTenField);
                }
            }
        };
        worker.execute();
    }
}
