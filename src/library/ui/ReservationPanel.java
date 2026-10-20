package library.ui;

import library.model.Book;
import library.model.Reservation;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Đặt trước sách" của Độc giả (UC-07, UC-10 phần xem trạng thái).
 *
 * - Mở màn hình là tự tải TOÀN BỘ lượt đặt trước còn hiệu lực của mình (GET_MY_RESERVATIONS): WAITING, RESERVED, NOTIFIED.
 *   Mỗi lượt là một khung; khung mới nhất nằm trên cùng.
 * - "+ Tạo phiếu đặt trước" thêm một khung trống: nhập tên sách rồi bấm "Đặt trước". Sau khi đặt xong, nút "Đặt trước"
 *   bị khóa và đổi thành trạng thái của lượt đặt, bên cạnh hiện thêm nút "Hủy" (CANCEL_RESERVATION).
 * - API (RESERVE_BOOK) cần mã đầu sách chứ không nhận tên sách, nên khi bấm "Đặt trước" màn hình tìm sách theo tên
 *   (SEARCH_BOOKS) trước: một kết quả thì dùng luôn, nhiều kết quả thì cho chọn.
 * - Chỉ đặt trước được khi đầu sách đã hết cuốn sẵn sàng; việc kiểm tra này (và thẻ hợp lệ, đã đặt rồi...) do Backend quyết định,
 *   lỗi của Backend hiện ngay trong khung.
 * - "Làm mới" tải lại từ Backend (khung nhập chưa đặt sẽ bị bỏ).
 */
public class ReservationPanel extends JPanel {

    private static final Color SUCCESS_COLOR = new Color(16, 185, 129);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);

    private static final String WAITING = "WAITING";
    private static final String RESERVED = "RESERVED";
    private static final String NOTIFIED = "NOTIFIED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";

    private static final DateTimeFormatter DATE_TIME_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JPanel listPanel = new JPanel();
    private final JLabel emptyHint = new JLabel("Bạn chưa có phiếu đặt trước nào. Bấm \"+ Tạo phiếu đặt trước\" để bắt đầu.");
    private final JButton newButton = new JButton("+ Tạo phiếu đặt trước");
    private final JButton refreshButton = new JButton("Làm mới");
    private final JLabel statusLabel = new JLabel(" ");
    private JScrollPane scrollPane;

    private boolean loading = false;   // Đang tải danh sách
    private int busyCards = 0;         // Số khung đang chờ Backend trả lời
    private int latestRequestId = 0;   // Để bỏ qua kết quả của request đã cũ
    private int generation = 0;        // Tăng khi đăng xuất, để khung cũ không làm sai bộ đếm busyCards

    public ReservationPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Đặt trước sách");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        newButton.addActionListener(e -> addNewCard());
        refreshButton.addActionListener(e -> loadReservations());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(newButton);
        actions.add(refreshButton);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        emptyHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        emptyHint.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Bọc vào NORTH để các khung không bị kéo giãn ra hết chiều cao màn hình
        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.add(listPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần mở màn hình thì tải lại (trạng thái đặt trước có thể vừa đổi ở quầy thủ thư)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                loadReservations();
            }
        });
    }

    /**
     * Xóa sạch dữ liệu đang hiển thị. MainFrame gọi khi đăng xuất để người đăng nhập sau
     * không nhìn thấy các lượt đặt trước của người trước.
     */
    public void clear() {
        latestRequestId++; // Bỏ qua mọi request tải đang chạy dở
        generation++;
        loading = false;
        busyCards = 0;
        listPanel.removeAll();
        listPanel.revalidate();
        listPanel.repaint();
        statusLabel.setText(" ");
        refreshButton.setEnabled(true);
        refreshButton.setText("Làm mới");
    }

    // ==========================================================
    // TẢI DANH SÁCH
    // ==========================================================
    private void loadReservations() {
        if (loading || busyCards > 0) return;

        final int requestId = ++latestRequestId;
        setLoading(true);

        SwingWorker<List<Reservation>, Void> worker = new SwingWorker<List<Reservation>, Void>() {
            @Override
            protected List<Reservation> doInBackground() throws Exception {
                return SocketClient.getInstance().getMyReservations();
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất / có request mới hơn

                try {
                    rebuildList(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được danh sách đặt trước.");
                    JOptionPane.showMessageDialog(ReservationPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải danh sách thất bại",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setLoading(false);
                }
            }
        };
        worker.execute();
    }

    private void setLoading(boolean value) {
        loading = value;
        updateToolbar();
        if (value) {
            statusLabel.setText("Đang tải dữ liệu từ máy chủ...");
        }
    }

    private void updateToolbar() {
        refreshButton.setEnabled(!loading && busyCards == 0);
        refreshButton.setText(loading ? "Đang tải..." : "Làm mới");
    }

    /** Dựng lại toàn bộ danh sách từ dữ liệu Backend (bỏ các khung nhập chưa đặt). */
    private void rebuildList(List<Reservation> reservations) {
        listPanel.removeAll();

        if (reservations.isEmpty()) {
            listPanel.add(emptyHint);
        } else {
            for (Reservation r : reservations) {
                listPanel.add(new ReservationCard(r));
                listPanel.add(Box.createVerticalStrut(10));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
        SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(new Point(0, 0)));

        statusLabel.setText(reservations.isEmpty()
                ? "Bạn chưa có lượt đặt trước nào đang hiệu lực. (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")"
                : "Có " + reservations.size() + " lượt đặt trước đang hiệu lực. (Cập nhật lúc "
                        + LocalTime.now().format(TIME_VN) + ")");
    }

    /** Thêm một khung trống (chưa đặt) lên đầu danh sách. */
    private void addNewCard() {
        if (emptyHint.getParent() != null) {
            listPanel.remove(emptyHint);
        }

        ReservationCard card = new ReservationCard(null);
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
    // HIỂN THỊ TRẠNG THÁI
    // ==========================================================
    private static boolean isActive(String status) {
        return WAITING.equals(status) || RESERVED.equals(status) || NOTIFIED.equals(status);
    }

    /** Chữ trên nút trạng thái (nút "Đặt trước" sau khi đặt xong). */
    private static String statusText(String status) {
        if (status == null) return "";
        switch (status) {
            case WAITING:   return "Đang chờ";
            case RESERVED:  return "Đã giữ sách";
            case NOTIFIED:  return "Sách đã sẵn sàng";
            case COMPLETED: return "Đã mượn";
            case CANCELLED: return "Đã hủy";
            default:        return status;
        }
    }

    private static String formatDateTime(String iso) {
        if (iso == null) return "";
        try {
            return LocalDateTime.parse(iso).format(DATE_TIME_VN);
        } catch (Exception ex) {
            return UiUtil.formatDate(iso); // Định dạng lạ thì rơi về chỉ hiện ngày / nguyên văn
        }
    }

    private static String buildInfoHtml(Reservation r) {
        StringBuilder sb = new StringBuilder("<html>Ngày đặt: ").append(formatDateTime(r.ngayDat));
        String status = r.trangThai == null ? "" : r.trangThai;

        switch (status) {
            case WAITING:
                sb.append(" - Vị trí trong hàng chờ: ").append(r.viTriHangCho == null ? "?" : r.viTriHangCho);
                break;
            case RESERVED:
                sb.append("<br>Đã giữ một cuốn cho bạn. Hệ thống sẽ thông báo khi sách được trả về quầy.");
                break;
            case NOTIFIED:
                sb.append("<br><font color='#10b981'><b>")
                  .append(UiUtil.html(r.thongBao != null ? r.thongBao : "Sách bạn đặt trước đã có tại quầy."))
                  .append("</b></font>");
                if (r.maCuonSach != null || r.hanNhanSach != null) {
                    sb.append("<br>");
                    if (r.maCuonSach != null) sb.append("Mã cuốn sách: ").append(UiUtil.html(r.maCuonSach));
                    if (r.maCuonSach != null && r.hanNhanSach != null) sb.append(" - ");
                    if (r.hanNhanSach != null) sb.append("Hạn nhận: ").append(UiUtil.formatDate(r.hanNhanSach));
                }
                break;
            case COMPLETED:
                sb.append("<br>Bạn đã mượn được cuốn sách này.");
                break;
            case CANCELLED:
                sb.append("<br>Lượt đặt trước đã được hủy.");
                break;
            default:
                break;
        }
        return sb.append("</html>").toString();
    }

    /** Một dòng trong hộp thoại chọn sách khi tên nhập vào khớp nhiều đầu sách. */
    private static class BookChoice {
        final Book book;

        BookChoice(Book book) {
            this.book = book;
        }

        @Override
        public String toString() {
            String author = book.getTacGia() == null || book.getTacGia().isEmpty() ? "" : " - " + book.getTacGia();
            String stock = book.getSoLuongKhaDung() > 0
                    ? "  [còn " + book.getSoLuongKhaDung() + " cuốn, không cần đặt trước]"
                    : "  [hết sách]";
            return book.getTenSach() + author + stock;
        }
    }

    // ==========================================================
    // MỘT KHUNG ĐẶT TRƯỚC
    // ==========================================================
    private class ReservationCard extends JPanel {
        private final JTextField nameField = new JTextField(32);
        private final JButton placeButton = new JButton("Đặt trước");
        private final JButton cancelButton = new JButton("Hủy");
        private final JLabel infoLabel = new JLabel();
        private final JLabel messageLabel = new JLabel(" ");
        private final TitledBorder titledBorder;
        private final int cardGeneration = generation;

        private Reservation reservation; // null = khung nhập chưa đặt
        private boolean busy = false;    // Đang chờ Backend trả lời

        ReservationCard(Reservation existing) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            titledBorder = BorderFactory.createTitledBorder("Phiếu đặt trước mới");
            setBorder(BorderFactory.createCompoundBorder(
                    titledBorder,
                    BorderFactory.createEmptyBorder(6, 10, 10, 10)));

            nameField.putClientProperty("JTextField.placeholderText", "Nhập tên sách muốn đặt trước");

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(new JLabel("Tên sách:"));
            row.add(nameField);
            row.add(placeButton);
            row.add(cancelButton);

            infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
            infoLabel.setVisible(false);

            messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messageLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));

            add(row);
            add(infoLabel);
            add(messageLabel);

            nameField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { refreshControls(); }
                @Override public void removeUpdate(DocumentEvent e) { refreshControls(); }
                @Override public void changedUpdate(DocumentEvent e) { refreshControls(); }
            });
            nameField.addActionListener(e -> {
                if (placeButton.isEnabled()) place();
            });
            placeButton.addActionListener(e -> place());
            cancelButton.addActionListener(e -> cancel());

            if (existing != null) {
                showReservation(existing);
            } else {
                refreshControls();
            }
        }

        // Chiều cao tối đa = chiều cao cần thiết, để BoxLayout bên ngoài không kéo giãn khung
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        void focusFirstField() {
            nameField.requestFocusInWindow();
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

        private void setBusy(boolean value) {
            if (busy == value) return;
            busy = value;
            if (cardGeneration == generation) { // Khung của phiên đăng nhập cũ thì không đụng tới bộ đếm
                busyCards = Math.max(0, busyCards + (value ? 1 : -1));
                updateToolbar();
            }
            refreshControls();
        }

        /** Cập nhật chữ và trạng thái bật/khóa của ô nhập, nút "Đặt trước" / trạng thái và nút "Hủy". */
        private void refreshControls() {
            boolean placed = reservation != null;

            nameField.setEditable(!placed && !busy);

            if (!placed) {
                placeButton.setText(busy ? "Đang đặt..." : "Đặt trước");
                placeButton.setEnabled(!busy && !nameField.getText().trim().isEmpty());
                cancelButton.setVisible(false);
            } else {
                // Sau khi đặt: nút "Đặt trước" bị khóa và hiện trạng thái hiện tại của lượt đặt
                placeButton.setText(statusText(reservation.trangThai));
                placeButton.setEnabled(false);
                cancelButton.setVisible(isActive(reservation.trangThai));
                cancelButton.setEnabled(!busy);
                cancelButton.setText(busy ? "Đang hủy..." : "Hủy");
            }
            relayout();
        }

        /** Chuyển khung sang trạng thái "đã có lượt đặt trước" theo dữ liệu Backend. */
        private void showReservation(Reservation r) {
            reservation = r;
            nameField.setText(r.tenSach == null ? "" : r.tenSach);
            nameField.setCaretPosition(0);
            titledBorder.setTitle("Phiếu đặt trước #" + r.maDatTruoc);
            infoLabel.setText(buildInfoHtml(r));
            infoLabel.setVisible(true);
            refreshControls();
        }

        // ----- Đặt trước: tìm sách theo tên -> (chọn nếu nhiều) -> RESERVE_BOOK -----
        private void place() {
            if (busy || reservation != null) return;

            final String keyword = nameField.getText().trim();
            if (keyword.isEmpty()) return;

            setMessage(" ", null);
            setBusy(true);

            SwingWorker<List<Book>, Void> worker = new SwingWorker<List<Book>, Void>() {
                @Override
                protected List<Book> doInBackground() throws Exception {
                    return SocketClient.getInstance().searchBooks(keyword);
                }

                @Override
                protected void done() {
                    try {
                        onBooksFound(keyword, get());
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

        private void onBooksFound(String keyword, List<Book> found) {
            if (found.isEmpty()) {
                setBusy(false);
                setMessage("Không tìm thấy sách nào khớp \"" + keyword + "\".", ERROR_COLOR);
                return;
            }

            Book chosen = pickBook(keyword, found);
            if (chosen == null) { // Người dùng bấm Hủy ở hộp thoại chọn sách
                setBusy(false);
                return;
            }
            reserve(chosen);
        }

        /** Một kết quả thì dùng luôn; nhiều kết quả mà chỉ một tên trùng khớp hoàn toàn thì dùng cuốn đó; còn lại cho chọn. */
        private Book pickBook(String keyword, List<Book> found) {
            if (found.size() == 1) return found.get(0);

            List<Book> exact = new ArrayList<>();
            for (Book b : found) {
                if (b.getTenSach() != null && b.getTenSach().trim().equalsIgnoreCase(keyword)) {
                    exact.add(b);
                }
            }
            if (exact.size() == 1) return exact.get(0);

            DefaultListModel<BookChoice> listModel = new DefaultListModel<>();
            for (Book b : found) {
                listModel.addElement(new BookChoice(b));
            }
            JList<BookChoice> list = new JList<>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setSelectedIndex(0);
            list.setVisibleRowCount(Math.min(10, found.size()));

            JScrollPane listScroll = new JScrollPane(list);
            listScroll.setPreferredSize(new Dimension(560, Math.min(240, 30 + found.size() * 22)));

            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.add(new JLabel("Có " + found.size() + " đầu sách khớp \"" + keyword
                    + "\". Chọn cuốn bạn muốn đặt trước:"), BorderLayout.NORTH);
            panel.add(listScroll, BorderLayout.CENTER);

            int choice = JOptionPane.showOptionDialog(ReservationPanel.this, panel, "Chọn sách",
                    JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, new Object[]{"Chọn", "Hủy"}, "Chọn");
            if (choice != 0 || list.getSelectedValue() == null) return null; // 0 = nút "Chọn"
            return list.getSelectedValue().book;
        }

        private void reserve(final Book book) {
            SwingWorker<Reservation, Void> worker = new SwingWorker<Reservation, Void>() {
                @Override
                protected Reservation doInBackground() throws Exception {
                    return SocketClient.getInstance().reserveBook(book.getMaDauSach());
                }

                @Override
                protected void done() {
                    try {
                        Reservation r = get();
                        if (r.tenSach == null) r.tenSach = book.getTenSach();
                        setBusy(false);
                        showReservation(r);
                        setMessage("Đặt trước thành công. Hệ thống sẽ thông báo khi sách sẵn sàng.", SUCCESS_COLOR);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        setBusy(false);
                    } catch (ExecutionException ex) {
                        Throwable cause = UiUtil.unwrap(ex);
                        setBusy(false); // Cho phép sửa tên và đặt lại
                        // Lỗi từ Backend (vd. sách còn cuốn sẵn sàng, đã đặt rồi, thẻ không đủ điều kiện) hiện ngay trong khung
                        setMessage((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                                + UiUtil.messageOf(cause), ERROR_COLOR);
                    }
                }
            };
            worker.execute();
        }

        // ----- Hủy đặt trước -----
        private void cancel() {
            if (busy || reservation == null || !isActive(reservation.trangThai)) return;

            // Hủy không hoàn tác được (mất lượt trong hàng chờ) nên hỏi xác nhận trước
            int choice = JOptionPane.showOptionDialog(ReservationPanel.this,
                    "Hủy đặt trước cuốn \"" + (reservation.tenSach == null ? "" : reservation.tenSach) + "\"?\n"
                            + "Bạn sẽ mất lượt trong hàng chờ và không thể hoàn tác.",
                    "Xác nhận hủy đặt trước", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new Object[]{"Hủy đặt trước", "Giữ lại"}, "Giữ lại");
            if (choice != 0) return; // 0 = nút "Hủy đặt trước"

            final int maDatTruoc = reservation.maDatTruoc;
            setMessage(" ", null);
            setBusy(true);

            SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    return SocketClient.getInstance().cancelReservation(maDatTruoc);
                }

                @Override
                protected void done() {
                    try {
                        String message = get();
                        reservation.trangThai = CANCELLED;
                        reservation.viTriHangCho = null;
                        setBusy(false);
                        showReservation(reservation);
                        setMessage(message, SUCCESS_COLOR);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        setBusy(false);
                    } catch (ExecutionException ex) {
                        Throwable cause = UiUtil.unwrap(ex);
                        setBusy(false);
                        setMessage((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                                + UiUtil.messageOf(cause)
                                + " (Bấm \"Làm mới\" để cập nhật trạng thái mới nhất.)", ERROR_COLOR);
                    }
                }
            };
            worker.execute();
        }
    }
}
