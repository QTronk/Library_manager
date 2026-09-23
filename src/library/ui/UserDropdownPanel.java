package library.ui;

import library.model.User;
import javax.swing.*;
import java.awt.*;

public class UserDropdownPanel extends JPanel {

    public interface UserDropdownListener {
        void onLogout();
        void onAction(String actionName); // Cho các chức năng phụ sau này nếu có
    }

    public UserDropdownPanel(User user, UserDropdownListener listener) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        setBackground(Color.WHITE);

        // 1. Tên User (Header của dropdown)
        JLabel lblName = new JLabel(user.getFullName());
        lblName.setFont(new Font("Arial", Font.BOLD, 13));
        lblName.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(lblName);

        add(new JSeparator());

        // 2. Các nút Chức năng phụ (Tùy chọn theo Role)
        if ("ADMIN".equals(user.getRole())) {
            addMenuItem("Quản lý tài khoản", () -> listener.onAction("MANAGE_ACCOUNT"));
        } else if ("READER".equals(user.getRole())) {
            addMenuItem("Lịch sử mượn", () -> listener.onAction("HISTORY"));
        }

        // 3. Nút Đăng xuất
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setFont(new Font("Arial", Font.PLAIN, 12));
        btnLogout.setForeground(Color.RED);
        btnLogout.setContentAreaFilled(false);
        btnLogout.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        btnLogout.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogout.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        btnLogout.addActionListener(e -> {
            if (listener != null) listener.onLogout();
        });

        add(btnLogout);
    }

    private void addMenuItem(String title, Runnable action) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Arial", Font.PLAIN, 12));
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());
        add(btn);
    }
}