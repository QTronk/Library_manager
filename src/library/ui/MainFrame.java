package library.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;
import library.model.User;
import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class MainFrame extends JFrame {

    private JPanel contentPanel;
    private CardLayout cardLayout;

    // Card Names (Định danh màn hình)
    private static final String CARD_HOME = "HOME";
    private static final String CARD_SEARCH = "SEARCH";
    private static final String CARD_LOGIN = "LOGIN";
    private static final String CARD_BORROW = "BORROW";
    private static final String CARD_HISTORY = "HISTORY";
    private static final String CARD_RETURN = "RETURN";
    private static final String CARD_ADMIN_USERS = "ADMIN_USERS";
    private static final String CARD_CONFIG = "CONFIG";

    // Header Components
    private JPanel headerRightPanel;
    private JLabel titleLabel;
    private JButton loginButton;

    // Biến Dark/Light Mode
    private boolean isDarkMode = false;
    private JButton themeToggleButton;

    // Sidebar Menu Panel & Nút bấm
    private JPanel menuPanel;
    private JButton homeButton;
    private JButton searchButton;

    // Nút phân quyền Sidebar
    private JButton historyButton;
    private JButton approveBorrowButton;
    private JButton returnBookButton;
    private JButton manageStockButton;
    private JButton manageReaderButton;
    private JButton reportButton;
    private JButton manageAccountButton;
    private JButton configButton;

    // --- BIẾN Quản LÝ NÚT ACTIVE ---
    private JButton currentActiveButton = null;

    private User currentUser = null;

    private BorrowHistoryPanel historyPanel; // Giữ tham chiếu để xóa dữ liệu khi đăng xuất
    private AdminUsersPanel adminUsersPanel; // Tương tự: danh sách người dùng của Admin
    private SystemConfigPanel configPanel;   // Tương tự: thoát chế độ sửa và xóa dữ liệu khi đăng xuất

    public MainFrame() {
        setTitle("Library Management System");
        setSize(1280, 720);
        setMinimumSize(new Dimension(1000, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createUI();
    }

    private void createUI() {
        // ==========================================
        // 1. HEADER PANEL (NORTH)
        // ==========================================
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        // Title Panel: <logo1> Library Management <logo>
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titlePanel.setOpaque(false);

        // 1. Logo1 (Biểu tượng vô cực - LỚN)
        JLabel logo1Label = new JLabel();
        try {
            URL imgUrl1 = getClass().getResource("/logo1.png");
            ImageIcon icon1 = (imgUrl1 != null) ? new ImageIcon(imgUrl1) : new ImageIcon("logo1.png");
            if (icon1.getIconWidth() > 0) {
                int targetHeight1 = 32;
                int targetWidth1 = (int) ((double) icon1.getIconWidth() / icon1.getIconHeight() * targetHeight1);
                Image img1 = icon1.getImage().getScaledInstance(targetWidth1, targetHeight1, Image.SCALE_SMOOTH);
                logo1Label.setIcon(new ImageIcon(img1));
            }
        } catch (Exception ignored) {}

        // 2. Chữ "Library Management" Xanh lá
        titleLabel = new JLabel("Library Management");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(new Color(16, 185, 129)); // Emerald Green

        // 3. Logo (Biểu tượng xanh biển - NHỎ)
        JLabel logoLabel = new JLabel();
        try {
            URL imgUrl = getClass().getResource("/logo.png");
            ImageIcon icon = (imgUrl != null) ? new ImageIcon(imgUrl) : new ImageIcon("logo.png");
            if (icon.getIconWidth() > 0) {
                int targetHeight = 22;
                int targetWidth = (int) ((double) icon.getIconWidth() / icon.getIconHeight() * targetHeight);
                Image img = icon.getImage().getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
                logoLabel.setIcon(new ImageIcon(img));
            }
        } catch (Exception ignored) {}

        titlePanel.add(logo1Label);
        titlePanel.add(titleLabel);
        titlePanel.add(logoLabel);

        headerRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerRightPanel.setOpaque(false);

        // Nút Toggle Dark/Light Mode
        themeToggleButton = new JButton("🌙");
        themeToggleButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        themeToggleButton.setFocusPainted(false);
        themeToggleButton.setContentAreaFilled(false);
        themeToggleButton.setBorderPainted(false);
        themeToggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        themeToggleButton.setPreferredSize(new Dimension(50, 36));
        themeToggleButton.setMargin(new Insets(0, 0, 0, 0));
        themeToggleButton.addActionListener(e -> toggleTheme());

        loginButton = new JButton("Đăng nhập");

        headerRightPanel.add(themeToggleButton);
        headerRightPanel.add(loginButton);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(headerRightPanel, BorderLayout.EAST);

        // ==========================================
        // 2. SIDEBAR MENU PANEL (WEST)
        // ==========================================
        menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        homeButton = createMenuButton("Trang chủ");
        searchButton = createMenuButton("Tra cứu sách");

        historyButton = createMenuButton("Lịch sử mượn");

        approveBorrowButton = createMenuButton("Lập phiếu mượn");
        returnBookButton = createMenuButton("Trả sách");
        manageStockButton = createMenuButton("Quản lý kho sách");
        manageReaderButton = createMenuButton("Quản lý độc giả");

        reportButton = createMenuButton("Thống kê & Báo cáo");
        manageAccountButton = createMenuButton("Quản lý tài khoản");
        configButton = createMenuButton("Cấu hình quy định");

        updateSidebarByRole(null);

        // ==========================================
        // 3. CONTENT PANEL (CENTER - CARD LAYOUT)
        // ==========================================
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        contentPanel.add(createHomePanel(), CARD_HOME);
        contentPanel.add(new SearchBookPanel(), CARD_SEARCH);
        contentPanel.add(new BorrowTicketPanel(), CARD_BORROW);
        historyPanel = new BorrowHistoryPanel();
        contentPanel.add(historyPanel, CARD_HISTORY);
        contentPanel.add(new ReturnBookPanel(), CARD_RETURN);
        adminUsersPanel = new AdminUsersPanel();
        contentPanel.add(adminUsersPanel, CARD_ADMIN_USERS);
        configPanel = new SystemConfigPanel();
        contentPanel.add(configPanel, CARD_CONFIG);

        LoginPanel loginPanel = new LoginPanel(new LoginPanel.LoginListener() {
            @Override
            public void onLoginSuccess(User user) {
                currentUser = user;
                renderUserHeader(user);
                updateSidebarByRole(user.getRole());
                navigateTo(homeButton, CARD_HOME);
            }

            @Override
            public void onCancelLogin() {
                navigateTo(homeButton, CARD_HOME);
            }
        });
        contentPanel.add(loginPanel, CARD_LOGIN);

        // ==========================================
        // 4. NAVIGATION EVENTS WITH ACTIVE HIGHLIGHT
        // ==========================================
        homeButton.addActionListener(e -> navigateTo(homeButton, CARD_HOME));
        searchButton.addActionListener(e -> navigateTo(searchButton, CARD_SEARCH));
        
        historyButton.addActionListener(e -> navigateTo(historyButton, CARD_HISTORY));
        approveBorrowButton.addActionListener(e -> navigateTo(approveBorrowButton, CARD_BORROW));
        returnBookButton.addActionListener(e -> navigateTo(returnBookButton, CARD_RETURN));
        manageStockButton.addActionListener(e -> navigateTo(manageStockButton, CARD_HOME));
        manageReaderButton.addActionListener(e -> navigateTo(manageReaderButton, CARD_HOME));

        reportButton.addActionListener(e -> navigateTo(reportButton, CARD_ADMIN_USERS));
        manageAccountButton.addActionListener(e -> navigateTo(manageAccountButton, CARD_HOME));
        configButton.addActionListener(e -> navigateTo(configButton, CARD_CONFIG));

        loginButton.addActionListener(e -> {
            setActiveButton(null); // Bỏ active ở sidebar khi sang Login
            cardLayout.show(contentPanel, CARD_LOGIN);
        });

        add(header, BorderLayout.NORTH);
        add(menuPanel, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        // Mặc định chọn Trang chủ ban đầu
        navigateTo(homeButton, CARD_HOME);
    }

    // --- HÀM CHUYỂN TRANG VÀ ĐỔI TRẠNG THÁI ACTIVE ---
    private void navigateTo(JButton button, String cardName) {
        setActiveButton(button);
        cardLayout.show(contentPanel, cardName);
    }

    // --- LOGIC ĐỔI MÀU NÚT ACTIVE (XANH LÁ NGỌC LỤC BẢO) ---
    private void setActiveButton(JButton button) {
        // Reset màu nút Active cũ về màu Button mặc định chuẩn của FlatLaf
        if (currentActiveButton != null) {
            currentActiveButton.setBackground(UIManager.getColor("Button.background"));
            currentActiveButton.setForeground(UIManager.getColor("Button.foreground"));
        }

        currentActiveButton = button;

        // Đặt màu Active mới cho nút vừa chọn
        if (currentActiveButton != null) {
            Color activeColor = isDarkMode ? new Color(5, 150, 105) : new Color(16, 185, 129);
            currentActiveButton.setBackground(activeColor);
            currentActiveButton.setForeground(Color.WHITE);
        }
    }

    private JButton createMenuButton(String text) {
        JButton btn = new JButton(text);
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        return btn;
    }

    private void addMenuButton(JButton btn) {
        menuPanel.add(btn);
        menuPanel.add(Box.createVerticalStrut(8));
    }

    // ==========================================
    // LOGIC CHUYỂN ĐỔI DARK / LIGHT MODE
    // ==========================================
    private void toggleTheme() {
        isDarkMode = !isDarkMode;

        if (isDarkMode) {
            FlatDarkLaf.setup();
            themeToggleButton.setText("☀️");
            if (titleLabel != null) {
                titleLabel.setForeground(new Color(52, 211, 153));
            }
        } else {
            FlatLightLaf.setup();
            themeToggleButton.setText("🌙");
            if (titleLabel != null) {
                titleLabel.setForeground(new Color(16, 185, 129));
            }
        }

        FlatLaf.updateUI();

        // Cập nhật lại màu nút Active sau khi đổi Theme
        if (currentActiveButton != null) {
            setActiveButton(currentActiveButton);
        }
    }

    // ==========================================
    // LOGIC CẬP NHẬT HEADER & SIDEBAR
    // ==========================================
    private void renderUserHeader(User user) {
        headerRightPanel.removeAll();

        headerRightPanel.add(themeToggleButton);

        JButton userMenuButton = new JButton(user.getFullName() + " ▼");
        userMenuButton.setFocusPainted(false);

        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem itemAccount = new JMenuItem("Đổi mật khẩu");
        itemAccount.addActionListener(e -> new ChangePasswordDialog(this).setVisible(true));
        popupMenu.add(itemAccount);

        JMenuItem itemNotify = new JMenuItem("Thông báo");
        itemNotify.addActionListener(e -> JOptionPane.showMessageDialog(this, "Chức năng: Xem thông báo cá nhân"));
        popupMenu.add(itemNotify);

        popupMenu.addSeparator();

        JMenuItem itemLogout = new JMenuItem("Đăng xuất");
        itemLogout.setForeground(Color.RED);
        itemLogout.addActionListener(e -> confirmLogout());
        popupMenu.add(itemLogout);

        userMenuButton.addActionListener(e -> popupMenu.show(userMenuButton, 0, userMenuButton.getHeight()));

        headerRightPanel.add(userMenuButton);
        headerRightPanel.revalidate();
        headerRightPanel.repaint();
    }

    /** UC-06: hỏi xác nhận; chỉ khi người dùng chọn "Đồng ý" mới đăng xuất và báo Backend. */
    private void confirmLogout() {
        int choice = JOptionPane.showOptionDialog(this,
                "Bạn có chắc chắn muốn đăng xuất?", "Đăng xuất",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new Object[]{"Đồng ý", "Hủy"}, "Hủy");
        if (choice == 0) { // 0 = nút "Đồng ý"
            handleLogout();
        }
    }

    private void handleLogout() {
        currentUser = null;
        historyPanel.clear(); // Không để người đăng nhập sau thấy dữ liệu của người trước
        adminUsersPanel.clear();
        configPanel.reset();

        // Báo Backend xóa phiên đăng nhập của kết nối (chạy nền, không chặn giao diện)
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                SocketClient.getInstance().logout();
                return null;
            }
        }.execute();

        headerRightPanel.removeAll();
        headerRightPanel.add(themeToggleButton);
        headerRightPanel.add(loginButton);
        headerRightPanel.revalidate();
        headerRightPanel.repaint();

        updateSidebarByRole(null);
        navigateTo(homeButton, CARD_HOME);

        JOptionPane.showMessageDialog(this, "Đã đăng xuất thành công!");
    }

    private void updateSidebarByRole(String role) {
        menuPanel.removeAll();

        addMenuButton(homeButton);
        addMenuButton(searchButton);

        if ("READER".equals(role)) {
            addMenuButton(historyButton);
        } 
        else if ("LIBRARIAN".equals(role)) {
            addMenuButton(approveBorrowButton);
            addMenuButton(returnBookButton);
            addMenuButton(manageStockButton);
            addMenuButton(manageReaderButton);
        } 
        else if ("ADMIN".equals(role)) {
            addMenuButton(reportButton);
            addMenuButton(manageAccountButton);
            addMenuButton(configButton);
        }

        // Đồng bộ giao diện Sidebar
        SwingUtilities.updateComponentTreeUI(menuPanel);

        // Đảm bảo nút đang active vẫn giữ đúng màu Xanh
        if (currentActiveButton != null) {
            setActiveButton(currentActiveButton);
        }

        menuPanel.revalidate();
        menuPanel.repaint();
    }

    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel("Chào mừng đến với Thư viện");
        label.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(label);
        return panel;
    }
}