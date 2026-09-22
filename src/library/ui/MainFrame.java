package library.ui;

import javax.swing.*;
import library.model.User;
import java.awt.*;

public class MainFrame extends JFrame {

    private JPanel contentPanel;
    private CardLayout cardLayout;

    // Định nghĩa tên định danh (Card Name) cho các màn hình
    private static final String CARD_HOME = "HOME";
    private static final String CARD_SEARCH = "SEARCH";
    private static final String CARD_LOGIN = "LOGIN";

    private JButton loginButton;

    public MainFrame() {
        setTitle("Library Management");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createUI();
    }

    private void createUI() {
        // ==========================================
        // 1. HEADER PANEL (NORTH)
        // ==========================================
        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("   Library Management");
        title.setFont(new Font("Arial", Font.BOLD, 20));

        loginButton = new JButton("Đăng nhập");

        header.add(title, BorderLayout.WEST);
        header.add(loginButton, BorderLayout.EAST);

        // ==========================================
        // 2. MENU PANEL (WEST)
        // ==========================================
        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));

        JButton homeButton = new JButton("Trang chủ");
        JButton searchButton = new JButton("Tra cứu sách");

        menu.add(homeButton);
        menu.add(Box.createVerticalStrut(10));
        menu.add(searchButton);

        // ==========================================
        // 3. CONTENT PANEL (CENTER) - Dùng CardLayout
        // ==========================================
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        // Thêm màn hình 1: Trang chủ
        contentPanel.add(createHomePanel(), CARD_HOME);

        // Thêm màn hình 2: Tra cứu sách
        contentPanel.add(new SearchBookPanel(), CARD_SEARCH);

        // Thêm màn hình 3: Form Đăng nhập[cite: 1]
        LoginPanel loginPanel = new LoginPanel(new LoginPanel.LoginListener() {
            @Override
            public void onLoginSuccess(User user) {
                // Đăng nhập thành công -> quay về Trang chủ (hoặc mở rộng menu sau này)[cite: 1, 2]
                cardLayout.show(contentPanel, CARD_HOME);
            }

            @Override
            public void onCancelLogin() {
                // Nhấn Hủy -> Lật về Trang chủ[cite: 1]
                cardLayout.show(contentPanel, CARD_HOME);
            }
        });
        contentPanel.add(loginPanel, CARD_LOGIN);

        // ==========================================
        // 4. BẮT SỰ KIỆN NÚT BẤM (NAVIGATION)
        // ==========================================
        homeButton.addActionListener(e -> {
            cardLayout.show(contentPanel, CARD_HOME);
        });

        searchButton.addActionListener(e -> {
            cardLayout.show(contentPanel, CARD_SEARCH);
        });

        loginButton.addActionListener(e -> {
            cardLayout.show(contentPanel, CARD_LOGIN); // Lật sang form Đăng nhập[cite: 1]
        });

        // ==========================================
        // 5. THÊM CÁC VÙNG VÀO MAINFRAME
        // ==========================================
        add(header, BorderLayout.NORTH);
        add(menu, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        // Mặc định ban đầu hiển thị màn hình Trang chủ
        cardLayout.show(contentPanel, CARD_HOME);
    }

    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel("Chào mừng đến với Thư viện");
        label.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(label);
        return panel;
    }
}