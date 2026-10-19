package library.ui;

import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Hộp thoại "Đổi mật khẩu" (UC-05 luồng B), dùng chung cho mọi vai trò (Admin, Thủ thư, Độc giả).
 *
 * Client chỉ gửi mật khẩu cũ, mật khẩu mới và xác nhận; tài khoản được Backend lấy từ phiên đăng nhập.
 * Các luật kiểm tra ở đây trùng với Backend (tối thiểu 6 ký tự, xác nhận phải khớp) để báo lỗi ngay,
 * còn việc mật khẩu cũ có đúng hay không chỉ Backend mới biết.
 */
public class ChangePasswordDialog extends JDialog {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private final JPasswordField oldField = new JPasswordField(20);
    private final JPasswordField newField = new JPasswordField(20);
    private final JPasswordField confirmField = new JPasswordField(20);
    private final JCheckBox showCheck = new JCheckBox("Hiện mật khẩu");
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton submitButton = new JButton("Hoàn thành");
    private final JButton cancelButton = new JButton("Hủy");
    private final char defaultEchoChar;

    private boolean submitting = false; // Đang chờ Backend trả lời

    public ChangePasswordDialog(Frame owner) {
        super(owner, "Đổi mật khẩu", true); // true = modal: chặn thao tác ở cửa sổ chính cho đến khi đóng
        defaultEchoChar = oldField.getEchoChar();

        newField.putClientProperty("JTextField.placeholderText", "Tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        addRow(form, c, 0, "Mật khẩu cũ:", oldField);
        addRow(form, c, 1, "Mật khẩu mới:", newField);
        addRow(form, c, 2, "Xác nhận mật khẩu mới:", confirmField);

        c.gridx = 1; c.gridy = 3; c.fill = GridBagConstraints.NONE;
        form.add(showCheck, c);

        errorLabel.setForeground(ERROR_COLOR);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(errorLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(submitButton);
        c.gridx = 0; c.gridy = 5; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 5, 0, 5);
        form.add(buttons, c);

        setContentPane(form);

        showCheck.addActionListener(e -> {
            char echo = showCheck.isSelected() ? (char) 0 : defaultEchoChar;
            oldField.setEchoChar(echo);
            newField.setEchoChar(echo);
            confirmField.setEchoChar(echo);
        });
        submitButton.addActionListener(e -> submit());

        // Enter chuyển ô tuần tự: mật khẩu cũ -> mật khẩu mới -> xác nhận -> nút Hoàn thành.
        // (Ô có ActionListener sẽ giữ lại phím Enter nên không kích hoạt nút mặc định giữa chừng.)
        oldField.addActionListener(e -> newField.requestFocusInWindow());
        newField.addActionListener(e -> confirmField.requestFocusInWindow());
        confirmField.addActionListener(e -> submitButton.requestFocusInWindow());
        cancelButton.addActionListener(e -> closeIfIdle());

        getRootPane().setDefaultButton(submitButton); // Nút mặc định (viền nổi bật): Enter khi đang ở nút = Hoàn thành
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

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        form.add(field, c);
    }

    private void closeIfIdle() {
        if (!submitting) {
            dispose();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null || message.isEmpty() ? " " : message);
        UiUtil.refit(this); // Dòng lỗi dài có thể làm hộp thoại cần rộng hơn
    }

    /** Kiểm tra ở Client theo cùng thứ tự với Backend; trả về thông báo lỗi hoặc null nếu hợp lệ. */
    private String validateInput(String oldPw, String newPw, String confirmPw) {
        if (oldPw.trim().isEmpty()) return "Vui lòng nhập mật khẩu cũ!";
        if (newPw.trim().isEmpty()) return "Vui lòng nhập mật khẩu mới!";
        if (confirmPw.trim().isEmpty()) return "Vui lòng nhập lại mật khẩu mới để xác nhận!";
        if (newPw.length() < MIN_PASSWORD_LENGTH) {
            return "Mật khẩu mới không hợp lệ (tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự)!";
        }
        if (!newPw.equals(confirmPw)) return "Mật khẩu xác nhận không khớp!";
        return null;
    }

    /** Đưa con trỏ về ô đang bị lỗi để người dùng sửa ngay. */
    private void focusFieldFor(String oldPw, String newPw, String confirmPw) {
        if (oldPw.trim().isEmpty()) {
            oldField.requestFocusInWindow();
        } else if (newPw.trim().isEmpty() || newPw.length() < MIN_PASSWORD_LENGTH) {
            newField.requestFocusInWindow();
        } else {
            confirmField.requestFocusInWindow();
        }
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        oldField.setEditable(!value);
        newField.setEditable(!value);
        confirmField.setEditable(!value);
        showCheck.setEnabled(!value);
        cancelButton.setEnabled(!value);
        submitButton.setEnabled(!value);
        submitButton.setText(value ? "Đang xử lý..." : "Hoàn thành");
    }

    private void submit() {
        if (submitting) return;

        final String oldPw = new String(oldField.getPassword());
        final String newPw = new String(newField.getPassword());
        final String confirmPw = new String(confirmField.getPassword());

        String problem = validateInput(oldPw, newPw, confirmPw);
        if (problem != null) {
            showError(problem);
            focusFieldFor(oldPw, newPw, confirmPw);
            return;
        }

        showError(null);
        setSubmitting(true);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().changePassword(oldPw, newPw, confirmPw);
            }

            @Override
            protected void done() {
                try {
                    String message = get();
                    setSubmitting(false);
                    JOptionPane.showMessageDialog(ChangePasswordDialog.this, message,
                            "Đổi mật khẩu", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setSubmitting(false);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String message = cause.getMessage() != null && !cause.getMessage().trim().isEmpty()
                            ? cause.getMessage()
                            : "Đã xảy ra lỗi không xác định.";

                    setSubmitting(false);
                    // Lỗi từ Backend (vd. "Mật khẩu cũ không chính xác!") hiện ngay trong hộp thoại để sửa lại
                    showError(cause instanceof IOException ? "Lỗi kết nối: " + message : message);
                    oldField.requestFocusInWindow();
                    oldField.selectAll();
                }
            }
        };
        worker.execute();
    }
}
