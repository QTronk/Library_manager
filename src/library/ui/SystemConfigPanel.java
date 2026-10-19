package library.ui;

import library.model.SystemConfig;
import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Cấu hình quy định" của Admin (UC-04).
 *
 * - Mặc định ở chế độ CHỈ ĐỌC: hiển thị cấu hình lấy từ Backend (GET_SYSTEM_CONFIG).
 * - Bấm "Sửa" -> cho nhập; hiện nút "Hủy" (bỏ thay đổi, về chế độ chỉ đọc) và "Xác nhận"
 *   (gửi UPDATE_SYSTEM_CONFIG). Quy định mới có hiệu lực ngay cho các giao dịch phát sinh sau.
 * - Cả 5 tham số đều phải là số nguyên lớn hơn 0 (giống luật kiểm tra của Backend).
 */
public class SystemConfigPanel extends JPanel {

    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private static final String[] TITLES = {
            "Số sách mượn tối đa",
            "Số ngày mượn tối đa",
            "Tiền phạt mỗi ngày trễ",
            "Số lần gia hạn tối đa",
            "Số ngày giữ sách đặt trước"
    };
    private static final String[] DESCRIPTIONS = {
            "Số cuốn một độc giả được mượn cùng lúc.",
            "Thời hạn mỗi lượt mượn; cũng là số ngày được cộng thêm mỗi lần gia hạn.",
            "Tiền phạt cho mỗi ngày trả sách quá hạn.",
            "Số lần gia hạn tối đa cho mỗi cuốn đang mượn.",
            "Thời gian giữ sách ở quầy cho người đặt trước, tính từ lúc được thông báo."
    };
    private static final String[] UNITS = {"cuốn", "ngày", "đ / ngày", "lần", "ngày"};
    private static final int MONEY_INDEX = 2; // Tiền phạt: cho phép nhập có dấu chấm ngăn cách hàng nghìn

    private final JTextField[] fields = new JTextField[5];
    private final JButton refreshButton = new JButton("Làm mới");
    private final JButton editButton = new JButton("Sửa");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Xác nhận");
    private final JLabel statusLabel = new JLabel(" ");

    private final NumberFormat moneyFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));

    private SystemConfig current = null; // Cấu hình mới nhất lấy từ Backend (null = chưa tải được)
    private boolean editing = false;     // Đang ở chế độ sửa?
    private boolean busy = false;        // Đang gửi yêu cầu lưu?
    private int latestRequestId = 0;     // Để bỏ qua kết quả tải của request đã cũ

    public SystemConfigPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Cấu hình quy định");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        refreshButton.addActionListener(e -> loadConfig());

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.WEST);
        top.add(refreshButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Khung chứa 5 dòng tham số (không giãn quá 760px cho dễ nhìn)
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(760, getPreferredSize().height);
            }
        };
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Quy định hệ thống"),
                BorderFactory.createEmptyBorder(8, 14, 12, 14)));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 4, 8, 4);
        c.anchor = GridBagConstraints.WEST;

        for (int i = 0; i < fields.length; i++) {
            fields[i] = new JTextField(10);
            fields[i].setHorizontalAlignment(JTextField.RIGHT);
            fields[i].setEditable(false);

            JLabel name = new JLabel(TITLES[i]);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            JLabel description = new JLabel(DESCRIPTIONS[i]);
            description.setFont(description.getFont().deriveFont(description.getFont().getSize2D() - 1f));
            description.setForeground(UIManager.getColor("Label.disabledForeground"));

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            description.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(name);
            text.add(description);

            c.gridy = i;
            c.gridx = 0; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
            card.add(text, c);
            c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            card.add(fields[i], c);
            c.gridx = 2;
            JLabel unit = new JLabel(UNITS[i]);
            unit.setPreferredSize(new Dimension(60, unit.getPreferredSize().height));
            card.add(unit, c);

            // Enter: nhảy sang ô tiếp theo; ở ô cuối thì nhảy tới nút Xác nhận
            final int index = i;
            fields[i].addActionListener(e -> {
                if (!editing) return;
                if (index < fields.length - 1) {
                    fields[index + 1].requestFocusInWindow();
                } else {
                    confirmButton.requestFocusInWindow();
                }
            });
        }

        JLabel note = new JLabel("Quy định mới có hiệu lực ngay cho các giao dịch phát sinh sau khi lưu.");
        note.setForeground(UIManager.getColor("Label.disabledForeground"));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 4));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.add(editButton);
        buttonRow.add(cancelButton);
        buttonRow.add(confirmButton);

        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 0, 4));

        editButton.addActionListener(e -> startEdit());
        cancelButton.addActionListener(e -> cancelEdit());
        confirmButton.addActionListener(e -> confirmChange());
        for (JButton button : new JButton[]{editButton, cancelButton, confirmButton}) {
            enterToClick(button);
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(card);
        body.add(note);
        body.add(buttonRow);
        body.add(statusLabel);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(body, BorderLayout.NORTH);
        add(wrapper, BorderLayout.CENTER);

        // Mỗi lần mở màn hình thì tải lại (trừ khi đang sử dở)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadConfig();
            }
        });

        updateButtons();
    }

    /** Cho phép phím Enter bấm nút đang được chọn (mặc định Swing chỉ nhận phím Space). */
    private void enterToClick(JButton button) {
        button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
        button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
    }

    /** Xóa dữ liệu và thoát chế độ sửa (MainFrame gọi khi đăng xuất). */
    public void reset() {
        latestRequestId++; // Bỏ qua mọi request tải đang chạy dở
        editing = false;
        current = null;
        for (JTextField field : fields) {
            field.setText("");
        }
        setStatus(" ", null);
        updateButtons();
    }

    // ==========================================================
    // TẢI CẤU HÌNH
    // ==========================================================
    private void loadConfig() {
        if (editing || busy) return;

        final int requestId = ++latestRequestId;
        setStatus("Đang tải cấu hình từ máy chủ...", null);
        refreshButton.setEnabled(false);

        SwingWorker<SystemConfig, Void> worker = new SwingWorker<SystemConfig, Void>() {
            @Override
            protected SystemConfig doInBackground() throws Exception {
                return SocketClient.getInstance().getSystemConfig();
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã có request mới hơn (hoặc đã đăng xuất)

                try {
                    current = get();
                    showConfig(current);
                    setStatus(" ", null);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    setStatus((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "Không tải được cấu hình: ")
                            + UiUtil.messageOf(cause), ERROR_COLOR);
                }
                updateButtons();
            }
        };
        worker.execute();
    }

    private void showConfig(SystemConfig config) {
        fields[0].setText(String.valueOf(config.soSachToiDa));
        fields[1].setText(String.valueOf(config.soNgayMuonToiDa));
        fields[MONEY_INDEX].setText(moneyFormat.format(config.tienPhatNgay));
        fields[3].setText(String.valueOf(config.soLanGiaHanToiDa));
        fields[4].setText(String.valueOf(config.soNgayGiuSachDatTruoc));
    }

    // ==========================================================
    // SỬA / HỦY / XÁC NHẬN
    // ==========================================================
    private void startEdit() {
        if (current == null || busy) return;
        editing = true;
        setStatus(" ", null);
        updateButtons();
        fields[0].requestFocusInWindow();
        fields[0].selectAll();
    }

    /** Hủy: bỏ mọi thay đổi chưa lưu, hiển thị lại cấu hình cũ ở chế độ chỉ đọc. */
    private void cancelEdit() {
        if (busy) return;
        editing = false;
        if (current != null) {
            showConfig(current);
        }
        setStatus(" ", null);
        updateButtons();
    }

    private void confirmChange() {
        if (!editing || busy) return;

        // Kiểm tra từng ô: số nguyên lớn hơn 0
        long[] values = new long[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Long value = parsePositive(fields[i].getText(), i == MONEY_INDEX ? 12 : 9);
            if (value == null) {
                setStatus(TITLES[i] + " phải là số nguyên lớn hơn 0.", ERROR_COLOR);
                fields[i].requestFocusInWindow();
                fields[i].selectAll();
                return;
            }
            values[i] = value;
        }

        final SystemConfig newConfig = new SystemConfig();
        newConfig.soSachToiDa = (int) values[0];
        newConfig.soNgayMuonToiDa = (int) values[1];
        newConfig.tienPhatNgay = (double) values[2];
        newConfig.soLanGiaHanToiDa = (int) values[3];
        newConfig.soNgayGiuSachDatTruoc = (int) values[4];

        // Không đổi gì thì khỏi gửi
        if (newConfig.sameValues(current)) {
            editing = false;
            showConfig(current);
            setStatus("Không có thay đổi nào.", null);
            updateButtons();
            return;
        }

        busy = true;
        setStatus(" ", null);
        updateButtons();

        SwingWorker<SystemConfig, Void> worker = new SwingWorker<SystemConfig, Void>() {
            @Override
            protected SystemConfig doInBackground() throws Exception {
                return SocketClient.getInstance().updateSystemConfig(newConfig);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    SystemConfig saved = get();
                    current = saved != null ? saved : newConfig;
                    editing = false;
                    showConfig(current);
                    setStatus("Cập nhật quy định thành công.", SUCCESS_COLOR);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    // Giữ nguyên chế độ sửa để người dùng chỉnh lại
                    setStatus((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                            + UiUtil.messageOf(cause), ERROR_COLOR);
                }
                updateButtons();
            }
        };
        worker.execute();
    }

    /** "5.000" hoặc "5000" -> 5000; không phải số nguyên dương (hoặc quá dài) -> null. */
    private static Long parsePositive(String text, int maxDigits) {
        String digits = text == null ? "" : text.replaceAll("[\\s.,]", "");
        if (!digits.matches("\\d{1," + maxDigits + "}")) return null;
        long value = Long.parseLong(digits);
        return value > 0 ? value : null;
    }

    // ==========================================================
    // TRẠNG THÁI GIAO DIỆN
    // ==========================================================
    private void updateButtons() {
        editButton.setVisible(!editing);
        cancelButton.setVisible(editing);
        confirmButton.setVisible(editing);

        editButton.setEnabled(current != null && !busy);
        cancelButton.setEnabled(!busy);
        confirmButton.setEnabled(!busy);
        confirmButton.setText(busy ? "Đang lưu..." : "Xác nhận");
        refreshButton.setEnabled(!editing && !busy);

        for (JTextField field : fields) {
            field.setEditable(editing && !busy);
        }
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text == null || text.isEmpty() ? " " : text);
        if (color != null) {
            statusLabel.setForeground(color);
        } else {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
    }
}
