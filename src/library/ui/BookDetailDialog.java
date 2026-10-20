package library.ui;

import library.model.AddCopiesResult;
import library.model.BookDetail;
import library.model.Categories;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Hộp thoại chi tiết một đầu sách dành cho Thủ thư (UC-12). Mặc định CHỈ ĐỌC, có hai chức năng:
 *  - "Cập nhật thông tin": sửa Tên sách, Tác giả, Thể loại, Nhà xuất bản, Giá tiền, Vị trí kệ (UPDATE_BOOK_TITLE),
 *    rồi "Xác nhận" hoặc "Hủy". Chỉ gửi những trường thật sự bị đổi.
 *  - "Xóa đầu sách": hỏi xác nhận rồi gửi DELETE_BOOK_TITLE. Backend chỉ cho xóa khi chưa từng có giao dịch
 *    mượn / đặt trước; nếu không xóa được thì hiện đúng lý do của Backend ngay trong hộp thoại.
 *  - "Nhập thêm cuốn sách": mở AddCopiesDialog để nhập các cuốn vật lý (mã vạch) vào kho (ADD_BOOK_COPIES);
 *    số lượng hiển thị được cập nhật ngay theo số liệu mới của Backend.
 * Số lượng cuốn chỉ để xem; sửa tình trạng từng cuốn vật lý sẽ làm riêng.
 *
 * Backend chỉ trả TÊN tác giả / thể loại / NXB chứ không trả mã, nên các ô chọn được chọn sẵn bằng cách đối chiếu tên
 * với danh mục. Nếu không đối chiếu được thì ô chọn để ở "(Giữ nguyên)" và trường đó không bị gửi đi.
 * Sau khi đóng, gọi isModified() để biết có cần tải lại danh sách không.
 */
public class BookDetailDialog extends JDialog {

    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final String KEEP = "(Giữ nguyên)";

    private final BookDetail detail;
    private final NumberFormat moneyFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));

    // Mã (theo danh mục) tương ứng với dữ liệu đang lưu; null nếu không đối chiếu được. Cập nhật lại sau mỗi lần lưu.
    private Integer matchedAuthor;
    private Integer matchedCategory;
    private Integer matchedPublisher;

    private final JTextField maField = newField();
    private final JTextField tenSachField = newField();
    private final JTextField authorField = newField();
    private final JTextField categoryField = newField();
    private final JTextField publisherField = newField();
    private final JTextField giaField = newField();
    private final JTextField viTriField = newField();
    private final JTextField tongField = newField();
    private final JTextField khaDungField = newField();

    private final JComboBox<CatalogOption> authorBox;
    private final JComboBox<CatalogOption> categoryBox;
    private final JComboBox<CatalogOption> publisherBox;
    private final JPanel authorHolder = new JPanel(new CardLayout());
    private final JPanel categoryHolder = new JPanel(new CardLayout());
    private final JPanel publisherHolder = new JPanel(new CardLayout());

    private final JButton addCopiesButton = new JButton("Nhập thêm cuốn sách");
    private final JButton editButton = new JButton("Cập nhật thông tin");
    private final JButton deleteButton = new JButton("Xóa đầu sách");
    private final JButton closeButton = new JButton("Đóng");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Xác nhận");
    private final JLabel statusLabel = new JLabel(" ");

    private boolean editing = false;  // Đang ở chế độ sửa?
    private boolean busy = false;     // Đang chờ Backend trả lời
    private boolean modified = false; // Đã có thay đổi (sửa hoặc xóa) được lưu thành công

    public BookDetailDialog(Window owner, BookDetail detail, Categories categories) {
        super(owner, "Chi tiết đầu sách", Dialog.ModalityType.APPLICATION_MODAL);
        this.detail = detail;

        // Đối chiếu tên -> mã để chọn sẵn trong các ô chọn
        matchedAuthor = matchAuthor(categories, detail.tacGia);
        matchedCategory = matchCategory(categories, detail.theLoai);
        matchedPublisher = matchPublisher(categories, detail.nhaXuatBan, detail.namXuatBan);

        authorBox = new JComboBox<>(CatalogOption.authors(categories, KEEP));
        categoryBox = new JComboBox<>(CatalogOption.categories(categories, KEEP));
        publisherBox = new JComboBox<>(CatalogOption.publishers(categories, KEEP));
        select(authorBox, matchedAuthor);
        select(categoryBox, matchedCategory);
        select(publisherBox, matchedPublisher);

        // Ô chọn cùng bề rộng với ô chữ để bố cục không nhảy khi đổi chế độ
        Dimension width = tenSachField.getPreferredSize();
        for (JComboBox<CatalogOption> box : java.util.List.of(authorBox, categoryBox, publisherBox)) {
            box.setPreferredSize(new Dimension(width.width, box.getPreferredSize().height));
        }

        authorHolder.add(authorField, "view");
        authorHolder.add(authorBox, "edit");
        categoryHolder.add(categoryField, "view");
        categoryHolder.add(categoryBox, "edit");
        publisherHolder.add(publisherField, "view");
        publisherHolder.add(publisherBox, "edit");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Mã đầu sách:", maField);
        addRow(form, c, row++, "Tên sách:", tenSachField);
        addRow(form, c, row++, "Tác giả:", authorHolder);
        addRow(form, c, row++, "Thể loại:", categoryHolder);
        addRow(form, c, row++, "Nhà xuất bản:", publisherHolder);
        addRow(form, c, row++, "Giá tiền (VNĐ):", giaField);
        addRow(form, c, row++, "Vị trí kệ:", viTriField);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(new JSeparator(), c);

        addRow(form, c, row++, "Tổng số cuốn:", tongField);
        addRow(form, c, row++, "Đang sẵn sàng:", khaDungField);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(statusLabel, c);

        deleteButton.setForeground(ERROR_COLOR);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(addCopiesButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(closeButton);
        buttons.add(cancelButton);
        buttons.add(confirmButton);
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 4, 0, 4);
        form.add(buttons, c);

        setContentPane(form);

        addCopiesButton.addActionListener(e -> openAddCopies());
        editButton.addActionListener(e -> startEdit());
        deleteButton.addActionListener(e -> confirmDelete());
        closeButton.addActionListener(e -> closeIfIdle());
        cancelButton.addActionListener(e -> cancelEdit());
        confirmButton.addActionListener(e -> submit());

        // Enter chuyển ô tuần tự khi đang sửa: tên sách -> (chọn) -> giá -> vị trí -> Xác nhận
        tenSachField.addActionListener(e -> { if (editing) authorBox.requestFocusInWindow(); });
        giaField.addActionListener(e -> { if (editing) viTriField.requestFocusInWindow(); });
        viTriField.addActionListener(e -> { if (editing) confirmButton.requestFocusInWindow(); });

        for (JButton button : new JButton[]{addCopiesButton, editButton, deleteButton, closeButton, cancelButton, confirmButton}) {
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        }

        // Esc: đang sửa thì hủy sửa, còn không thì đóng hộp thoại
        getRootPane().registerKeyboardAction(e -> {
            if (editing) {
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

    /** Có thay đổi nào (sửa hoặc xóa) đã được lưu thành công không (để màn hình danh sách tải lại). */
    public boolean isModified() {
        return modified;
    }

    // ==========================================================
    // ĐỐI CHIẾU TÊN -> MÃ
    // ==========================================================
    private static Integer matchAuthor(Categories c, String name) {
        if (name == null) return null;
        for (Categories.Author a : c.tacGiaList) {
            if (name.trim().equalsIgnoreCase(nz(a.tenTacGia).trim())) return a.maTG;
        }
        return null;
    }

    private static Integer matchCategory(Categories c, String name) {
        if (name == null) return null;
        for (Categories.Category t : c.theLoaiList) {
            if (name.trim().equalsIgnoreCase(nz(t.tenTL).trim())) return t.maTL;
        }
        return null;
    }

    /** Khớp theo tên và, nếu Backend có trả, cả năm xuất bản (có thể có hai NXB trùng tên khác năm). */
    private static Integer matchPublisher(Categories c, String name, Integer year) {
        if (name == null) return null;
        Integer byNameOnly = null;
        for (Categories.Publisher p : c.nhaXuatBanList) {
            if (!name.trim().equalsIgnoreCase(nz(p.tenNXB).trim())) continue;
            if (year == null || year.equals(p.namXuatBan)) return p.maNXB;
            if (byNameOnly == null) byNameOnly = p.maNXB;
        }
        return year == null ? byNameOnly : null; // Có năm mà không khớp năm nào thì coi như không đối chiếu được
    }

    private static void select(JComboBox<CatalogOption> box, Integer id) {
        box.setSelectedIndex(0); // "(Giữ nguyên)"
        if (id == null) return;
        for (int i = 1; i < box.getItemCount(); i++) {
            if (id.equals(box.getItemAt(i).id)) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    // ==========================================================
    // DỰNG GIAO DIỆN
    // ==========================================================

    /**
     * Ô chữ có viền gốc (hiện khi cho sửa) và viền "phẳng" (khi chỉ đọc) với CÙNG khoảng đệm, để chiều cao ô
     * không đổi khi chuyển chế độ (đổi chiều cao sẽ làm vỡ bố cục hộp thoại).
     */
    private static JTextField newField() {
        JTextField field = new JTextField(28);

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
        c.insets = new Insets(4, 4, 4, 4);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        form.add(l, c);
        c.gridx = 1;
        form.add(field, c);
    }

    /** Đổ dữ liệu đầu sách lên các ô. */
    private void showItem() {
        maField.setText(String.valueOf(detail.maDauSach));
        tenSachField.setText(nz(detail.tenSach));
        authorField.setText(nz(detail.tacGia));
        categoryField.setText(nz(detail.theLoai));
        publisherField.setText(CatalogOption.publisherLabel(detail.nhaXuatBan, detail.namXuatBan));
        giaField.setText(moneyFormat.format(detail.giaTien));
        viTriField.setText(nz(detail.viTri));
        tongField.setText(detail.tongSoLuong + " cuốn");
        khaDungField.setText(detail.soLuongKhaDung + " cuốn");

        select(authorBox, matchedAuthor);
        select(categoryBox, matchedCategory);
        select(publisherBox, matchedPublisher);

        for (JTextField field : new JTextField[]{maField, tenSachField, authorField, categoryField,
                publisherField, giaField, viTriField, tongField, khaDungField}) {
            field.setCaretPosition(0);
        }
    }

    /** Cập nhật hiển thị theo chế độ hiện tại (xem / sửa) và trạng thái đang gửi. */
    private void applyMode() {
        boolean view = !editing;

        addCopiesButton.setVisible(view);
        editButton.setVisible(view);
        deleteButton.setVisible(view);
        closeButton.setVisible(view);
        cancelButton.setVisible(editing);
        confirmButton.setVisible(editing);

        addCopiesButton.setEnabled(!busy);
        editButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
        closeButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        confirmButton.setEnabled(!busy);
        confirmButton.setText(busy ? "Đang lưu..." : "Xác nhận");

        boolean edit = editing && !busy;
        setFieldEditable(tenSachField, edit);
        setFieldEditable(giaField, edit);
        setFieldEditable(viTriField, edit);
        authorBox.setEnabled(edit);
        categoryBox.setEnabled(edit);
        publisherBox.setEnabled(edit);

        String card = editing ? "edit" : "view";
        ((CardLayout) authorHolder.getLayout()).show(authorHolder, card);
        ((CardLayout) categoryHolder.getLayout()).show(categoryHolder, card);
        ((CardLayout) publisherHolder.getLayout()).show(publisherHolder, card);

        UiUtil.refit(this); // Phòng khi nội dung cần chỗ lớn hơn cửa sổ hiện tại
    }

    private void setStatus(String text, Color color) {
        // Bọc HTML để lý do dài của Backend tự xuống dòng thay vì làm hộp thoại quá rộng
        statusLabel.setText(text == null || text.isEmpty() ? " "
                : "<html><div style='width:380px'>" + UiUtil.html(text) + "</div></html>");
        statusLabel.setForeground(color != null ? color : UIManager.getColor("Label.foreground"));
        UiUtil.refit(this);
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
    // NHẬP CUỐN SÁCH VÀO KHO (ADD_BOOK_COPIES)
    // ==========================================================
    private void openAddCopies() {
        if (busy || editing) return;

        AddCopiesDialog dialog = new AddCopiesDialog(this, detail.maDauSach, detail.tenSach);
        dialog.setVisible(true); // Hộp thoại modal: dòng tiếp theo chạy khi hộp thoại đã đóng

        AddCopiesResult result = dialog.getResult();
        if (result == null) return; // Người dùng hủy hoặc chưa nhập được

        // Backend trả số liệu mới của đầu sách: hiển thị luôn, không cần tải lại
        detail.tongSoLuong = result.tongSoLuong;
        detail.soLuongKhaDung = result.soLuongKhaDung;
        modified = true;
        showItem();
        setStatus("Đã nhập " + result.soCuonDaThem + " cuốn vào kho.", SUCCESS_COLOR);
    }

    // ==========================================================
    // SỬA ĐẦU SÁCH (UPDATE_BOOK_TITLE)
    // ==========================================================
    private void startEdit() {
        if (busy) return;
        editing = true;
        setStatus(" ", null);
        applyMode();
        tenSachField.requestFocusInWindow();
        tenSachField.selectAll();
    }

    /** Hủy: bỏ mọi thay đổi chưa lưu, hiển thị lại dữ liệu cũ ở chế độ chỉ đọc. */
    private void cancelEdit() {
        if (busy) return;
        editing = false;
        showItem();
        setStatus(" ", null);
        applyMode();
    }

    private void fail(String message, JComponent field) {
        setStatus(message, ERROR_COLOR);
        field.requestFocusInWindow();
    }

    private void submit() {
        if (busy || !editing) return;

        final String tenSach = tenSachField.getText().trim();
        final String viTri = viTriField.getText().trim();

        // Kiểm tra theo cùng luật với Backend
        if (tenSach.isEmpty() || tenSach.length() > 200) {
            fail("Tên sách không hợp lệ (không để trống, tối đa 200 ký tự).", tenSachField);
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

        // Chỉ gửi trường nào thật sự đổi
        final String sendTen = tenSach.equals(nz(detail.tenSach)) ? null : tenSach;
        final Double sendGia = Double.compare(gia, detail.giaTien) == 0 ? null : gia;
        final String sendViTri = viTri.equals(nz(detail.viTri).trim()) ? null : viTri; // "" = xóa trắng vị trí
        final Integer sendAuthor = changedId(authorBox, matchedAuthor);
        final Integer sendCategory = changedId(categoryBox, matchedCategory);
        final Integer sendPublisher = changedId(publisherBox, matchedPublisher);

        if (sendTen == null && sendGia == null && sendViTri == null
                && sendAuthor == null && sendCategory == null && sendPublisher == null) {
            editing = false;
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
                return SocketClient.getInstance().updateBookTitle(detail.maDauSach, sendTen, sendGia,
                        sendAuthor, sendPublisher, sendCategory, sendViTri);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String message = get();
                    // Cập nhật dữ liệu đang hiển thị theo những gì vừa lưu
                    if (sendTen != null) detail.tenSach = sendTen;
                    if (sendGia != null) detail.giaTien = sendGia;
                    if (sendViTri != null) detail.viTri = sendViTri;
                    if (sendAuthor != null) {
                        detail.tacGia = ((CatalogOption) authorBox.getSelectedItem()).label;
                        matchedAuthor = sendAuthor;
                    }
                    if (sendCategory != null) {
                        detail.theLoai = ((CatalogOption) categoryBox.getSelectedItem()).label;
                        matchedCategory = sendCategory;
                    }
                    if (sendPublisher != null) {
                        applyPublisherFromBox(); // Nhãn NXB có kèm năm nên tách lại tên và năm
                        matchedPublisher = sendPublisher;
                    }
                    modified = true;
                    editing = false;
                    showItem();
                    setStatus(message, SUCCESS_COLOR);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    // Giữ nguyên chế độ sửa để chỉnh lại
                    setStatus((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                            + UiUtil.messageOf(cause), ERROR_COLOR);
                }
                applyMode();
            }
        };
        worker.execute();
    }

    /** Mã được chọn trong ô chọn nếu khác với mã đối chiếu ban đầu; không đổi (hoặc để "Giữ nguyên") thì null. */
    private static Integer changedId(JComboBox<CatalogOption> box, Integer matched) {
        CatalogOption selected = (CatalogOption) box.getSelectedItem();
        if (selected == null || selected.id == null) return null;
        return selected.id.equals(matched) ? null : selected.id;
    }

    /** Sau khi lưu NXB: tách tên và năm từ nhãn "Tên (năm)" của lựa chọn hiện tại. */
    private void applyPublisherFromBox() {
        String label = ((CatalogOption) publisherBox.getSelectedItem()).label;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*) \\((\\d{4})\\)$").matcher(label);
        if (m.matches()) {
            detail.nhaXuatBan = m.group(1);
            detail.namXuatBan = Integer.valueOf(m.group(2));
        } else {
            detail.nhaXuatBan = label;
            detail.namXuatBan = null;
        }
    }

    // ==========================================================
    // XÓA ĐẦU SÁCH (DELETE_BOOK_TITLE)
    // ==========================================================
    private void confirmDelete() {
        if (busy || editing) return;

        int choice = JOptionPane.showOptionDialog(this,
                "Xóa đầu sách \"" + nz(detail.tenSach) + "\" (mã " + detail.maDauSach + ")?\n\n"
                        + "Các cuốn sách thuộc đầu sách này (nếu có) cũng bị xóa theo.\n"
                        + "Hành động này không thể hoàn tác.",
                "Xác nhận xóa đầu sách", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new Object[]{"Xóa", "Hủy"}, "Hủy");
        if (choice != 0) return; // 0 = nút "Xóa"

        setStatus(" ", null);
        setBusy(true);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return SocketClient.getInstance().deleteBookTitle(detail.maDauSach);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String message = get();
                    modified = true;
                    applyMode();
                    JOptionPane.showMessageDialog(BookDetailDialog.this, message,
                            "Xóa đầu sách", JOptionPane.INFORMATION_MESSAGE);
                    dispose(); // Đầu sách không còn nữa nên đóng hộp thoại; danh sách sẽ được tải lại
                    return;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    // Vd. đầu sách đã phát sinh giao dịch: hiện đúng lý do của Backend
                    setStatus((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                            + UiUtil.messageOf(cause), ERROR_COLOR);
                }
                applyMode();
            }
        };
        worker.execute();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
