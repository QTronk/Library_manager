package library.ui;

import library.model.Reservation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Hộp thoại "Thông báo" của Độc giả (UC-10): các thông báo sách đặt trước đã về quầy (lượt đặt trước NOTIFIED).
 *
 * Mở ra là hiện ngay dữ liệu đang có rồi tự làm mới một lần; nút "Làm mới" để tải lại thủ công.
 * Dữ liệu lấy từ {@link ReservationNotifier} (cùng nguồn với dấu thông báo trên tên người dùng).
 * "Đến Đặt trước sách" đóng hộp thoại và báo cho nơi gọi chuyển sang màn hình Đặt trước sách (xem isGoToReservations()).
 */
public class NotificationDialog extends JDialog implements ReservationNotifier.Listener {

    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final Color NOTICE_COLOR = new Color(16, 185, 129);
    private static final DateTimeFormatter DATE_TIME_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int TEXT_WIDTH = 480; // Bề rộng chữ (px) để nội dung tự xuống dòng

    private final ReservationNotifier notifier;
    private final JPanel listPanel = new JPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton refreshButton = new JButton("Làm mới");
    private final JButton goButton = new JButton("Đến Đặt trước sách");
    private final JButton closeButton = new JButton("Đóng");

    private boolean goToReservations = false;

    public NotificationDialog(Window owner, ReservationNotifier notifier) {
        super(owner, "Thông báo", Dialog.ModalityType.APPLICATION_MODAL);
        this.notifier = notifier;

        JLabel title = new JLabel("Thông báo sách đặt trước");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        // Bọc vào NORTH để các khung không bị kéo giãn ra hết chiều cao hộp thoại
        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.add(listPanel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listWrapper);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(TEXT_WIDTH + 60, 300));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(refreshButton);
        buttons.add(goButton);
        buttons.add(closeButton);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 18, 12, 18));
        content.add(title, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);

        refreshButton.addActionListener(e -> startRefresh());
        goButton.addActionListener(e -> {
            goToReservations = true;
            dispose();
        });
        closeButton.addActionListener(e -> dispose());
        for (JButton button : new JButton[]{refreshButton, goButton, closeButton}) {
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        }
        getRootPane().setDefaultButton(closeButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        rebuild(notifier.getNotified());   // Hiện ngay dữ liệu đang có
        notifier.addListener(this);        // Nhận kết quả của lần làm mới
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                startRefresh();            // Rồi cập nhật lại một lần cho mới nhất
            }
        });

        pack();
        setMinimumSize(new Dimension(getWidth(), 300));
        setLocationRelativeTo(owner);
    }

    /** Sau khi hộp thoại đóng: true nếu người dùng chọn "Đến Đặt trước sách". */
    public boolean isGoToReservations() {
        return goToReservations;
    }

    @Override
    public void dispose() {
        notifier.removeListener(this);
        super.dispose();
    }

    // ==========================================================
    // LÀM MỚI
    // ==========================================================
    private void startRefresh() {
        refreshButton.setEnabled(false);
        refreshButton.setText("Đang tải...");
        if (!notifier.refreshNow()) { // Chưa đăng nhập: sẽ không có kết quả nào trả về
            finishRefresh();
        }
    }

    private void finishRefresh() {
        refreshButton.setEnabled(true);
        refreshButton.setText("Làm mới");
    }

    @Override
    public void onChanged(List<Reservation> notified, List<Reservation> newlyNotified) {
        finishRefresh();
        rebuild(notified);
        setStatus(notified.isEmpty()
                ? "Không có thông báo nào. (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")"
                : "Có " + notified.size() + " thông báo. (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")", null);
    }

    @Override
    public void onError(String message) {
        finishRefresh();
        setStatus("Không làm mới được: " + message, ERROR_COLOR);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text == null || text.isEmpty() ? " " : text);
        statusLabel.setForeground(color != null ? color : UIManager.getColor("Label.foreground"));
    }

    // ==========================================================
    // HIỂN THỊ
    // ==========================================================
    private void rebuild(List<Reservation> notified) {
        listPanel.removeAll();

        if (notified.isEmpty()) {
            JLabel empty = new JLabel("Bạn không có thông báo nào.");
            empty.setForeground(UIManager.getColor("Label.disabledForeground"));
            empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(empty);
        } else {
            for (Reservation r : notified) {
                listPanel.add(buildItem(r));
                listPanel.add(Box.createVerticalStrut(8));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
        UiUtil.refit(this);
    }

    /** Một thông báo: nội dung do Backend soạn (thongBao) và dòng chi tiết bên dưới. */
    private JComponent buildItem(Reservation r) {
        StringBuilder sb = new StringBuilder("<html><div style='width:" + TEXT_WIDTH + "px'>");
        sb.append("<b>").append(UiUtil.html(r.thongBao != null ? r.thongBao
                : "Sách [" + (r.tenSach == null ? "" : r.tenSach) + "] bạn đặt trước đã có tại quầy.")).append("</b>");

        StringBuilder detail = new StringBuilder();
        if (r.maCuonSach != null) detail.append("Mã cuốn sách: ").append(UiUtil.html(r.maCuonSach));
        if (r.hanNhanSach != null) {
            if (detail.length() > 0) detail.append(" - ");
            detail.append("Hạn nhận: <font color='#10b981'><b>").append(UiUtil.formatDate(r.hanNhanSach)).append("</b></font>");
        }
        if (r.ngayThongBao != null) {
            if (detail.length() > 0) detail.append(" - ");
            detail.append("Thông báo lúc ").append(formatDateTime(r.ngayThongBao));
        }
        if (detail.length() > 0) sb.append("<br>").append(detail);
        sb.append("</div></html>");

        JLabel label = new JLabel(sb.toString());

        JPanel box = new JPanel(new BorderLayout());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, NOTICE_COLOR),
                BorderFactory.createEmptyBorder(8, 12, 8, 8)));
        box.add(label, BorderLayout.CENTER);
        return box;
    }

    private static String formatDateTime(String iso) {
        try {
            return LocalDateTime.parse(iso).format(DATE_TIME_VN);
        } catch (Exception ex) {
            return UiUtil.formatDate(iso);
        }
    }
}
