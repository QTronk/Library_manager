package library.ui;

import library.model.AddCopiesResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hộp thoại "Nhập cuốn sách vào kho" (UC-12, action ADD_BOOK_COPIES) dành cho Thủ thư.
 *
 * - Nhập cho MỘT đầu sách (đã biết trước). Mỗi cuốn vật lý có một mã vạch riêng, ghi mỗi dòng một mã
 *   (máy quét mã vạch tự xuống dòng sau mỗi lần quét; cũng có thể dán nhiều mã tách bằng dòng mới, dấu phẩy hoặc dấu chấm phẩy).
 * - Cuốn mới luôn có trạng thái AVAILABLE; "Tình trạng vật lý" chọn sẵn hoặc tự gõ (mặc định "Mới").
 * - Backend làm "tất cả hoặc không": nếu có mã đã tồn tại thì không cuốn nào được nhập. Lỗi hiện ngay trong hộp thoại
 *   (mã bị báo trùng được bôi đen trong ô nhập) để sửa và gửi lại.
 * - Các luật kiểm tra ở Client trùng với Backend: danh sách không rỗng, mỗi mã tối đa 50 ký tự, không trùng nhau.
 *
 * Sau khi hộp thoại đóng, gọi getResult(): khác null nghĩa là đã nhập thành công.
 */
public class AddCopiesDialog extends JDialog {

    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final int MAX_BARCODE_LENGTH = 50;   // Cột MaCuonSach (50)
    private static final int MAX_CONDITION_LENGTH = 100; // Cột TinhTrangVatLy (100)
    private static final String[] CONDITIONS = {"Mới", "Tốt", "Cũ", "Hỏng nhẹ"};
    private static final Pattern SPLIT = Pattern.compile("[\\r\\n,;]+");
    private static final Pattern EXISTS = Pattern.compile("tồn tại:\\s*(.+?)\\s*$");

    private final int maDauSach;
    private final JTextArea codesArea = new JTextArea(7, 28);
    private final JComboBox<String> conditionBox = new JComboBox<>(CONDITIONS);
    private final JLabel countLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton cancelButton = new JButton("Hủy");
    private final JButton confirmButton = new JButton("Nhập kho");

    private AddCopiesResult result = null;
    private boolean busy = false;

    public AddCopiesDialog(Window owner, int maDauSach, String tenSach) {
        super(owner, "Nhập cuốn sách vào kho", Dialog.ModalityType.APPLICATION_MODAL);
        this.maDauSach = maDauSach;

        conditionBox.setEditable(true);
        conditionBox.setSelectedItem(CONDITIONS[0]);

        codesArea.setLineWrap(false);
        codesArea.putClientProperty("JTextField.placeholderText", "BARCODE-10\nBARCODE-11\n...");
        // Tab chuyển ô thay vì chèn ký tự Tab vào danh sách mã
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        codesArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                fm.getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        codesArea.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                fm.getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
        JScrollPane codesScroll = new JScrollPane(codesArea);

        JLabel bookLabel = new JLabel("<html><div style='width:330px'><b>" + maDauSach + "</b> - "
                + UiUtil.html(tenSach == null ? "" : tenSach) + "</div></html>");

        JLabel hint = new JLabel("<html><div style='width:330px;color:gray'>Mỗi dòng một mã vạch (quét mã vạch sẽ tự xuống dòng). "
                + "Có thể dán nhiều mã, tách bằng dòng mới, dấu phẩy hoặc dấu chấm phẩy.</div></html>");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.NORTHWEST;

        int row = 0;
        addRow(form, c, row++, "Đầu sách:", bookLabel, false);
        addRow(form, c, row++, "Mã cuốn sách:", codesScroll, true);

        c.gridx = 1; c.gridy = row++; c.gridwidth = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        c.insets = new Insets(0, 5, 5, 5);
        form.add(hint, c);

        c.gridx = 1; c.gridy = row++; c.insets = new Insets(0, 5, 8, 5);
        form.add(countLabel, c);

        c.insets = new Insets(5, 5, 5, 5);
        addRow(form, c, row++, "Tình trạng vật lý:", conditionBox, false);

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
        for (JButton button : new JButton[]{cancelButton, confirmButton}) {
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            button.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        }

        // Ctrl+Enter gửi luôn (Enter trong ô nhập là xuống dòng cho mã tiếp theo)
        codesArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "submit");
        codesArea.getActionMap().put("submit", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                submit();
            }
        });
        getRootPane().registerKeyboardAction(e -> closeIfIdle(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        codesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateCount(); }
            @Override public void removeUpdate(DocumentEvent e) { updateCount(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCount(); }
        });
        updateCount();

        // Không cho đóng cửa sổ khi đang gửi yêu cầu (nếu không sẽ không biết kết quả)
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfIdle();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                codesArea.requestFocusInWindow();
            }
        });

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Sau khi hộp thoại đóng: kết quả nhập kho, hoặc null nếu chưa nhập. */
    public AddCopiesResult getResult() {
        return result;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field, boolean fillBoth) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        form.add(l, c);
        c.gridx = 1;
        c.fill = fillBoth ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
    }

    private void closeIfIdle() {
        if (!busy) {
            dispose();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null || message.isEmpty() ? " "
                : "<html><div style='width:420px'>" + UiUtil.html(message) + "</div></html>");
        UiUtil.refit(this);
    }

    private void updateCount() {
        int n = parseCodes().size();
        countLabel.setText(n == 0 ? "Chưa có mã nào." : "Sẽ nhập " + n + " cuốn.");
    }

    /** Tách ô nhập thành danh sách mã (đã cắt khoảng trắng, bỏ dòng trống), giữ nguyên thứ tự. */
    private List<String> parseCodes() {
        List<String> codes = new ArrayList<>();
        for (String part : SPLIT.split(codesArea.getText())) {
            String code = part.trim();
            if (!code.isEmpty()) codes.add(code);
        }
        return codes;
    }

    private void setBusy(boolean value) {
        busy = value;
        codesArea.setEditable(!value);
        conditionBox.setEnabled(!value);
        cancelButton.setEnabled(!value);
        confirmButton.setEnabled(!value);
        confirmButton.setText(value ? "Đang nhập..." : "Nhập kho");
    }

    /** Bôi đen mã vạch đầu tiên trong ô nhập có tên đúng bằng code (để thủ thư thấy ngay mã bị lỗi). */
    private void highlight(String code) {
        String text = codesArea.getText();
        Matcher m = Pattern.compile("(?m)(?<=^|[,;])\\s*" + Pattern.quote(code) + "\\s*(?=$|[,;])").matcher(text);
        if (m.find()) {
            String found = m.group();
            int start = m.start() + found.indexOf(code);
            codesArea.requestFocusInWindow();
            codesArea.select(start, start + code.length());
        }
    }

    private void submit() {
        if (busy) return;

        final List<String> codes = parseCodes();
        if (codes.isEmpty()) {
            showError("Danh sách mã cuốn sách trống! Hãy nhập ít nhất một mã vạch.");
            codesArea.requestFocusInWindow();
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            if (code.length() > MAX_BARCODE_LENGTH) {
                showError("Mã cuốn sách tối đa " + MAX_BARCODE_LENGTH + " ký tự: " + code);
                highlight(code);
                return;
            }
            // So sánh không phân biệt hoa thường vì CSDL coi "abc" và "ABC" là cùng một mã
            if (!seen.add(code.toLowerCase(Locale.ROOT))) {
                showError("Mã cuốn sách đã tồn tại: " + code + " (bị trùng ngay trong danh sách đang nhập)");
                highlight(code);
                return;
            }
        }

        Object selected = conditionBox.getEditor().getItem();
        String condition = selected == null ? "" : selected.toString().trim();
        if (condition.isEmpty()) condition = CONDITIONS[0];
        if (condition.length() > MAX_CONDITION_LENGTH) {
            showError("Tình trạng vật lý tối đa " + MAX_CONDITION_LENGTH + " ký tự.");
            conditionBox.requestFocusInWindow();
            return;
        }
        final String finalCondition = condition;

        showError(null);
        setBusy(true);

        SwingWorker<AddCopiesResult, Void> worker = new SwingWorker<AddCopiesResult, Void>() {
            @Override
            protected AddCopiesResult doInBackground() throws Exception {
                return SocketClient.getInstance().addBookCopies(maDauSach, codes, finalCondition);
            }

            @Override
            protected void done() {
                try {
                    result = get();
                    setBusy(false);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setBusy(false);
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    setBusy(false); // Cho phép sửa danh sách và gửi lại (Backend không nhập cuốn nào khi có lỗi)
                    String message = UiUtil.messageOf(cause);
                    showError((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "") + message);
                    Matcher m = EXISTS.matcher(message);
                    if (m.find()) highlight(m.group(1));
                }
            }
        };
        worker.execute();
    }
}
