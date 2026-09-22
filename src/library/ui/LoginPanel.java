package library.ui;

import library.model.User;
import javax.swing.*;
import java.awt.*;

public class LoginPanel extends JPanel {
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;
    private JButton btnCancel;

    // Interface để gửi tín hiệu về cho MainFrame xử lý chuyển trang
    public interface LoginListener {
        void onLoginSuccess(User user);
        void onCancelLogin();
    }

    private LoginListener listener;

    public LoginPanel(LoginListener listener) {
        this.listener = listener;
        setLayout(new GridBagLayout());
        initComponents();
    }

    private void initComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. Tiêu đề
        JLabel lblTitle = new JLabel("ĐĂNG NHẬP HỆ THỐNG", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 18));
        lblTitle.setForeground(new Color(33, 150, 243));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(lblTitle, gbc);

        // 2. Tên đăng nhập
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("Tên đăng nhập:"), gbc);

        txtUsername = new JTextField(18);
        gbc.gridx = 1; gbc.gridy = 1;
        add(txtUsername, gbc);

        // 3. Mật khẩu
        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("Mật khẩu:"), gbc);

        txtPassword = new JPasswordField(18);
        gbc.gridx = 1; gbc.gridy = 2;
        add(txtPassword, gbc);

        // 4. Các nút bấm
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnLogin = new JButton("Đăng nhập");
        btnCancel = new JButton("Hủy");

        buttonPanel.add(btnLogin);
        buttonPanel.add(btnCancel);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        add(buttonPanel, gbc);

        // Nút Hủy: Trả về trang chính
        btnCancel.addActionListener(e -> {
            if (listener != null) {
                listener.onCancelLogin();
            }
        });

        // Nút Đăng nhập: Sẽ bổ sung xử lý sự kiện Socket/SwingWorker ở bước sau
    }

    // Các hàm getter để hỗ trợ lấy thông tin dữ liệu nhập từ UI
    public String getUsernameInput() {
        return txtUsername.getText().trim();
    }

    public String getPasswordInput() {
        return new String(txtPassword.getPassword()).trim();
    }

    public JButton getBtnLogin() {
        return btnLogin;
    }
}