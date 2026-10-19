package library.ui;

import library.model.BorrowHistoryResult;
import library.model.ReturnBookResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Trả sách" dành cho Thủ thư (UC-02, kèm UC-14 khi sách hỏng/mất).
 *
 * Giống màn Lập phiếu mượn: mỗi lần bấm "+ Tạo phiếu trả mới" thêm một khung lên đầu danh sách,
 * khung được giữ lại đến khi tắt ứng dụng. Mỗi khung trả MỘT cuốn sách theo 2 bước:
 *   1. Nhập mã vạch -> "Tìm sách": tra cứu dòng đang mượn (GET_BORROW_HISTORY, tab BORROWING, theo maCuonSach).
 *   2. Chọn tình trạng khi trả (Bình thường / Hỏng / Mất) -> "Xác nhận trả sách" (PROCESS_RETURN_BOOK).
 * Tiền phạt trễ hạn do Backend tự tính; hỏng/mất thì thủ thư có thể nhập tiền phạt (bỏ trống = giá bìa).
 */
public class ReturnBookPanel extends JPanel {

    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private static final String COND_NORMAL = "Bình thường";
    private static final String COND_DAMAGED = "Hỏng";
    private static final String COND_LOST = "Mất";

    private final JPanel listPanel = new JPanel();
    private final JLabel emptyHint = new JLabel("Bấm \"+ Tạo phiếu trả mới\" để bắt đầu trả sách.");
    private JScrollPane scrollPane;
    private int cardCounter = 0;

    public ReturnBookPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Trả sách");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JButton newButton = new JButton("+ Tạo phiếu trả mới");
        newButton.addActionListener(e -> addNewCard());

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.WEST);
        top.add(newButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        emptyHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        emptyHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        listPanel.add(emptyHint);

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.add(listPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void addNewCard() {
        if (emptyHint.getParent() != null) {
            listPanel.remove(emptyHint);
        }

        cardCounter++;
        ReturnCard card = new ReturnCard(cardCounter);

        listPanel.add(card, 0);
        listPanel.add(Box.createVerticalStrut(10), 1);
        listPanel.revalidate();
        listPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            scrollPane.getViewport().setViewPosition(new Point(0, 0));
            card.focusFirstField();
        });
    }

    // ==========================================================
    // MỘT KHUNG PHIẾU TRẢ
    // ==========================================================
    private class ReturnCard extends JPanel {
        private final JTextField barcodeField = new JTextField(18);
        private final JButton findButton = new JButton("Tìm sách");
        private final JLabel infoLabel = new JLabel();

        private final JPanel detailsPanel = new JPanel();
        private final JComboBox<String> conditionBox =
                new JComboBox<>(new String[]{COND_NORMAL, COND_DAMAGED, COND_LOST});
        private final JPanel violationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        private final JLabel noteLabel = new JLabel("Mô tả hư hỏng:");
        private final JTextField noteField = new JTextField(24);
        private final JTextField amountField = new JTextField(12);
        private final JButton confirmButton = new JButton("Xác nhận trả sách");

        private final JLabel messageLabel = new JLabel(" ");
        private final TitledBorder titledBorder;

        private BorrowHistoryResult.Item found = null; // Dòng đang mượn tìm được (null = chưa tìm / đã xóa)
        private boolean busy = false;                  // Đang chờ Backend trả lời
        private boolean completed = false;             // Đã trả xong

        ReturnCard(int index) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            titledBorder = BorderFactory.createTitledBorder("Phiếu trả #" + index);
            setBorder(BorderFactory.createCompoundBorder(
                    titledBorder,
                    BorderFactory.createEmptyBorder(6, 10, 10, 10)));

            barcodeField.putClientProperty("JTextField.placeholderText", "VD: BS-1-02");
            amountField.putClientProperty("JTextField.placeholderText", "Bỏ trống = giá bìa");

            // Hàng 1: nhập mã vạch
            JPanel searchRow = newRow();
            searchRow.add(new JLabel("Mã vạch cuốn sách:"));
            searchRow.add(barcodeField);
            searchRow.add(findButton);

            // Thông tin dòng mượn tìm được
            infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));
            infoLabel.setVisible(false);

            // Phần chọn tình trạng + xác nhận (chỉ hiện sau khi tìm thấy sách)
            detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
            detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailsPanel.setVisible(false);

            JPanel conditionRow = newRow();
            conditionRow.add(new JLabel("Tình trạng khi trả:"));
            conditionRow.add(conditionBox);

            violationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            violationRow.add(noteLabel);
            violationRow.add(noteField);
            violationRow.add(new JLabel("Tiền phạt (VNĐ):"));
            violationRow.add(amountField);
            violationRow.setVisible(false);

            JPanel confirmRow = newRow();
            confirmRow.add(confirmButton);

            detailsPanel.add(conditionRow);
            detailsPanel.add(violationRow);
            detailsPanel.add(confirmRow);

            messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));

            add(searchRow);
            add(infoLabel);
            add(detailsPanel);
            add(messageLabel);

            // Sửa mã vạch sau khi đã tìm -> bỏ kết quả cũ để không trả nhầm cuốn khác
            barcodeField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { onBarcodeEdited(); }
                @Override public void removeUpdate(DocumentEvent e) { onBarcodeEdited(); }
                @Override public void changedUpdate(DocumentEvent e) { onBarcodeEdited(); }
            });
            // Máy quét mã vạch tự gõ Enter -> tìm luôn
            barcodeField.addActionListener(e -> {
                if (findButton.isEnabled()) {
                    find();
                }
            });
            findButton.addActionListener(e -> find());
            conditionBox.addActionListener(e -> onConditionChanged());
            confirmButton.addActionListener(e -> confirmReturn());

            refreshControls();
        }

        private JPanel newRow() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            return row;
        }

        // Chiều cao tối đa = chiều cao cần thiết, để BoxLayout bên ngoài không kéo giãn khung
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        void focusFirstField() {
            barcodeField.requestFocusInWindow();
        }

        private void relayout() {
            revalidate();
            repaint();
            listPanel.revalidate();
        }

        private void setMessage(String text, Color color) {
            messageLabel.setText(text == null || text.isEmpty() ? " " : text);
            if (color != null) {
                messageLabel.setForeground(color);
            }
            relayout();
        }

        private void refreshControls() {
            boolean idle = !busy && !completed;
            findButton.setEnabled(idle && !barcodeField.getText().trim().isEmpty());
            confirmButton.setEnabled(idle && found != null);
        }

        private void setBusy(boolean value) {
            busy = value;
            barcodeField.setEditable(!value && !completed);
            conditionBox.setEnabled(!value && !completed);
            noteField.setEditable(!value && !completed);
            amountField.setEditable(!value && !completed);
            refreshControls();
        }

        private void onBarcodeEdited() {
            if (completed || busy) return;
            if (found != null) {
                resetFound();
            }
            refreshControls();
        }

        /** Xóa kết quả tìm kiếm cũ, quay về bước nhập mã vạch. */
        private void resetFound() {
            found = null;
            infoLabel.setVisible(false);
            detailsPanel.setVisible(false);
            conditionBox.setSelectedIndex(0);
            noteField.setText("");
            amountField.setText("");
            setMessage(" ", null);
            refreshControls();
        }

        private void onConditionChanged() {
            String selected = (String) conditionBox.getSelectedItem();
            boolean violation = !COND_NORMAL.equals(selected);
            violationRow.setVisible(violation);
            noteLabel.setText(COND_LOST.equals(selected) ? "Ghi chú (tùy chọn):" : "Mô tả hư hỏng:");
            relayout();
        }

        // ----- Bước 1: tìm sách đang mượn theo mã vạch -----
        private void find() {
            if (busy || completed) return;

            final String barcode = barcodeField.getText().trim();
            if (barcode.isEmpty()) return;

            resetFound();
            setBusy(true);
            findButton.setText("Đang tìm...");

            SwingWorker<BorrowHistoryResult.Item, Void> worker = new SwingWorker<BorrowHistoryResult.Item, Void>() {
                @Override
                protected BorrowHistoryResult.Item doInBackground() throws Exception {
                    return SocketClient.getInstance().findBorrowingByBarcode(barcode);
                }

                @Override
                protected void done() {
                    findButton.setText("Tìm sách");
                    try {
                        BorrowHistoryResult.Item item = get();
                        setBusy(false);
                        if (item == null) {
                            setMessage("Không tìm thấy cuốn sách đang mượn với mã vạch này "
                                    + "(sách chưa được mượn, đã trả rồi hoặc sai mã vạch).", ERROR_COLOR);
                        } else {
                            showFound(item);
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        setBusy(false);
                    } catch (ExecutionException ex) {
                        Throwable cause = UiUtil.unwrap(ex);
                        setBusy(false);
                        setMessage((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                                + UiUtil.messageOf(cause), ERROR_COLOR);
                    }
                }
            };
            worker.execute();
        }

        private void showFound(BorrowHistoryResult.Item item) {
            found = item;
            infoLabel.setText(buildInfoHtml(item));
            infoLabel.setVisible(true);
            detailsPanel.setVisible(true);
            conditionBox.setSelectedIndex(0);
            onConditionChanged();
            refreshControls();
            relayout();
            SwingUtilities.invokeLater(() -> conditionBox.requestFocusInWindow());
        }

        private String buildInfoHtml(BorrowHistoryResult.Item it) {
            StringBuilder sb = new StringBuilder("<html>");
            sb.append("<b>").append(UiUtil.html(it.tenSach)).append("</b> (")
              .append(UiUtil.html(it.maCuonSach)).append(")<br>");
            sb.append("Độc giả: ").append(UiUtil.html(it.tenDocGia))
              .append(" (thẻ #").append(it.maThe).append(") - Phiếu mượn #").append(it.maPhieuMuon).append("<br>");
            sb.append("Ngày mượn: ").append(UiUtil.formatDate(it.ngayMuon))
              .append(" - Hạn trả: ").append(UiUtil.formatDate(it.ngayHenTra)).append("<br>");
            if (it.quaHan) {
                sb.append("<font color='#ef4444'>Quá hạn ").append(it.soNgayTre)
                  .append(" ngày - tiền phạt trễ hạn dự kiến ")
                  .append(UiUtil.formatMoney(it.tienPhatDuKien)).append("</font>");
            } else {
                sb.append("Còn trong hạn - không phạt trễ hạn");
            }
            return sb.append("</html>").toString();
        }

        // ----- Bước 2: xác nhận trả -----
        private void confirmReturn() {
            if (busy || completed || found == null) return;

            String condition = (String) conditionBox.getSelectedItem();
            String viPham = null;
            String tinhTrang = COND_NORMAL;
            Double amount = null;
            String note = null;

            if (COND_DAMAGED.equals(condition) || COND_LOST.equals(condition)) {
                boolean lost = COND_LOST.equals(condition);
                viPham = lost ? "LOST" : "DAMAGED";
                note = noteField.getText().trim();

                // Cho phép gõ "30.000" hay "30,000": bỏ dấu phân cách rồi chỉ nhận chữ số
                String amountText = amountField.getText().replaceAll("[\\s.,]", "");
                if (!amountText.isEmpty()) {
                    if (!amountText.matches("\\d{1,12}")) {
                        setMessage("Tiền phạt không hợp lệ (chỉ nhập số, ví dụ 30000).", ERROR_COLOR);
                        return;
                    }
                    amount = Double.valueOf(amountText);
                }
                tinhTrang = lost ? "Mất" : (note.isEmpty() ? "Hỏng" : note);
            }

            final int maCTPM = found.maCTPM;
            final String finalTinhTrang = tinhTrang;
            final String finalViPham = viPham;
            final Double finalAmount = amount;
            final String finalNote = note;

            setBusy(true);
            confirmButton.setText("Đang xử lý...");
            setMessage(" ", null);

            SwingWorker<ReturnBookResult, Void> worker = new SwingWorker<ReturnBookResult, Void>() {
                @Override
                protected ReturnBookResult doInBackground() throws Exception {
                    return SocketClient.getInstance().processReturn(
                            maCTPM, finalTinhTrang, finalViPham, finalAmount, finalNote);
                }

                @Override
                protected void done() {
                    try {
                        markCompleted(get());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        confirmButton.setText("Xác nhận trả sách");
                        setBusy(false);
                    } catch (ExecutionException ex) {
                        Throwable cause = UiUtil.unwrap(ex);
                        confirmButton.setText("Xác nhận trả sách");
                        setBusy(false); // Cho phép sửa và gửi lại
                        setMessage((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                                + UiUtil.messageOf(cause), ERROR_COLOR);
                    }
                }
            };
            worker.execute();
        }

        /** Trả thành công: khóa khung, hiển thị kết quả; khung được giữ nguyên. */
        private void markCompleted(ReturnBookResult result) {
            completed = true;
            busy = false;

            barcodeField.setEditable(false);
            conditionBox.setEnabled(false);
            noteField.setEditable(false);
            amountField.setEditable(false);
            findButton.setEnabled(false);
            confirmButton.setText("Đã trả sách");
            confirmButton.setEnabled(false);

            titledBorder.setTitle("Đã trả: " + found.maCuonSach);
            setMessage(buildResultHtml(result), SUCCESS_COLOR);
        }

        private String buildResultHtml(ReturnBookResult result) {
            StringBuilder sb = new StringBuilder("<html>Trả sách thành công.");

            ReturnBookResult.Item k = (result.ketQua != null && !result.ketQua.isEmpty())
                    ? result.ketQua.get(0) : null;
            if (k != null) {
                if (k.soNgayTre > 0) {
                    sb.append(" Trễ hạn ").append(k.soNgayTre).append(" ngày.");
                }
                if (k.phieuPhat != null) {
                    for (ReturnBookResult.Fine f : k.phieuPhat) {
                        sb.append("<br>- Phiếu phạt: ").append(UiUtil.html(f.lyDo))
                          .append(" - ").append(UiUtil.formatMoney(f.soTienPhat));
                    }
                }
                if (result.coPhat) {
                    sb.append("<br><b>Tổng tiền phạt: ").append(UiUtil.formatMoney(result.tongTienPhat))
                      .append("</b> (độc giả cần nộp phạt trước khi mượn tiếp)");
                }
                sb.append("<br>").append(describeCopyState(k));
            }
            return sb.append("</html>").toString();
        }

        private String describeCopyState(ReturnBookResult.Item k) {
            if (k.datTruocDuocThongBao != null) {
                return "Sách đang có người đặt trước: hãy cất lại ở quầy để giữ cho người đó "
                        + "(mã đặt trước #" + k.datTruocDuocThongBao + ", đã thông báo).";
            }
            String state = k.trangThaiCuonSach == null ? "" : k.trangThaiCuonSach;
            switch (state) {
                case "AVAILABLE": return "Sách đã về kho và sẵn sàng cho mượn.";
                case "DAMAGED":   return "Sách được đánh dấu hỏng, không cho mượn.";
                case "LOST":      return "Sách được ghi nhận là mất.";
                default:          return "Trạng thái cuốn sách: " + UiUtil.html(state);
            }
        }
    }
}
