package library.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;
import library.model.Reservation;
import library.model.User;
import library.service.SocketClient;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

    private JPanel contentPanel;
    private CardLayout cardLayout;

    // Card Names (Định danh màn hình)
    private static final String CARD_HOME = "HOME";
    private static final String CARD_SEARCH = "SEARCH";
    private static final String CARD_LOGIN = "LOGIN";
    private static final String CARD_BORROW = "BORROW";
    private static final String CARD_CARD_CHECK = "CARD_CHECK";
    private static final String CARD_HISTORY = "HISTORY";
    private static final String CARD_FINES = "FINES";
    private static final String CARD_RESERVATIONS = "RESERVATIONS";
    private static final String CARD_FINE_MANAGE = "FINE_MANAGE";
    private static final String CARD_RESERVE_MANAGE = "RESERVE_MANAGE";
    private static final String CARD_RETURN = "RETURN";
    private static final String CARD_ADMIN_USERS = "ADMIN_USERS";
    private static final String CARD_CONFIG = "CONFIG";
    private static final String CARD_READERS = "READERS";
    private static final String CARD_BOOKS = "BOOKS";
    private static final String CARD_REPORT = "REPORT";

    // Header Components
    private JPanel headerRightPanel;
    private JLabel titleLabel;
    private JButton loginButton;

    // Biến Dark/Light Mode
    private boolean isDarkMode = false;
    private JButton themeToggleButton;

    // Sidebar Menu Panel & Nút bấm
    private JPanel menuPanel;
    private SidebarHolder sidebarHolder;        // Khung bọc sidebar: co / giãn bề rộng khi thu vào / ra
    private JButton sidebarToggleButton;        // Nút 3 gạch ngang ở góc trái header
    private boolean sidebarCollapsed = false;
    private Timer sidebarTimer;                 // Hiệu ứng trượt của sidebar
    private JButton homeButton;
    private JButton searchButton;

    // Nút phân quyền Sidebar
    private JButton historyButton;
    private JButton reserveButton;
    private JButton fineButton;
    private JButton manageFineButton;
    private JButton manageReserveButton;
    private JButton approveBorrowButton;
    private JButton checkCardButton;
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
    private CardCheckPanel cardCheckPanel;   // Tương tự: kết quả kiểm tra thẻ của Thủ thư
    private FinePanel finePanel;             // Tương tự: phiếu phạt của Độc giả
    private ReservationPanel reservationPanel; // Tương tự: các lượt đặt trước của Độc giả

    // Thông báo sách đặt trước của Độc giả (UC-10): hỏi Backend định kỳ, hiện dấu trên tên người dùng và hộp thoại báo
    private final ReservationNotifier notifier = new ReservationNotifier();
    private final List<Reservation> pendingPopups = new ArrayList<>(); // Thông báo mới chờ hiện hộp thoại
    private JButton userMenuButton;     // Nút tên người dùng ở góc phải (null khi chưa đăng nhập)
    private JMenuItem notifyMenuItem;   // Mục "Thông báo" trong menu người dùng
    private String userDisplayName;
    private boolean popupShowing = false;            // Đang hiện hộp thoại báo thông báo mới
    private boolean notificationDialogOpen = false;  // Đang mở hộp thoại Thông báo
    private ReservationManagementPanel reservationManagementPanel; // Tương tự: lượt đặt trước của mọi độc giả (Thủ thư)
    private FineManagementPanel fineManagementPanel; // Tương tự: toàn bộ phiếu phạt của Thủ thư
    private AdminUsersPanel adminUsersPanel; // Tương tự: danh sách người dùng của Admin
    private ReaderManagementPanel readerPanel; // Tương tự: xóa danh sách thẻ vừa tạo khi đăng xuất
    private BookManagementPanel bookManagementPanel; // Tương tự: danh sách đầu sách của Thủ thư
    private SystemConfigPanel configPanel;   // Tương tự: thoát chế độ sửa và xóa dữ liệu khi đăng xuất
    private HomeSearchBar homeSearchBar;     // Thanh tìm kiếm ở Trang chủ (mọi vai trò): xóa từ khóa khi đăng xuất

    public MainFrame() {
        setTitle("Library Management System");
        setSize(1280, 720);
        setMinimumSize(new Dimension(1000, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setupAppIcon();

        createUI();
    }

    // ==========================================
    // ICON ỨNG DỤNG (góc trái thanh tiêu đề, thanh tác vụ, hộp thoại con)
    // ==========================================
    private void setupAppIcon() {
        List<Image> icons = new ArrayList<>();
        try {
            URL url = getClass().getResource("/logo1.png");
            BufferedImage src = url != null ? ImageIO.read(url) : ImageIO.read(new File("logo1.png"));
            if (src != null) {
                // Nhiều cỡ để hệ điều hành chọn bản nét nhất cho từng nơi (thanh tiêu đề, Alt+Tab, thanh tác vụ...)
                for (int size : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}) {
                    icons.add(squareIcon(src, size));
                }
            }
        } catch (Exception ignored) {
            // Không có logo thì dùng icon mặc định của Java
        }
        if (icons.isEmpty()) return;

        setIconImages(icons); // Hộp thoại con (JOptionPane, JDialog...) tự dùng lại icon của cửa sổ chính
        try {
            // macOS hiện icon ở Dock qua Taskbar; nơi không hỗ trợ thì bỏ qua
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(icons.get(icons.size() - 1));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Logo là hình chữ nhật (441x212) còn icon cửa sổ là hình vuông: đặt logo vào giữa ô vuông trong suốt
     * (giữ nguyên tỉ lệ, không bị kéo méo) và thu nhỏ từng nấc cho nét.
     */
    private static BufferedImage squareIcon(BufferedImage src, int size) {
        int w = size;
        int h = Math.max(1, Math.round((float) src.getHeight() * size / src.getWidth()));

        BufferedImage scaled = src;
        int curW = src.getWidth(), curH = src.getHeight();
        while (curW / 2 >= w && curH / 2 >= h) { // Thu nhỏ từng nửa để tránh răng cưa
            curW /= 2;
            curH /= 2;
            scaled = resize(scaled, curW, curH);
        }
        scaled = resize(scaled, w, h);

        BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        try {
            g.drawImage(scaled, 0, (size - h) / 2, null);
        } finally {
            g.dispose();
        }
        return icon;
    }

    private static BufferedImage resize(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
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

        // Nút 3 gạch ngang: thu vào / mở ra sidebar (đặt ở header để thu gọn rồi vẫn còn nút để mở lại)
        sidebarToggleButton = new JButton(new HamburgerIcon());
        sidebarToggleButton.setToolTipText("Thu gọn thanh bên");
        sidebarToggleButton.setFocusPainted(false);
        sidebarToggleButton.setContentAreaFilled(false);
        sidebarToggleButton.setBorderPainted(false);
        sidebarToggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sidebarToggleButton.setPreferredSize(new Dimension(40, 36));
        sidebarToggleButton.setMargin(new Insets(0, 0, 0, 0));
        sidebarToggleButton.addActionListener(e -> toggleSidebar());

        titlePanel.add(sidebarToggleButton);
        titlePanel.add(logo1Label);
        titlePanel.add(titleLabel);
        titlePanel.add(logoLabel);

        headerRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerRightPanel.setOpaque(false);

        // Nút Toggle Dark/Light Mode
        // Icon vẽ bằng code (không dùng emoji) để hai trạng thái luôn cùng kích thước, không lệch theo font
        themeToggleButton = new JButton(new ThemeIcon(false));
        themeToggleButton.setToolTipText("Chuyển sang giao diện tối");
        themeToggleButton.setFocusPainted(false);
        themeToggleButton.setContentAreaFilled(false);
        themeToggleButton.setBorderPainted(false);
        themeToggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        themeToggleButton.setPreferredSize(new Dimension(40, 36));
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
        // Sidebar là một khung nền xanh lá rất nhạt + viền xanh lá nhạt (màu đổi theo sáng / tối, xem sidebarFill()).
        // Cạnh trái và cạnh dưới chạm sát mép cửa sổ; chỉ cạnh trên và cạnh phải có viền bo góc.
        // Tự vẽ khung (thay vì dùng Border): đẩy cạnh trái / dưới ra ngoài vùng vẽ nên bị cắt đi, chỉ còn phần chạm mép.
        menuPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int x = -SIDEBAR_ARC;                          // Cạnh trái (và góc bo trái) nằm ngoài mép -> bị cắt
                    int y = SIDEBAR_GAP;                           // Chừa khe nhỏ dưới thanh tiêu đề
                    int w = getWidth() - SIDEBAR_GAP - 1 - x;      // Cạnh phải cách nội dung bên cạnh một khe nhỏ
                    int h = getHeight() + SIDEBAR_ARC - y;         // Cạnh dưới (và góc bo dưới) nằm ngoài mép -> bị cắt
                    g2.setColor(sidebarFill());
                    g2.fillRoundRect(x, y, w, h, SIDEBAR_ARC, SIDEBAR_ARC);
                    g2.setColor(sidebarBorder());
                    g2.drawRoundRect(x, y, w, h, SIDEBAR_ARC, SIDEBAR_ARC);
                } finally {
                    g2.dispose();
                }
            }
        };
        menuPanel.setOpaque(false); // Phần ngoài khung (khe phía trên và bên phải) để lộ nền của cửa sổ
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        // Đệm trong khung 10px như trước, cộng thêm khe ở phía trên và bên phải (trái và dưới chạm mép nên không có khe)
        menuPanel.setBorder(BorderFactory.createEmptyBorder(
                SIDEBAR_GAP + SIDEBAR_PAD, SIDEBAR_PAD, SIDEBAR_PAD, SIDEBAR_GAP + SIDEBAR_PAD));
        menuPanel.setPreferredSize(new Dimension(190, 0));

        // Khởi tạo các nút Menu với Icon tương ứng
        homeButton = createMenuButton("Trang chủ", SidebarIcon.Kind.HOME);
        searchButton = createMenuButton("Tra cứu sách", SidebarIcon.Kind.SEARCH);

        historyButton = createMenuButton("Lịch sử mượn", SidebarIcon.Kind.HISTORY);
        reserveButton = createMenuButton("Đặt trước sách", SidebarIcon.Kind.RESERVE);
        fineButton = createMenuButton("Phiếu phạt", SidebarIcon.Kind.FINE);

        approveBorrowButton = createMenuButton("Lập phiếu mượn", SidebarIcon.Kind.BORROW);
        checkCardButton = createMenuButton("Kiểm tra thẻ", SidebarIcon.Kind.CARD_CHECK);
        returnBookButton = createMenuButton("Trả sách", SidebarIcon.Kind.RETURN);
        manageFineButton = createMenuButton("Quản lý phiếu phạt", SidebarIcon.Kind.FINE_MANAGE);
        manageReserveButton = createMenuButton("Quản lý đặt trước", SidebarIcon.Kind.RESERVE_MANAGE);
        manageStockButton = createMenuButton("Quản lý kho sách", SidebarIcon.Kind.STOCK);
        manageReaderButton = createMenuButton("Quản lý độc giả", SidebarIcon.Kind.READERS);

        reportButton = createMenuButton("Thống kê & Báo cáo", SidebarIcon.Kind.REPORT);
        manageAccountButton = createMenuButton("Quản lý tài khoản", SidebarIcon.Kind.ACCOUNT);
        configButton = createMenuButton("Cấu hình quy định", SidebarIcon.Kind.CONFIG);

        updateSidebarByRole(null);

        // ==========================================
        // 3. CONTENT PANEL (CENTER - CARD LAYOUT)
        // ==========================================
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        contentPanel.add(createHomePanel(), CARD_HOME);
        contentPanel.add(new SearchBookPanel(), CARD_SEARCH);
        contentPanel.add(new BorrowTicketPanel(), CARD_BORROW);
        cardCheckPanel = new CardCheckPanel();
        contentPanel.add(cardCheckPanel, CARD_CARD_CHECK);
        historyPanel = new BorrowHistoryPanel();
        contentPanel.add(historyPanel, CARD_HISTORY);
        finePanel = new FinePanel();
        contentPanel.add(finePanel, CARD_FINES);
        reservationPanel = new ReservationPanel();
        contentPanel.add(reservationPanel, CARD_RESERVATIONS);
        reservationManagementPanel = new ReservationManagementPanel();
        contentPanel.add(reservationManagementPanel, CARD_RESERVE_MANAGE);
        fineManagementPanel = new FineManagementPanel();
        contentPanel.add(fineManagementPanel, CARD_FINE_MANAGE);
        contentPanel.add(new ReturnBookPanel(), CARD_RETURN);
        adminUsersPanel = new AdminUsersPanel();
        contentPanel.add(adminUsersPanel, CARD_ADMIN_USERS);
        configPanel = new SystemConfigPanel();
        contentPanel.add(configPanel, CARD_CONFIG);
        readerPanel = new ReaderManagementPanel();
        contentPanel.add(readerPanel, CARD_READERS);
        bookManagementPanel = new BookManagementPanel();
        contentPanel.add(bookManagementPanel, CARD_BOOKS);
        contentPanel.add(new ReportPanel(), CARD_REPORT);

        setupNotifier();

        LoginPanel loginPanel = new LoginPanel(new LoginPanel.LoginListener() {
            @Override
            public void onLoginSuccess(User user) {
                currentUser = user;
                renderUserHeader(user);
                updateSidebarByRole(user.getRole());
                navigateTo(homeButton, CARD_HOME);
                if ("READER".equals(user.getRole())) {
                    notifier.start(); // UC-10: tải thông báo ngay rồi hỏi lại định kỳ
                }
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
        fineButton.addActionListener(e -> navigateTo(fineButton, CARD_FINES));
        reserveButton.addActionListener(e -> navigateTo(reserveButton, CARD_RESERVATIONS));
        approveBorrowButton.addActionListener(e -> navigateTo(approveBorrowButton, CARD_BORROW));
        checkCardButton.addActionListener(e -> navigateTo(checkCardButton, CARD_CARD_CHECK));
        returnBookButton.addActionListener(e -> navigateTo(returnBookButton, CARD_RETURN));
        manageFineButton.addActionListener(e -> navigateTo(manageFineButton, CARD_FINE_MANAGE));
        manageReserveButton.addActionListener(e -> navigateTo(manageReserveButton, CARD_RESERVE_MANAGE));
        manageStockButton.addActionListener(e -> navigateTo(manageStockButton, CARD_BOOKS));
        manageReaderButton.addActionListener(e -> navigateTo(manageReaderButton, CARD_READERS));

        // Thống kê & Báo cáo (UC-16) chưa làm: tạm trỏ về Trang chủ
        reportButton.addActionListener(e -> navigateTo(reportButton, CARD_REPORT));
        manageAccountButton.addActionListener(e -> navigateTo(manageAccountButton, CARD_ADMIN_USERS));
        configButton.addActionListener(e -> navigateTo(configButton, CARD_CONFIG));

        loginButton.addActionListener(e -> {
            setActiveButton(null); // Bỏ active ở sidebar khi sang Login
            cardLayout.show(contentPanel, CARD_LOGIN);
        });

        add(header, BorderLayout.NORTH);
        sidebarHolder = new SidebarHolder(menuPanel);
        add(sidebarHolder, BorderLayout.WEST);
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

    // --- HÀM TẠO NÚT SIDEBAR CÓ ICON ---
    private JButton createMenuButton(String text, SidebarIcon.Kind iconKind) {
        JButton btn = new JButton(text);
        
        // Tích hợp Icon và tạo khoảng cách giữa Icon - Chữ
        btn.setIcon(new SidebarIcon(iconKind));
        btn.setIconTextGap(13);
        btn.setMargin(new Insets(5, 15, 5, 15));

        // Ép các nút căn trái để Icon thẳng hàng dọc, đệm lề trái 16px
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Rê chuột vào: nút đổi sang nền xanh lá nhạt (nút đang Active giữ nguyên màu xanh đậm)
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hoveredButton = btn;
                applyHover(btn, true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                applyHover(btn, false);
                if (hoveredButton == btn) hoveredButton = null;
            }
        });
        return btn;
    }

    // --- HIỆU ỨNG RÊ CHUỘT CHO NÚT SIDEBAR ---
    private JButton hoveredButton = null; // Nút sidebar đang được rê chuột vào

    /** Màu nền nút khi rê chuột: sáng = emerald-100; tối = xanh lá sẫm hơn nền nút thường một bậc. */
    private Color sidebarHoverFill() {
        return isDarkMode ? new Color(35, 85, 68) : new Color(209, 250, 229);
    }

    /** Bật / tắt màu rê chuột. Nút đang Active không đổi (luôn xanh đậm). */
    private void applyHover(JButton btn, boolean hover) {
        if (btn == currentActiveButton) return;
        if (hover) {
            btn.setBackground(sidebarHoverFill());
        } else {
            btn.setBackground(UIManager.getColor("Button.background")); // Màu mặc định của giao diện (như setActiveButton)
        }
    }

    /** Bỏ màu rê chuột còn sót (vd. nút bị gỡ khỏi sidebar khi đổi vai trò nên không nhận được sự kiện chuột rời đi). */
    private void clearHover() {
        if (hoveredButton != null) {
            applyHover(hoveredButton, false);
            hoveredButton = null;
        }
    }

    // --- MÀU KHUNG SIDEBAR (xanh lá, cùng họ với màu nút Active 16,185,129) ---
    private static final int SIDEBAR_GAP = 6;      // Khe giữa khung và thanh tiêu đề (trên) / vùng nội dung (phải)
    private static final int SIDEBAR_PAD = 10;     // Đệm từ viền khung tới các nút
    private static final int SIDEBAR_ARC = 14;     // Độ bo góc của khung

    /** Nền khung: sáng = emerald-50 (nhạt nhất trong họ xanh lá); tối = xanh rêu rất đậm, tối hơn nền chính một chút. */
    private Color sidebarFill() {
        return isDarkMode ? new Color(30, 48, 42) : new Color(236, 253, 245);
    }

    /** Viền khung: sáng = emerald-200; tối = emerald-800. */
    private Color sidebarBorder() {
        return isDarkMode ? new Color(6, 95, 70) : new Color(167, 243, 208);
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
            themeToggleButton.setIcon(new ThemeIcon(true));
            themeToggleButton.setToolTipText("Chuyển sang giao diện sáng");
            if (titleLabel != null) {
                titleLabel.setForeground(new Color(52, 211, 153));
            }
        } else {
            FlatLightLaf.setup();
            themeToggleButton.setIcon(new ThemeIcon(false));
            themeToggleButton.setToolTipText("Chuyển sang giao diện tối");
            if (titleLabel != null) {
                titleLabel.setForeground(new Color(16, 185, 129));
            }
        }

        FlatLaf.updateUI();
        menuPanel.repaint(); // Vẽ lại khung sidebar với bộ màu của giao diện mới

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

        userDisplayName = user.getFullName();
        userMenuButton = new JButton();
        userMenuButton.setFocusPainted(false);

        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem itemAccount = new JMenuItem("Đổi mật khẩu");
        itemAccount.addActionListener(e -> new ChangePasswordDialog(this).setVisible(true));
        popupMenu.add(itemAccount);

        notifyMenuItem = new JMenuItem("Thông báo");
        if ("READER".equals(user.getRole())) {
            notifyMenuItem.addActionListener(e -> openNotificationDialog());
        } else {
            notifyMenuItem.addActionListener(e -> JOptionPane.showMessageDialog(this, "Chức năng: Xem thông báo cá nhân"));
        }
        popupMenu.add(notifyMenuItem);

        popupMenu.addSeparator();

        JMenuItem itemLogout = new JMenuItem("Đăng xuất");
        itemLogout.setForeground(Color.RED);
        itemLogout.addActionListener(e -> confirmLogout());
        popupMenu.add(itemLogout);

        userMenuButton.addActionListener(e -> popupMenu.show(userMenuButton, 0, userMenuButton.getHeight()));

        updateNotificationBadge();

        headerRightPanel.add(userMenuButton);
        headerRightPanel.revalidate();
        headerRightPanel.repaint();
    }

    // ==========================================
    // THÔNG BÁO SÁCH ĐẶT TRƯỚC (UC-10, Độc giả)
    // ==========================================
    private void setupNotifier() {
        notifier.addListener(new ReservationNotifier.Listener() {
            @Override
            public void onChanged(List<Reservation> notified, List<Reservation> newlyNotified) {
                if (currentUser == null) return;
                updateNotificationBadge();
                if (!newlyNotified.isEmpty()) {
                    pendingPopups.addAll(newlyNotified);
                    showPendingPopups();
                }
            }
        });
    }

    /** Hiện số thông báo trên tên người dùng và trên mục "Thông báo" (không có thông báo thì hiện như thường). */
    private void updateNotificationBadge() {
        if (userMenuButton == null) return;

        int count = notifier.getNotified().size();
        String name = userDisplayName == null ? "" : userDisplayName;
        userMenuButton.setText(count > 0
                ? "<html>" + UiUtil.html(name) + " <font color='#ef4444'>●" + count + "</font> ▼</html>"
                : name + " ▼");
        userMenuButton.setToolTipText(count > 0 ? "Bạn có " + count + " thông báo" : null);
        if (notifyMenuItem != null) {
            notifyMenuItem.setText(count > 0 ? "Thông báo (" + count + ")" : "Thông báo");
        }
    }

    /** Hộp thoại báo ngay khi có thông báo MỚI (mỗi thông báo chỉ báo một lần trong phiên đăng nhập). */
    private void showPendingPopups() {
        if (popupShowing || notificationDialogOpen || pendingPopups.isEmpty()) return;
        popupShowing = true;

        List<Reservation> batch = new ArrayList<>(pendingPopups);
        pendingPopups.clear();

        StringBuilder sb = new StringBuilder("<html><div style='width:380px'>");
        for (int i = 0; i < batch.size(); i++) {
            Reservation r = batch.get(i);
            if (i > 0) sb.append("<br><br>");
            sb.append(UiUtil.html(r.thongBao != null ? r.thongBao
                    : "Sách [" + (r.tenSach == null ? "" : r.tenSach) + "] bạn đặt trước đã có tại quầy."));
            if (r.hanNhanSach != null) {
                sb.append("<br>Hạn nhận: <b>").append(UiUtil.formatDate(r.hanNhanSach)).append("</b>");
            }
        }
        sb.append("</div></html>");

        int choice = JOptionPane.showOptionDialog(this, sb.toString(), "Sách đặt trước đã sẵn sàng",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, new Object[]{"Xem đặt trước", "Đóng"}, "Xem đặt trước");

        popupShowing = false;
        if (currentUser != null && choice == 0) { // 0 = nút "Xem đặt trước"
            navigateTo(reserveButton, CARD_RESERVATIONS);
        }
        showPendingPopups(); // Nếu trong lúc đang hiện lại có thông báo mới tới
    }

    private void openNotificationDialog() {
        if (currentUser == null || notificationDialogOpen) return;
        notificationDialogOpen = true;

        NotificationDialog dialog = new NotificationDialog(this, notifier);
        dialog.setVisible(true); // Hộp thoại modal: dòng tiếp theo chạy khi hộp thoại đã đóng

        notificationDialogOpen = false;
        pendingPopups.clear(); // Những thông báo mới tới lúc đang mở hộp thoại đã được hiện trong đó
        updateNotificationBadge();
        if (dialog.isGoToReservations() && currentUser != null) {
            navigateTo(reserveButton, CARD_RESERVATIONS);
        }
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
        notifier.stop(); // Dừng hỏi thông báo và xóa dữ liệu của phiên này
        pendingPopups.clear();
        historyPanel.clear(); // Không để người đăng nhập sau thấy dữ liệu của người trước
        finePanel.clear();
        reservationPanel.clear();
        cardCheckPanel.clear();
        fineManagementPanel.clear();
        reservationManagementPanel.clear();
        adminUsersPanel.clear();
        configPanel.reset();
        readerPanel.clear();
        bookManagementPanel.clear();
        homeSearchBar.clear();

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

        userMenuButton = null;
        notifyMenuItem = null;

        updateSidebarByRole(null);
        navigateTo(homeButton, CARD_HOME);

        JOptionPane.showMessageDialog(this, "Đã đăng xuất thành công!");
    }

    private void updateSidebarByRole(String role) {
        clearHover();
        menuPanel.removeAll();

        addMenuButton(homeButton);
        addMenuButton(searchButton);

        if ("READER".equals(role)) {
            addMenuButton(historyButton);
            addMenuButton(reserveButton);
            addMenuButton(fineButton);
        } 
        else if ("LIBRARIAN".equals(role)) {
            addMenuButton(approveBorrowButton);
            addMenuButton(checkCardButton);
            addMenuButton(returnBookButton);
            addMenuButton(manageReserveButton);
            addMenuButton(manageFineButton);
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
        label.setFont(new Font("Arial", Font.BOLD, 28));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("Tìm sách theo tên, tác giả, thể loại hoặc nhà xuất bản");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        homeSearchBar = new HomeSearchBar();
        homeSearchBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(label);
        box.add(Box.createVerticalStrut(8));
        box.add(hint);
        box.add(Box.createVerticalStrut(22));
        box.add(homeSearchBar);

        // Nâng khối nội dung lên cao hơn tâm một chút cho cân đối khi cửa sổ lớn
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 120, 0);
        panel.add(box, c);
        return panel;
    }

    // ==========================================
    // THU VÀO / MỞ RA SIDEBAR (mọi vai trò)
    // ==========================================
    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        sidebarToggleButton.setToolTipText(sidebarCollapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên");

        final int from = sidebarHolder.currentWidth(); // Bấm giữa chừng thì đảo chiều từ vị trí đang trượt
        final int target = sidebarCollapsed ? 0 : sidebarHolder.contentWidth();
        if (sidebarTimer != null) sidebarTimer.stop();
        if (!sidebarCollapsed) menuPanel.setVisible(true); // Hiện lại trước khi trượt ra

        final long start = System.nanoTime();
        final double durationMs = 180;
        sidebarTimer = new Timer(12, null);
        sidebarTimer.addActionListener(e -> {
            double t = Math.min(1.0, (System.nanoTime() - start) / 1e6 / durationMs);
            double eased = 1 - Math.pow(1 - t, 3); // Nhanh lúc đầu, chậm dần về cuối
            sidebarHolder.setAnimatedWidth((int) Math.round(from + (target - from) * eased));
            if (t >= 1.0) {
                sidebarTimer.stop();
                if (sidebarCollapsed) {
                    menuPanel.setVisible(false); // Không cho Tab lọt vào nút đã ẩn
                } else {
                    sidebarHolder.setAnimatedWidth(-1); // Về bề rộng tự nhiên (đổi vai trò vẫn tự co giãn)
                }
            }
        });
        sidebarTimer.start();
    }

    /**
     * Khung bọc sidebar: bề rộng có thể đặt tay (để trượt), còn nội dung luôn giữ nguyên bề rộng đầy đủ nên
     * khi trượt các nút chỉ bị che dần chứ không bị bóp méo chữ.
     */
    private static final class SidebarHolder extends JPanel {
        private final JComponent content;
        private int animatedWidth = -1; // -1 = đúng bề rộng nội dung

        SidebarHolder(JComponent content) {
            super(null);
            this.content = content;
            setLayout(new LayoutManager() {
                @Override public void addLayoutComponent(String name, Component comp) { }
                @Override public void removeLayoutComponent(Component comp) { }

                @Override
                public Dimension preferredLayoutSize(Container parent) {
                    return new Dimension(currentWidth(), content.getPreferredSize().height);
                }

                @Override
                public Dimension minimumLayoutSize(Container parent) {
                    return new Dimension(currentWidth(), 0);
                }

                @Override
                public void layoutContainer(Container parent) {
                    content.setBounds(0, 0, contentWidth(), parent.getHeight());
                }
            });
            add(content);
        }

        int contentWidth() {
            return content.getPreferredSize().width;
        }

        int currentWidth() {
            return animatedWidth < 0 ? contentWidth() : animatedWidth;
        }

        void setAnimatedWidth(int width) {
            animatedWidth = width;
            revalidate();
            repaint();
        }
    }

    /** Biểu tượng 3 gạch ngang vẽ bằng code, màu theo giao diện sáng / tối hiện tại. */
    private static class HamburgerIcon implements Icon {
        @Override public int getIconWidth() { return 22; }
        @Override public int getIconHeight() { return 22; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                Color fg = UIManager.getColor("Label.foreground");
                g2.setColor(fg != null ? fg : new Color(71, 85, 105));
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 3; i++) {
                    double ly = y + 5 + i * 6;
                    g2.draw(new java.awt.geom.Line2D.Double(x + 3, ly, x + 19, ly));
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Icon mặt trăng (đang sáng) / mặt trời (đang tối), luôn 22x22 và căn giữa tuyệt đối. */
    private static class ThemeIcon implements Icon {
        private static final int SIZE = 22;
        private final boolean sun;

        ThemeIcon(boolean sun) { this.sun = sun; }

        @Override public int getIconWidth() { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                double cx = x + SIZE / 2.0, cy = y + SIZE / 2.0;
                if (sun) {
                    g2.setColor(new Color(251, 191, 36));          // vàng
                    double r = 4.5;
                    g2.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        g2.draw(new java.awt.geom.Line2D.Double(
                                cx + Math.cos(a) * 7.2, cy + Math.sin(a) * 7.2,
                                cx + Math.cos(a) * 9.8, cy + Math.sin(a) * 9.8));
                    }
                } else {
                    g2.setColor(new Color(71, 85, 105));           // xám xanh đậm
                    java.awt.geom.Area moon = new java.awt.geom.Area(
                            new java.awt.geom.Ellipse2D.Double(cx - 8, cy - 8, 16, 16));
                    moon.subtract(new java.awt.geom.Area(
                            new java.awt.geom.Ellipse2D.Double(cx - 2.5, cy - 11, 15, 15)));
                    g2.fill(moon);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}