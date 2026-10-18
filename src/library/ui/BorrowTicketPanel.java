package library.ui;

import library.model.BorrowTicketResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Lập phiếu mượn" dành cho Thủ thư (UC-01).
 *
 * Mỗi lần bấm "+ Tạo phiếu mới" sẽ thêm một khung chữ nhật (TicketCard) lên ĐẦU danh sách.
 * Mỗi khung gồm 2 ô nhập (Mã thẻ, Mã vạch cuốn sách) và nút "Hoàn thành":
 *  - Nút chỉ bật khi cả 2 ô hợp lệ.
 *  - Bấm nút -> gửi CREATE_BORROW_TICKET (1 phiếu = 1 cuốn sách).
 *  - Thành công: nút đổi thành "Đã lập đơn" và bị khóa, khung được giữ lại (chỉ mất khi tắt ứng dụng).
 *  - Thất bại: hiện thông báo lỗi của Backend, khung vẫn sửa và gửi lại được.
 */
public class BorrowTicketPanel extends JPanel {

    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color SUCCESS_COLOR = new Color(16, 185, 129); // Emerald, cùng màu chủ đạo của ứng dụng

    private final JPanel listPanel = new JPanel();
    private final JLabel emptyHint = new JLabel("Bấm \"+ Tạo phiếu mới\" để bắt đầu lập phiếu mượn.");
    private JScrollPane scrollPane;
    private int cardCounter = 0;

    public BorrowTicketPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Lập phiếu mượn");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JButton newButton = new JButton("+ Tạo phiếu mới");
        newButton.addActionListener(e -> addNewCard());

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.WEST);
        top.add(newButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Danh sách khung xếp dọc, khung mới nhất nằm trên cùng
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        emptyHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        emptyHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        listPanel.add(emptyHint);

        // Bọc vào NORTH để các khung không bị kéo giãn ra hết chiều cao màn hình
        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.add(listPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    /** Thêm một khung phiếu mới lên đầu danh sách. */
    private void addNewCard() {
        if (emptyHint.getParent() != null) {
            listPanel.remove(emptyHint);
        }

        cardCounter++;
        TicketCard card = new TicketCard(cardCounter);

        listPanel.add(card, 0);
        listPanel.add(Box.createVerticalStrut(10), 1);
        listPanel.revalidate();
        listPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            scrollPane.getViewport().setViewPosition(new Point(0, 0));
            card.focusFirstField();
        });
    }

    private static String formatDate(String iso) {
        if (iso == null) return "";
        try {
            return LocalDate.parse(iso).format(DATE_VN);
        } catch (Exception ex) {
            return iso; // Định dạng lạ thì hiển thị nguyên văn
        }
    }

    /** Escape tối thiểu để chèn text vào JLabel dạng HTML. */
    private static String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==========================================================
    // MỘT KHUNG PHIẾU MƯỢN
    // ==========================================================
    private class TicketCard extends JPanel {
        private final JTextField maTheField = new JTextField(10);
        private final JTextField barcodeField = new JTextField(18);
        private final JButton submitButton = new JButton("Hoàn thành");
        private final JLabel resultLabel = new JLabel();
        private final TitledBorder titledBorder;

        private boolean submitting = false; // Đang chờ Backend trả lời
        private boolean completed = false;  // Đã lập phiếu thành công

        TicketCard(int index) {
            setLayout(new GridBagLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);

            titledBorder = BorderFactory.createTitledBorder("Phiếu mới #" + index);
            setBorder(BorderFactory.createCompoundBorder(
                    titledBorder,
                    BorderFactory.createEmptyBorder(6, 10, 10, 10)));

            maTheField.putClientProperty("JTextField.placeholderText", "VD: 1");
            barcodeField.putClientProperty("JTextField.placeholderText", "VD: BS-1-02");

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0; c.gridy = 0;
            add(new JLabel("Mã thẻ độc giả:"), c);
            c.gridx = 1;
            add(maTheField, c);
            c.gridx = 2;
            add(new JLabel("Mã vạch cuốn sách:"), c);
            c.gridx = 3;
            add(barcodeField, c);

            c.gridx = 4; c.weightx = 1.0; c.anchor = GridBagConstraints.EAST;
            add(submitButton, c);

            resultLabel.setForeground(SUCCESS_COLOR);
            resultLabel.setVisible(false);
            c.gridx = 0; c.gridy = 1; c.gridwidth = 5; c.weightx = 1.0;
            c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
            add(resultLabel, c);

            DocumentListener listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { refreshSubmitState(); }
                @Override public void removeUpdate(DocumentEvent e) { refreshSubmitState(); }
                @Override public void changedUpdate(DocumentEvent e) { refreshSubmitState(); }
            };
            maTheField.getDocument().addDocumentListener(listener);
            barcodeField.getDocument().addDocumentListener(listener);

            // Enter ở ô Mã thẻ -> nhảy sang ô Mã vạch; Enter ở ô Mã vạch (máy quét mã vạch tự gõ Enter) -> gửi
            maTheField.addActionListener(e -> barcodeField.requestFocusInWindow());
            barcodeField.addActionListener(e -> {
                if (submitButton.isEnabled()) {
                    submit();
                }
            });
            submitButton.addActionListener(e -> submit());

            refreshSubmitState();
        }

        // Chiều cao tối đa = chiều cao cần thiết, để BoxLayout không kéo giãn khung theo chiều dọc
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        void focusFirstField() {
            maTheField.requestFocusInWindow();
        }

        /** Mã thẻ hợp lệ = chỉ gồm 1-9 chữ số; ngược lại trả null. */
        private Integer parseMaThe() {
            String text = maTheField.getText().trim();
            if (!text.matches("\\d{1,9}")) return null;
            return Integer.valueOf(text);
        }

        private void refreshSubmitState() {
            if (completed) return;

            boolean maTheInvalid = !maTheField.getText().trim().isEmpty() && parseMaThe() == null;
            maTheField.putClientProperty("JComponent.outline", maTheInvalid ? "error" : null);

            boolean ready = parseMaThe() != null && !barcodeField.getText().trim().isEmpty();
            submitButton.setEnabled(ready && !submitting);
        }

        private void setSubmitting(boolean value) {
            submitting = value;
            maTheField.setEditable(!value);
            barcodeField.setEditable(!value);
            submitButton.setText(value ? "Đang gửi..." : "Hoàn thành");
            refreshSubmitState();
        }

        private void submit() {
            if (submitting || completed) return;

            final Integer maThe = parseMaThe();
            final String barcode = barcodeField.getText().trim();
            if (maThe == null || barcode.isEmpty()) return;

            setSubmitting(true);

            SwingWorker<BorrowTicketResult, Void> worker = new SwingWorker<>() {
                @Override
                protected BorrowTicketResult doInBackground() throws Exception {
                    return SocketClient.getInstance().createBorrowTicket(maThe, barcode);
                }

                @Override
                protected void done() {
                    try {
                        markCompleted(get());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        setSubmitting(false);
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        boolean connectionProblem = cause instanceof IOException;
                        String message = cause.getMessage() != null && !cause.getMessage().trim().isEmpty()
                                ? cause.getMessage()
                                : "Đã xảy ra lỗi không xác định.";

                        setSubmitting(false); // Cho phép sửa và gửi lại
                        JOptionPane.showMessageDialog(TicketCard.this, message,
                                connectionProblem ? "Lỗi kết nối" : "Lập phiếu thất bại",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }

        /** Lập phiếu thành công: khóa khung lại và hiển thị kết quả, khung được giữ nguyên. */
        private void markCompleted(BorrowTicketResult result) {
            completed = true;
            submitting = false;

            maTheField.setEditable(false);
            barcodeField.setEditable(false);
            maTheField.putClientProperty("JComponent.outline", null);

            submitButton.setText("Đã lập đơn");
            submitButton.setEnabled(false);

            titledBorder.setTitle("Phiếu mượn #" + result.maPhieuMuon);

            StringBuilder text = new StringBuilder("<html>Lập phiếu thành công.");
            if (result.chiTiet != null && !result.chiTiet.isEmpty()) {
                BorrowTicketResult.Line line = result.chiTiet.get(0);
                text.append(" Sách: <b>").append(html(line.tenSach)).append("</b>")
                    .append(" (").append(html(line.maCuonSach)).append(")")
                    .append(" - Hẹn trả: <b>").append(formatDate(line.ngayHenTra)).append("</b>");
            }
            text.append("</html>");
            resultLabel.setText(text.toString());
            resultLabel.setVisible(true);

            revalidate();
            repaint();
            listPanel.revalidate();
        }
    }
}
