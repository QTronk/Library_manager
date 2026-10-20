package library.ui;

import library.model.Categories;
import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutionException;

/**
 * Hộp thoại "Thêm đầu sách" (UC-12, action ADD_BOOK_TITLE) dành cho Thủ thư.
 *
 * Backend bắt buộc có Tên sách, Giá tiền, Tác giả, Thể loại và Nhà xuất bản (Vị trí kệ thì tùy chọn). Đầu sách mới có
 * số lượng 0: các cuốn sách vật lý được nhập riêng sau. Các luật kiểm tra ở đây trùng với Backend để báo lỗi ngay.
 * Sau khi hộp thoại đóng, gọi getCreatedId(): khác null nghĩa là đã tạo thành công.
 */
public class AddBookDialog extends JDialog {

    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private final JTextField tenSachField = new JTextField(28);
    private final JTextField giaField = new JTextField(28);
    private final JComboBox<CatalogOption> authorBox;
    private final JComboBox<CatalogOption> categoryBox;
    private final JComboBox<CatalogOption> publisherBox;
    private final JTextField viTriField = new JTextField(28);
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Xác nhận");

    private Integer createdId = null; // Mã đầu sách vừa tạo
    private boolean busy = false;     // Đang chờ Backend trả lời

    public AddBookDialog(Window owner, Categories categories) {
        super(owner, "Thêm đầu sách", Dialog.ModalityType.APPLICATION_MODAL);

        authorBox = new JComboBox<>(CatalogOption.authors(categories, "-- Chọn tác giả --"));
        categoryBox = new JComboBox<>(CatalogOption.categories(categories, "-- Chọn thể loại --"));
        publisherBox = new JComboBox<>(CatalogOption.publishers(categories, "-- Chọn nhà xuất bản --"));

        // Ba ô chọn cùng bề rộng với ô nhập chữ để form gọn
        Dimension width = new Dimension(tenSachField.getPreferredSize().width, authorBox.getPreferredSize().height);
        for (JComboBox<CatalogOption> box : java.util.List.of(authorBox, categoryBox, publisherBox)) {
            box.setPreferredSize(width);
        }

        tenSachField.putClientProperty("JTextField.placeholderText", "VD: Lập trình Java nâng cao");
        giaField.putClientProperty("JTextField.placeholderText", "VD: 150000 (VNĐ)");
        viTriField.putClientProperty("JTextField.placeholderText", "VD: Kệ B1 (không bắt buộc)");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Tên sách:", tenSachField);
        addRow(form, c, row++, "Tác giả:", authorBox);
        addRow(form, c, row++, "Thể loại:", categoryBox);
        addRow(form, c, row++, "Nhà xuất bản:", publisherBox);
        addRow(form, c, row++, "Giá tiền (VNĐ):", giaField);
        addRow(form, c, row++, "Vị trí kệ:", viTriField);

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

        cancelButton.addActionListener(e -> closeIfIdle());
        confirmButton.addActionListener(e -> submit());

        // Enter chuyển ô tuần tự: tên sách -> giá -> vị trí -> nút Xác nhận (Enter lần nữa để gửi)
        tenSachField.addActionListener(e -> authorBox.requestFocusInWindow());
        giaField.addActionListener(e -> viTriField.requestFocusInWindow());
        viTriField.addActionListener(e -> confirmButton.requestFocusInWindow());
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

    /** Sau khi hộp thoại đóng: mã đầu sách vừa tạo, hoặc null nếu chưa tạo. */
    public Integer getCreatedId() {
        return createdId;
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
        UiUtil.refit(this); // Dòng lỗi dài có thể làm hộp thoại cần rộng hơn
    }

    private void fail(String message, JComponent field) {
        showError(message);
        field.requestFocusInWindow();
    }

    private void setBusy(boolean value) {
        busy = value;
        tenSachField.setEditable(!value);
        giaField.setEditable(!value);
        viTriField.setEditable(!value);
        authorBox.setEnabled(!value);
        categoryBox.setEnabled(!value);
        publisherBox.setEnabled(!value);
        cancelButton.setEnabled(!value);
        confirmButton.setEnabled(!value);
        confirmButton.setText(value ? "Đang gửi..." : "Xác nhận");
    }

    private void submit() {
        if (busy) return;

        final String tenSach = tenSachField.getText().trim();
        final String viTri = viTriField.getText().trim();
        final CatalogOption author = (CatalogOption) authorBox.getSelectedItem();
        final CatalogOption category = (CatalogOption) categoryBox.getSelectedItem();
        final CatalogOption publisher = (CatalogOption) publisherBox.getSelectedItem();

        if (tenSach.isEmpty() || tenSach.length() > 200) {
            fail("Tên sách không hợp lệ (không để trống, tối đa 200 ký tự).", tenSachField);
            return;
        }
        if (author == null || author.id == null) {
            fail("Vui lòng chọn tác giả.", authorBox);
            return;
        }
        if (category == null || category.id == null) {
            fail("Vui lòng chọn thể loại.", categoryBox);
            return;
        }
        if (publisher == null || publisher.id == null) {
            fail("Vui lòng chọn nhà xuất bản.", publisherBox);
            return;
        }
        final Double gia = CatalogOption.parseMoney(giaField.getText());
        if (gia == null) {
            fail("Giá tiền không hợp lệ (chỉ nhập số nguyên không âm, ví dụ 150000).", giaField);
            return;
        }
        if (viTri.length() > 100) {
            fail("Vị trí kệ tối đa 100 ký tự.", viTriField);
            return;
        }

        showError(null);
        setBusy(true);

        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return SocketClient.getInstance().addBookTitle(tenSach, gia, author.id, publisher.id, category.id,
                        viTri.isEmpty() ? null : viTri);
            }

            @Override
            protected void done() {
                try {
                    createdId = get();
                    setBusy(false);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setBusy(false);
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    setBusy(false); // Cho phép sửa và gửi lại
                    showError((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "") + UiUtil.messageOf(cause));
                }
            }
        };
        worker.execute();
    }
}
