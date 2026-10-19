package library.ui;

import library.model.CardUpdateResult;
import library.model.UserListResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.concurrent.ExecutionException;

/**
 * Hộp thoại chi tiết một độc giả dành cho Thủ thư (UC-11). Mặc định CHỈ ĐỌC, có hai chức năng sửa:
 *  - "Cập nhật thông tin": sửa trực tiếp Họ tên, Email, Số điện thoại (UPDATE_READER).
 *  - "Cập nhật thẻ": sửa Ngày hết hạn và Trạng thái thẻ bằng danh sách chọn (UPDATE_CARD).
 * Ở mỗi chế độ sửa có nút "Hủy" (bỏ thay đổi, về chỉ đọc) và "Xác nhận" (gửi Backend).
 * Chỉ gửi những trường thật sự bị đổi. Sau khi đóng, gọi isModified() để biết có cần tải lại danh sách không.
 */
public class ReaderDetailDialog extends JDialog {

    private enum Mode { VIEW, EDIT_INFO, EDIT_CARD }

    private static final DateTimeFormatter DATE_INPUT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";

    private final UserListResult.Item item;

    private final JTextField maNdField = newField();
    private final JTextField hoTenField = newField();
    private final JTextField emailField = newField();
    private final JTextField phoneField = newField();
    private final JTextField accountStatusField = newField();
    private final JTextField maTheField = newField();
    private final JTextField ngayCapField = newField();
    private final JTextField ngayHetHanField = newField();
    private final JTextField cardStatusField = newField();
    private final JTextField dangMuonField = newField();
    private final JComboBox<String> cardStatusBox = new JComboBox<>(new String[]{"Còn hiệu lực", "Bị khóa"});
    private final JPanel cardStatusHolder = new JPanel(new CardLayout());

    private final JButton infoButton = new JButton("Cập nhật thông tin");
    private final JButton cardButton = new JButton("Cập nhật thẻ");
    private final JButton closeButton = new JButton("Đóng");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Xác nhận");
    private final JLabel statusLabel = new JLabel(" ");

    private Mode mode = Mode.VIEW;
    private boolean busy = false;     // Đang chờ Backend trả lời
    private boolean modified = false; // Đã có thay đổi được lưu thành công

    public ReaderDetailDialog(Window owner, UserListResult.Item item) {
        super(owner, "Chi tiết độc giả", Dialog.ModalityType.APPLICATION_MODAL);
        this.item = item;

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        cardStatusHolder.add(cardStatusField, "view");
        cardStatusHolder.add(cardStatusBox, "edit");

        int row = 0;
        addRow(form, c, row++, "Mã người dùng:", maNdField);
        addRow(form, c, row++, "Họ tên:", hoTenField);
        addRow(form, c, row++, "Email:", emailField);
        addRow(form, c, row++, "Số điện thoại:", phoneField);
        addRow(form, c, row++, "Trạng thái tài khoản:", accountStatusField);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(new JSeparator(), c);

        addRow(form, c, row++, "Mã thẻ:", maTheField);
        addRow(form, c, row++, "Ngày cấp thẻ:", ngayCapField);
        addRow(form, c, row++, "Ngày hết hạn:", ngayHetHanField);
        addRow(form, c, row++, "Trạng thái thẻ:", cardStatusHolder);
        addRow(form, c, row++, "Đang mượn:", dangMuonField);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(statusLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(infoButton);
        buttons.add(cardButton);
        buttons.add(closeButton);
        buttons.add(cancelButton);
        buttons.add(confirmButton);
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 4, 0, 4);
        form.add(buttons, c);

        setContentPane(form);

        infoButton.addActionListener(e -> startEdit(Mode.EDIT_INFO));
        cardButton.addActionListener(e -> startEdit(Mode.EDIT_CARD));
        closeButton.addActionListener(e -> closeIfIdle());
        cancelButton.addActionListener(e -> cancelEdit());
        confirmButton.addActionListener(e -> confirmEdit());

        // Enter chuyển ô tuần tự khi đang sửa
        hoTenField.addActionListener(e -> { if (mode == Mode.EDIT_INFO) emailField.requestFocusInWindow(); });
        emailField.addActionListener(e -> { if (mode == Mode.EDIT_INFO) phoneField.requestFocusInWindow(); });
        phoneField.addActionListener(e -> { if (mode == Mode.EDIT_INFO) confirmButton.requestFocusInWindow(); });
        ngayHetHanField.addActionListener(e -> { if (mode == Mode.EDIT_CARD) cardStatusBox.requestFocusInWindow(); });

        for (JButton button : new JButton[]{infoButton, cardButton, closeButton, cancelButton, confirmButton}) {
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        }

        // Esc: đang sửa thì hủy sửa, còn không thì đóng hộp thoại
        getRootPane().registerKeyboardAction(e -> {
            if (mode != Mode.VIEW) {
                cancelEdit();
            } else {
                closeIfIdle();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfIdle();
            }
        });

        showItem();
        applyMode();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Có thay đổi nào đã được lưu thành công không (để màn hình danh sách tải lại). */
    public boolean isModified() {
        return modified;
    }

    // ==========================================================
    // DỰNG GIAO DIỆN
    // ==========================================================
    private static JTextField newField() {
        JTextField field = new JTextField(26);

        // Viền gốc (hiện khi cho sửa) và viền "phẳng" (khi chỉ đọc). Viền phẳng được tạo với ĐÚNG khoảng đệm
        // của viền gốc để chiều cao ô không đổi khi chuyển chế độ (đổi chiều cao sẽ làm vỡ bố cục hộp thoại).
        Border original = field.getBorder();
        Insets in = original != null ? original.getBorderInsets(field) : new Insets(2, 4, 2, 4);
        Border plain = BorderFactory.createEmptyBorder(in.top, in.left, in.bottom, in.right);

        field.putClientProperty("origBorder", original);
        field.putClientProperty("plainBorder", plain);
        field.setEditable(false);
        field.setBorder(plain);
        return field;
    }

    private static void setFieldEditable(JTextField field, boolean editable) {
        field.setEditable(editable);
        field.setBorder((Border) field.getClientProperty(editable ? "origBorder" : "plainBorder"));
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        form.add(l, c);
        c.gridx = 1;
        form.add(field, c);
    }

    /** Đổ dữ liệu của độc giả lên các ô. */
    private void showItem() {
        maNdField.setText(String.valueOf(item.maND));
        hoTenField.setText(nz(item.hoTen));
        emailField.setText(nz(item.email));
        phoneField.setText(nz(item.soDienThoai));
        accountStatusField.setText(AdminUsersPanel.accountStatusText(item.trangThaiTaiKhoan));
        maTheField.setText(item.maThe == null ? "Chưa có thẻ" : String.valueOf(item.maThe));
        ngayCapField.setText(UiUtil.formatDate(item.ngayCap));
        ngayHetHanField.setText(UiUtil.formatDate(item.ngayHetHan));
        cardStatusField.setText(UserDetailDialog.cardStatusText(item.trangThaiThe));
        cardStatusBox.setSelectedIndex(STATUS_LOCKED.equals(item.trangThaiThe) ? 1 : 0);
        dangMuonField.setText((item.dangMuon == null ? 0 : item.dangMuon) + " cuốn");

        for (JTextField field : new JTextField[]{maNdField, hoTenField, emailField, phoneField,
                accountStatusField, maTheField, ngayCapField, ngayHetHanField, cardStatusField, dangMuonField}) {
            field.setCaretPosition(0);
        }
    }

    /** Cập nhật hiển thị theo chế độ hiện tại (xem / sửa thông tin / sửa thẻ) và trạng thái đang gửi. */
    private void applyMode() {
        boolean view = mode == Mode.VIEW;

        infoButton.setVisible(view);
        cardButton.setVisible(view);
        closeButton.setVisible(view);
        cancelButton.setVisible(!view);
        confirmButton.setVisible(!view);

        infoButton.setEnabled(!busy);
        cardButton.setEnabled(!busy && item.maThe != null); // Không có thẻ thì không sửa thẻ được
        cancelButton.setEnabled(!busy);
        confirmButton.setEnabled(!busy);
        confirmButton.setText(busy ? "Đang lưu..." : "Xác nhận");

        boolean editInfo = mode == Mode.EDIT_INFO && !busy;
        setFieldEditable(hoTenField, editInfo);
        setFieldEditable(emailField, editInfo);
        setFieldEditable(phoneField, editInfo);

        boolean editCard = mode == Mode.EDIT_CARD && !busy;
        setFieldEditable(ngayHetHanField, editCard);
        cardStatusBox.setEnabled(editCard);
        ((CardLayout) cardStatusHolder.getLayout()).show(cardStatusHolder,
                mode == Mode.EDIT_CARD ? "edit" : "view");

        UiUtil.refit(this); // Phòng khi nội dung cần chỗ lớn hơn cửa sổ hiện tại
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text == null || text.isEmpty() ? " " : text);
        statusLabel.setForeground(color != null ? color : UIManager.getColor("Label.foreground"));
        UiUtil.refit(this); // Thông báo dài có thể làm hộp thoại cần rộng hơn
    }

    private void setBusy(boolean value) {
        busy = value;
        applyMode();
    }

    private void closeIfIdle() {
        if (!busy) {
            dispose();
        }
    }

    // ==========================================================
    // CHUYỂN CHẾ ĐỘ
    // ==========================================================
    private void startEdit(Mode newMode) {
        if (busy) return;
        mode = newMode;
        setStatus(" ", null);
        applyMode();
        if (newMode == Mode.EDIT_INFO) {
            hoTenField.requestFocusInWindow();
            hoTenField.selectAll();
        } else {
            ngayHetHanField.requestFocusInWindow();
            ngayHetHanField.selectAll();
        }
    }

    /** Hủy: bỏ mọi thay đổi chưa lưu, hiển thị lại dữ liệu cũ ở chế độ chỉ đọc. */
    private void cancelEdit() {
        if (busy) return;
        mode = Mode.VIEW;
        showItem();
        setStatus(" ", null);
        applyMode();
    }

    private void confirmEdit() {
        if (busy) return;
        if (mode == Mode.EDIT_INFO) {
            submitInfo();
        } else if (mode == Mode.EDIT_CARD) {
            submitCard();
        }
    }

    private void fail(String message, JComponent field) {
        setStatus(message, ERROR_COLOR);
        field.requestFocusInWindow();
    }

    // ==========================================================
    // CẬP NHẬT THÔNG TIN (UPDATE_READER)
    // ==========================================================
    private void submitInfo() {
        final String hoTen = hoTenField.getText().trim();
        final String email = emailField.getText().trim();
        final String phone = phoneField.getText().trim();

        // Kiểm tra theo cùng luật với Backend
        if (hoTen.isEmpty() || hoTen.length() > 100) {
            fail("Họ tên không hợp lệ (không để trống, tối đa 100 ký tự).", hoTenField);
            return;
        }
        if (!email.contains("@") || email.length() > 100) {
            fail("Email không hợp lệ.", emailField);
            return;
        }
        if (!phone.matches("\\d{8,15}")) {
            fail("Số điện thoại không hợp lệ (8 đến 15 chữ số).", phoneField);
            return;
        }

        // Chỉ gửi trường nào thật sự đổi
        final String sendHoTen = hoTen.equals(nz(item.hoTen)) ? null : hoTen;
        final String sendEmail = email.equals(nz(item.email)) ? null : email;
        final String sendPhone = phone.equals(nz(item.soDienThoai)) ? null : phone;

        if (sendHoTen == null && sendEmail == null && sendPhone == null) {
            mode = Mode.VIEW;
            showItem();
            setStatus("Không có thay đổi nào.", null);
            applyMode();
            return;
        }

        setStatus(" ", null);
        setBusy(true);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().updateReader(item.maND, sendHoTen, sendEmail, sendPhone);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String message = get();
                    if (sendHoTen != null) item.hoTen = sendHoTen;
                    if (sendEmail != null) item.email = sendEmail;
                    if (sendPhone != null) item.soDienThoai = sendPhone;
                    modified = true;
                    mode = Mode.VIEW;
                    showItem();
                    setStatus(message, SUCCESS_COLOR);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    // Giữ nguyên chế độ sửa để chỉnh lại (vd. trùng email / số điện thoại)
                    setStatus((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                            + UiUtil.messageOf(cause), ERROR_COLOR);
                }
                applyMode();
            }
        };
        worker.execute();
    }

    // ==========================================================
    // CẬP NHẬT THẺ (UPDATE_CARD)
    // ==========================================================
    private void submitCard() {
        LocalDate newDate = parseDate(ngayHetHanField.getText());
        if (newDate == null) {
            fail("Ngày hết hạn sai định dạng (dd/MM/yyyy).", ngayHetHanField);
            return;
        }
        LocalDate issued = parseIsoDate(item.ngayCap);
        if (issued != null && !newDate.isAfter(issued)) {
            fail("Ngày hết hạn phải sau ngày cấp.", ngayHetHanField);
            return;
        }

        String selectedStatus = cardStatusBox.getSelectedIndex() == 1 ? STATUS_LOCKED : STATUS_ACTIVE;

        // Chỉ gửi trường nào thật sự đổi
        final String sendDate = newDate.toString().equals(item.ngayHetHan) ? null : newDate.toString();
        final String sendStatus = selectedStatus.equals(dbStatusOf(item)) ? null : selectedStatus;

        if (sendDate == null && sendStatus == null) {
            mode = Mode.VIEW;
            showItem();
            setStatus("Không có thay đổi nào.", null);
            applyMode();
            return;
        }

        final int maThe = item.maThe;
        setStatus(" ", null);
        setBusy(true);

        SwingWorker<CardUpdateResult, Void> worker = new SwingWorker<CardUpdateResult, Void>() {
            @Override
            protected CardUpdateResult doInBackground() throws Exception {
                return SocketClient.getInstance().updateCard(maThe, sendDate, sendStatus);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    CardUpdateResult result = get();

                    // Ưu tiên dữ liệu Backend trả về; thiếu thì dùng giá trị vừa gửi
                    String newExpiry = result.ngayHetHan != null ? result.ngayHetHan
                            : (sendDate != null ? sendDate : item.ngayHetHan);
                    String newDbStatus = result.trangThai != null ? result.trangThai
                            : (sendStatus != null ? sendStatus : dbStatusOf(item));

                    item.ngayHetHan = newExpiry;
                    item.trangThaiThe = displayStatus(newDbStatus, newExpiry);
                    modified = true;
                    mode = Mode.VIEW;
                    showItem();
                    setStatus("Cập nhật thẻ độc giả thành công!", SUCCESS_COLOR);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    setStatus((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                            + UiUtil.messageOf(cause), ERROR_COLOR);
                }
                applyMode();
            }
        };
        worker.execute();
    }

    /** Trạng thái thẻ lưu trong CSDL (ACTIVE | LOCKED) suy ra từ trạng thái hiển thị (có thêm EXPIRED). */
    private static String dbStatusOf(UserListResult.Item item) {
        return STATUS_LOCKED.equals(item.trangThaiThe) ? STATUS_LOCKED : STATUS_ACTIVE;
    }

    /** Trạng thái hiển thị: bị khóa -> LOCKED; còn lại quá hạn -> EXPIRED; ngược lại ACTIVE. */
    private static String displayStatus(String dbStatus, String expiryIso) {
        if (STATUS_LOCKED.equals(dbStatus)) return STATUS_LOCKED;
        LocalDate expiry = parseIsoDate(expiryIso);
        return (expiry != null && expiry.isBefore(LocalDate.now())) ? "EXPIRED" : STATUS_ACTIVE;
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_INPUT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDate parseIsoDate(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
