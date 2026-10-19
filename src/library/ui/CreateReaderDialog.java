package library.ui;

import library.model.AddReaderResult;
import library.service.SocketClient;

import javax.swing.*;
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
 * Hộp thoại "Tạo thẻ độc giả" (UC-11, action ADD_READER) dành cho Thủ thư.
 *
 * Điền đủ thông tin rồi bấm "Xác nhận" để gửi. Backend tạo tài khoản độc giả và thẻ trong cùng một giao dịch.
 * Các luật kiểm tra ở đây trùng với Backend để báo lỗi ngay; việc trùng email/số điện thoại chỉ Backend biết.
 * Sau khi hộp thoại đóng, gọi getCreated(): khác null nghĩa là đã tạo thành công.
 */
public class CreateReaderDialog extends JDialog {

    /** Thông tin thẻ vừa tạo (dữ liệu nhập vào cộng với mã Backend trả về). */
    public static class Created {
        public final int maND;
        public final int maThe;
        public final String hoTen;
        public final String email;
        public final String soDienThoai;
        public final String ngayCap;     // dd/MM/yyyy
        public final String ngayHetHan;  // dd/MM/yyyy

        Created(int maND, int maThe, String hoTen, String email, String soDienThoai,
                String ngayCap, String ngayHetHan) {
            this.maND = maND;
            this.maThe = maThe;
            this.hoTen = hoTen;
            this.email = email;
            this.soDienThoai = soDienThoai;
            this.ngayCap = ngayCap;
            this.ngayHetHan = ngayHetHan;
        }
    }

    private static final DateTimeFormatter DATE_INPUT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final JTextField hoTenField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JTextField phoneField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JTextField ngayCapField = new JTextField(10);
    private final JTextField ngayHetHanField = new JTextField(10);
    private final JCheckBox showCheck = new JCheckBox("Hiện mật khẩu");
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Xác nhận");
    private final char defaultEchoChar;

    private Created created = null; // Kết quả khi tạo thành công
    private boolean busy = false;   // Đang chờ Backend trả lời

    public CreateReaderDialog(Window owner) {
        super(owner, "Tạo thẻ độc giả", Dialog.ModalityType.APPLICATION_MODAL);
        defaultEchoChar = passwordField.getEchoChar();

        hoTenField.putClientProperty("JTextField.placeholderText", "VD: Nguyễn Văn A");
        emailField.putClientProperty("JTextField.placeholderText", "VD: ten@gmail.com");
        phoneField.putClientProperty("JTextField.placeholderText", "8 đến 15 chữ số");
        passwordField.putClientProperty("JTextField.placeholderText", "Tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự");
        ngayCapField.putClientProperty("JTextField.placeholderText", "dd/MM/yyyy");
        ngayHetHanField.putClientProperty("JTextField.placeholderText", "dd/MM/yyyy");
        ngayCapField.setToolTipText("Bỏ trống = hôm nay");

        // Giá trị gợi ý: cấp hôm nay, hết hạn sau 1 năm (có thể sửa)
        LocalDate today = LocalDate.now();
        ngayCapField.setText(today.format(DATE_INPUT));
        ngayHetHanField.setText(today.plusYears(1).format(DATE_INPUT));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Họ tên:", hoTenField);
        addRow(form, c, row++, "Email:", emailField);
        addRow(form, c, row++, "Số điện thoại:", phoneField);
        addRow(form, c, row++, "Mật khẩu:", passwordField);
        addRow(form, c, row++, "Ngày cấp thẻ:", ngayCapField);
        addRow(form, c, row++, "Ngày hết hạn:", ngayHetHanField);

        c.gridx = 1; c.gridy = row++; c.fill = GridBagConstraints.NONE;
        form.add(showCheck, c);

        errorLabel.setForeground(ERROR_COLOR);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(errorLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(confirmButton);
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 5, 0, 5);
        form.add(buttons, c);

        setContentPane(form);

        showCheck.addActionListener(e ->
                passwordField.setEchoChar(showCheck.isSelected() ? (char) 0 : defaultEchoChar));
        cancelButton.addActionListener(e -> closeIfIdle());
        confirmButton.addActionListener(e -> submit());

        // Enter chuyển ô tuần tự; ở ô cuối thì nhảy tới nút Xác nhận (Enter lần nữa để gửi)
        JTextField[] order = {hoTenField, emailField, phoneField, passwordField, ngayCapField, ngayHetHanField};
        for (int i = 0; i < order.length; i++) {
            final JComponent next = (i < order.length - 1) ? order[i + 1] : confirmButton;
            order[i].addActionListener(e -> next.requestFocusInWindow());
        }
        for (JButton button : new JButton[]{cancelButton, confirmButton}) {
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        }

        getRootPane().registerKeyboardAction(e -> closeIfIdle(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Không cho đóng cửa sổ khi đang gửi yêu cầu (nếu không sẽ không biết kết quả)
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfIdle();
            }
        });

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Kết quả sau khi hộp thoại đóng: khác null nếu đã tạo thẻ thành công. */
    public Created getCreated() {
        return created;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        form.add(field, c);
    }

    private void closeIfIdle() {
        if (!busy) {
            dispose();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null || message.isEmpty() ? " " : message);
    }

    private void fail(String message, JComponent field) {
        showError(message);
        field.requestFocusInWindow();
    }

    private void setBusy(boolean value) {
        busy = value;
        hoTenField.setEditable(!value);
        emailField.setEditable(!value);
        phoneField.setEditable(!value);
        passwordField.setEditable(!value);
        ngayCapField.setEditable(!value);
        ngayHetHanField.setEditable(!value);
        showCheck.setEnabled(!value);
        cancelButton.setEnabled(!value);
        confirmButton.setEnabled(!value);
        confirmButton.setText(value ? "Đang gửi..." : "Xác nhận");
    }

    /** Đọc ngày dd/MM/yyyy (chặt chẽ: không chấp nhận 31/02); sai định dạng thì trả null. */
    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_INPUT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private void submit() {
        if (busy) return;

        final String hoTen = hoTenField.getText().trim();
        final String email = emailField.getText().trim();
        final String phone = phoneField.getText().trim();
        final String password = new String(passwordField.getPassword());
        String capText = ngayCapField.getText().trim();
        String hetHanText = ngayHetHanField.getText().trim();

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
        if (password.length() < MIN_PASSWORD_LENGTH) {
            fail("Mật khẩu không hợp lệ (tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự)!", passwordField);
            return;
        }

        LocalDate ngayCap;
        if (capText.isEmpty()) {
            ngayCap = LocalDate.now(); // Bỏ trống = hôm nay (Backend cũng làm vậy)
        } else {
            ngayCap = parseDate(capText);
            if (ngayCap == null) {
                fail("Ngày cấp sai định dạng (dd/MM/yyyy).", ngayCapField);
                return;
            }
        }
        LocalDate ngayHetHan = parseDate(hetHanText);
        if (ngayHetHan == null) {
            fail("Ngày hết hạn sai định dạng (dd/MM/yyyy).", ngayHetHanField);
            return;
        }
        if (!ngayHetHan.isAfter(ngayCap)) {
            fail("Ngày hết hạn phải sau ngày cấp.", ngayHetHanField);
            return;
        }

        final String ngayCapIso = capText.isEmpty() ? null : ngayCap.toString(); // yyyy-MM-dd
        final String ngayHetHanIso = ngayHetHan.toString();
        final String ngayCapShow = ngayCap.format(DATE_INPUT);
        final String ngayHetHanShow = ngayHetHan.format(DATE_INPUT);

        showError(null);
        setBusy(true);

        SwingWorker<AddReaderResult, Void> worker = new SwingWorker<AddReaderResult, Void>() {
            @Override
            protected AddReaderResult doInBackground() throws Exception {
                return SocketClient.getInstance().addReader(hoTen, email, phone, password, ngayCapIso, ngayHetHanIso);
            }

            @Override
            protected void done() {
                try {
                    AddReaderResult result = get();
                    setBusy(false);
                    created = new Created(result.maND, result.maThe, hoTen, email, phone,
                            ngayCapShow, ngayHetHanShow);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setBusy(false);
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    String message = UiUtil.messageOf(cause);

                    setBusy(false); // Cho phép sửa và gửi lại
                    // Lỗi từ Backend (vd. trùng email/số điện thoại) hiện ngay trong hộp thoại
                    fail((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "") + message,
                            message.contains("tồn tại") ? emailField : hoTenField);
                }
            }
        };
        worker.execute();
    }
}
