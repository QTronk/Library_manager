package library.ui;

import library.model.User;
import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private JPanel contentPanel;
    private CardLayout cardLayout;

    // Card Names (Định danh màn hình)
    private static final String CARD_HOME = "HOME";
    private static final String CARD_SEARCH = "SEARCH";
    private static final String CARD_LOGIN = "LOGIN";

    // Header Components
    private JPanel headerRightPanel;
    private JButton loginButton;

    // Sidebar Menu Panel & Base Buttons
    private JPanel menuPanel;
    private JButton homeButton;
    private JButton searchButton;

    // --- CÁC NÚT PHÂN QUYỀN TRÊN SIDEBAR ---
    // 1. Role Độc giả (READER)
    private JButton historyButton;

    // 2. Role Thủ thư (LIBRARIAN)
    private JButton approveRenewButton;   // Duyệt gia hạn sách
    private JButton approveBorrowButton;  // Duyệt / Lập phiếu mượn
    private JButton returnBookButton;     // Trả sách
    private JButton manageStockButton;    // Quản lý kho sách
    private JButton manageReaderButton;   // Quản lý độc giả

    // 3. Role Admin (ADMIN)
    private JButton reportButton;         // Thống kê & Báo cáo
    private JButton manageAccountButton;  // Quản lý tài khoản
    private JButton configButton;         // Cấu hình quy định

    private User currentUser = null; // Flag trạng thái phiên đăng nhập

    public MainFrame() {
        setTitle("Library Management System");
        setSize(1000, 650);
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

        headerRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        loginButton = new JButton("Đăng nhập");
        headerRightPanel.add(loginButton);

        header.add(title, BorderLayout.WEST);
        header.add(headerRightPanel, BorderLayout.EAST);

        // ==========================================
        // 2. SIDEBAR MENU PANEL (WEST)
        // ==========================================
        menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));

        // Nút Default
        homeButton = new JButton("Trang chủ");
        searchButton = new JButton("Tra cứu sách");

        // Khởi tạo các nút Phân quyền
        historyButton = new JButton("Lịch sử mượn");

        approveRenewButton = new JButton("Duyệt gia hạn sách");
        approveBorrowButton = new JButton("Duyệt / Lập phiếu mượn");
        returnBookButton = new JButton("Trả sách");
        manageStockButton = new JButton("Quản lý kho sách");
        manageReaderButton = new JButton("Quản lý độc giả");

        reportButton = new JButton("Thống kê & Báo cáo");
        manageAccountButton = new JButton("Quản lý tài khoản");
        configButton = new JButton("Cấu hình quy định");

        // Khởi tạo ban đầu chưa đăng nhập: Cập nhật Menu chỉ add các nút Default
        updateSidebarByRole(null);

        // ==========================================
        // 3. CONTENT PANEL (CENTER - CARD LAYOUT)
        // ==========================================
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        contentPanel.add(createHomePanel(), CARD_HOME);
        contentPanel.add(new SearchBookPanel(), CARD_SEARCH);

        LoginPanel loginPanel = new LoginPanel(new LoginPanel.LoginListener() {
            @Override
            public void onLoginSuccess(User user) {
                currentUser = user;
                renderUserHeader(user);
                updateSidebarByRole(user.getRole());
                cardLayout.show(contentPanel, CARD_HOME);
            }

            @Override
            public void onCancelLogin() {
                cardLayout.show(contentPanel, CARD_HOME);
            }
        });
        contentPanel.add(loginPanel, CARD_LOGIN);

        // ==========================================
        // 4. NAVIGATION EVENTS
        // ==========================================
        homeButton.addActionListener(e -> cardLayout.show(contentPanel, CARD_HOME));
        searchButton.addActionListener(e -> cardLayout.show(contentPanel, CARD_SEARCH));
        loginButton.addActionListener(e -> cardLayout.show(contentPanel, CARD_LOGIN));

        add(header, BorderLayout.NORTH);
        add(menuPanel, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        cardLayout.show(contentPanel, CARD_HOME);
    }

    // ==========================================
    // LOGIC CẬP NHẬT HEADER (USER DROPDOWN MENU)
    // ==========================================
    private void renderUserHeader(User user) {
        headerRightPanel.removeAll();

        JButton userMenuButton = new JButton(user.getFullName() + " ▼");
        userMenuButton.setFocusPainted(false);

        JPopupMenu popupMenu = new JPopupMenu();

        // 1. Tài khoản (Đổi tài khoản / Mật khẩu)
        JMenuItem itemAccount = new JMenuItem("Tài khoản");
        itemAccount.addActionListener(e -> JOptionPane.showMessageDialog(this, "Chức năng: Đổi tài khoản / Mật khẩu"));
        popupMenu.add(itemAccount);

        // 2. Thông báo
        JMenuItem itemNotify = new JMenuItem("Thông báo");
        itemNotify.addActionListener(e -> JOptionPane.showMessageDialog(this, "Chức năng: Xem thông báo cá nhân"));
        popupMenu.add(itemNotify);

        popupMenu.addSeparator();

        // 3. Đăng xuất
        JMenuItem itemLogout = new JMenuItem("Đăng xuất");
        itemLogout.setForeground(Color.RED);
        itemLogout.addActionListener(e -> handleLogout());
        popupMenu.add(itemLogout);

        userMenuButton.addActionListener(e -> {
            popupMenu.show(userMenuButton, 0, userMenuButton.getHeight());
        });

        headerRightPanel.add(userMenuButton);
        headerRightPanel.revalidate();
        headerRightPanel.repaint();
    }

    private void handleLogout() {
        currentUser = null;

        headerRightPanel.removeAll();
        headerRightPanel.add(loginButton);
        headerRightPanel.revalidate();
        headerRightPanel.repaint();

        updateSidebarByRole(null);

        cardLayout.show(contentPanel, CARD_HOME);
        JOptionPane.showMessageDialog(this, "Đã đăng xuất thành công!");
    }

    // ==========================================
    // LOGIC PHÂN QUYỀN REBUILD SIDEBAR (KHÔNG BỊ THỦNG LỖ)
    // ==========================================
    private void updateSidebarByRole(String role) {
        // 1. Xóa sạch nút cũ và các đoạn đệm thừa trên Sidebar
        menuPanel.removeAll();

        // 2. Luôn luôn thêm 2 nút Mặc định (Default cho mọi người dùng)
        addMenuButton(homeButton);
        addMenuButton(searchButton);

        // 3. Kiểm tra Role và CHỈ ADD NHỮNG NÚT ĐƯỢC PHÉP
        if ("READER".equals(role)) {
            addMenuButton(historyButton);
        } 
        else if ("LIBRARIAN".equals(role)) {
            addMenuButton(approveRenewButton);
            addMenuButton(approveBorrowButton);
            addMenuButton(returnBookButton);
            addMenuButton(manageStockButton);
            addMenuButton(manageReaderButton);
        } 
        else if ("ADMIN".equals(role)) {
            // Nếu Admin hiển thị 3 nút Quản trị riêng
            addMenuButton(reportButton);
            addMenuButton(manageAccountButton);
            addMenuButton(configButton);

            // Ghi chú: Nếu muốn Admin hiển thị cả các nút của Thủ thư, 
            // bạn chỉ cần bỏ comment các dòng dưới đây:
            // addMenuButton(approveRenewButton);
            // addMenuButton(approveBorrowButton);
            // addMenuButton(returnBookButton);
            // addMenuButton(manageStockButton);
            // addMenuButton(manageReaderButton);
        }

        // 4. Cập nhật lại Layout để vẽ các nút nối tiếp sát khít nhau
        menuPanel.revalidate();
        menuPanel.repaint();
    }

    // Hàm bổ trợ thêm nút kèm khoảng đệm chuẩn 8px
    private void addMenuButton(JButton btn) {
        menuPanel.add(btn);
        menuPanel.add(Box.createVerticalStrut(8));
    }

    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel("Chào mừng đến với Thư viện");
        label.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(label);
        return panel;
    }
}