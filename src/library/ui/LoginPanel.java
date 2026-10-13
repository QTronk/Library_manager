package library.ui;

import library.model.User;
import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

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

        // 2. Email hoặc SĐT (Backend xác thực bằng Email hoặc Số điện thoại)
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("Email hoặc SĐT:"), gbc);

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
            if (listener != null) listener.onCancelLogin();
        });

        btnLogin.addActionListener(e -> handleLogin());
    }

    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()); // Không trim mật khẩu: Backend so sánh nguyên văn

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Email/SĐT và Mật khẩu!");
            return;
        }

        btnLogin.setEnabled(false); // Tránh spam click

        SwingWorker<User, Void> worker = new SwingWorker<>() {
            @Override
            protected User doInBackground() throws Exception {
                return SocketClient.getInstance().login(username, password);
            }

            @Override
            protected void done() {
                try {
                    User user = get();
                    if (user != null) {
                        JOptionPane.showMessageDialog(LoginPanel.this,
                            "Đăng nhập thành công! Xin chào " + user.getFullName());
                        if (listener != null) listener.onLoginSuccess(user);
                    } else {
                        JOptionPane.showMessageDialog(LoginPanel.this,
                            "Mật khẩu hoặc Tên đăng nhập không đúng!",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    // Lấy đúng nguyên nhân thật thay vì luôn báo "Lỗi kết nối!"
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    boolean connectionProblem = cause instanceof IOException;
                    String message = cause.getMessage() != null && !cause.getMessage().trim().isEmpty()
                            ? cause.getMessage()
                            : "Đã xảy ra lỗi không xác định.";

                    JOptionPane.showMessageDialog(LoginPanel.this, message,
                        connectionProblem ? "Lỗi kết nối" : "Đăng nhập thất bại",
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnLogin.setEnabled(true);
                }
            }
        };

        worker.execute(); // Chạy luồng ngầm
    }
}
