package library.ui;

import library.model.CardEligibilityResult;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Kiểm tra thẻ" của Thủ thư (UC-13): nhập mã thẻ, Backend trả về thẻ có đủ điều kiện mượn sách không.
 *
 * - Không dùng hộp thoại: kết quả (kể cả lỗi như "Không tìm thấy thẻ") hiện ngay trên màn hình và được giữ
 *   nguyên cho đến lần kiểm tra kế tiếp (hoặc khi đăng xuất).
 * - Kết quả gồm một dải màu cho biết đủ / không đủ điều kiện kèm lý do, thông tin độc giả và hạn mức mượn.
 * - Backend kiểm tra lần lượt: trạng thái thẻ -> hạn thẻ -> nợ phạt -> sách quá hạn -> hạn mức mượn,
 *   và trả về lý do đầu tiên không đạt.
 */
public class CardCheckPanel extends JPanel {

    private static final Color OK_COLOR = new Color(16, 185, 129);     // Xanh lá: đủ điều kiện
    private static final Color BAD_COLOR = new Color(239, 68, 68);     // Đỏ: không đủ điều kiện / lỗi

    private final JTextField cardField = new JTextField(12);
    private JButton checkButton;
    private JLabel inputHint;

    // Dải kết quả
    private JPanel banner;
    private JLabel bannerTitle;
    private JLabel bannerMessage;

    // Phần chi tiết (chỉ hiện khi tìm thấy thẻ)
    private JPanel detailPanel;
    private final JLabel hoTen = valueLabel();
    private final JLabel maND = valueLabel();
    private final JLabel email = valueLabel();
    private final JLabel soDienThoai = valueLabel();
    private final JLabel maThe = valueLabel();
    private final JLabel ngayCap = valueLabel();
    private final JLabel ngayHetHan = valueLabel();
    private final JLabel trangThaiThe = valueLabel();
    private final JLabel dangMuon = valueLabel();
    private final JLabel soSachToiDa = valueLabel();
    private final JLabel conDuocMuon = valueLabel();

    private JLabel emptyHint;

    private boolean checking = false;
    private int latestRequestId = 0; // Để bỏ qua kết quả của request đã cũ (ví dụ đăng xuất giữa chừng)

    public CardCheckPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Kiểm tra thẻ độc giả");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        // Hàng nhập: Mã thẻ + nút Kiểm tra (Enter trong ô nhập cũng kiểm tra)
        cardField.putClientProperty("JTextField.placeholderText", "Nhập mã thẻ");
        cardField.addActionListener(e -> check());
        checkButton = new JButton("Kiểm tra");
        checkButton.addActionListener(e -> check());

        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        inputRow.add(new JLabel("Mã thẻ:"));
        inputRow.add(cardField);
        inputRow.add(checkButton);

        inputHint = new JLabel(" ");
        inputHint.setForeground(BAD_COLOR);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        inputRow.setAlignmentX(LEFT_ALIGNMENT);
        inputHint.setAlignmentX(LEFT_ALIGNMENT);
        north.add(title);
        north.add(Box.createVerticalStrut(6));
        north.add(inputRow);
        north.add(inputHint);
        add(north, BorderLayout.NORTH);

        // Khu vực kết quả
        emptyHint = new JLabel("Nhập mã thẻ rồi bấm \"Kiểm tra\" để xem thẻ có đủ điều kiện mượn sách không.");
        emptyHint.setForeground(UIManager.getColor("Label.disabledForeground"));

        banner = new JPanel();
        banner.setLayout(new BoxLayout(banner, BoxLayout.Y_AXIS));
        banner.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        bannerTitle = new JLabel(" ");
        bannerTitle.setFont(new Font("Arial", Font.BOLD, 20));
        bannerTitle.setForeground(Color.WHITE);
        bannerMessage = new JLabel(" ");
        bannerMessage.setFont(new Font("Arial", Font.PLAIN, 15));
        bannerMessage.setForeground(Color.WHITE);
        banner.add(bannerTitle);
        banner.add(Box.createVerticalStrut(4));
        banner.add(bannerMessage);

        detailPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        detailPanel.add(section("Thông tin độc giả", new String[]{
                "Họ tên", "Mã người dùng", "Email", "Số điện thoại",
                "Mã thẻ", "Ngày cấp", "Hạn thẻ", "Trạng thái thẻ"
        }, new JLabel[]{hoTen, maND, email, soDienThoai, maThe, ngayCap, ngayHetHan, trangThaiThe}));
        detailPanel.add(section("Hạn mức mượn sách", new String[]{
                "Đang mượn", "Tối đa được mượn", "Còn được mượn"
        }, new JLabel[]{dangMuon, soSachToiDa, conDuocMuon}));

        JPanel result = new JPanel();
        result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
        emptyHint.setAlignmentX(LEFT_ALIGNMENT);
        banner.setAlignmentX(LEFT_ALIGNMENT);
        detailPanel.setAlignmentX(LEFT_ALIGNMENT);
        result.add(emptyHint);
        result.add(banner);
        result.add(Box.createVerticalStrut(12));
        result.add(detailPanel);
        showEmpty();

        // Bọc vào NORTH để dải kết quả không bị kéo giãn ra hết chiều cao màn hình
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(result, BorderLayout.NORTH);
        add(new JScrollPane(wrapper), BorderLayout.CENTER);
    }

    /**
     * Xóa kết quả đang hiển thị. MainFrame gọi khi đăng xuất để người đăng nhập sau
     * không nhìn thấy thông tin độc giả của phiên trước.
     */
    public void clear() {
        latestRequestId++; // Bỏ qua mọi request đang chạy dở
        checking = false;
        cardField.setText("");
        inputHint.setText(" ");
        checkButton.setEnabled(true);
        checkButton.setText("Kiểm tra");
        showEmpty();
    }

    // ==========================================================
    // KIỂM TRA THẺ
    // ==========================================================
    private void check() {
        if (checking) return;

        final int card;
        try {
            card = Integer.parseInt(cardField.getText().trim());
            if (card <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            inputHint.setText("Mã thẻ phải là một số nguyên dương.");
            return;
        }
        inputHint.setText(" ");

        setChecking(true);
        final int requestId = ++latestRequestId;

        SwingWorker<CardEligibilityResult, Void> worker = new SwingWorker<CardEligibilityResult, Void>() {
            @Override
            protected CardEligibilityResult doInBackground() throws Exception {
                return SocketClient.getInstance().checkCardEligibility(card);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất / có request mới hơn
                try {
                    showResult(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    // Lỗi (không tìm thấy thẻ, mất kết nối...) hiện ngay trong dải kết quả, không dùng hộp thoại
                    Throwable cause = UiUtil.unwrap(ex);
                    showError(UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Không kiểm tra được thẻ " + card,
                            UiUtil.messageOf(cause));
                } finally {
                    setChecking(false);
                }
            }
        };
        worker.execute();
    }

    private void setChecking(boolean value) {
        checking = value;
        checkButton.setEnabled(!value);
        checkButton.setText(value ? "Đang kiểm tra..." : "Kiểm tra");
        cardField.setEnabled(!value);
        if (!value) {
            cardField.requestFocusInWindow();
            cardField.selectAll();
        }
    }

    // ==========================================================
    // HIỂN THỊ KẾT QUẢ
    // ==========================================================
    private void showEmpty() {
        emptyHint.setVisible(true);
        banner.setVisible(false);
        detailPanel.setVisible(false);
    }

    private void showResult(CardEligibilityResult r) {
        emptyHint.setVisible(false);

        if (r.eligible) {
            paintBanner(OK_COLOR, "Đủ điều kiện mượn sách",
                    r.message != null ? r.message : "Thẻ đủ điều kiện mượn sách");
        } else {
            paintBanner(BAD_COLOR, "Không đủ điều kiện mượn sách",
                    r.message != null ? r.message : "Thẻ không đủ điều kiện mượn sách");
        }

        CardEligibilityResult.DocGia d = r.docGia;
        if (d != null) {
            hoTen.setText(nz(d.hoTen));
            maND.setText(String.valueOf(d.maND));
            email.setText(nz(d.email));
            soDienThoai.setText(nz(d.soDienThoai));
            maThe.setText(String.valueOf(d.maThe));
            ngayCap.setText(UiUtil.formatDate(d.ngayCap));
            ngayHetHan.setText(UiUtil.formatDate(d.ngayHetHan));
            trangThaiThe.setText(cardStatusText(d.trangThaiThe));
            trangThaiThe.setForeground("ACTIVE".equals(d.trangThaiThe) ? OK_COLOR : BAD_COLOR);
        }
        dangMuon.setText(r.dangMuon + " cuốn");
        soSachToiDa.setText(r.soSachToiDa + " cuốn");
        conDuocMuon.setText(r.conDuocMuon + " cuốn");
        conDuocMuon.setForeground(r.conDuocMuon > 0 ? OK_COLOR : BAD_COLOR);
        detailPanel.setVisible(d != null);

        revalidate();
        repaint();
    }

    private void showError(String title, String message) {
        emptyHint.setVisible(false);
        detailPanel.setVisible(false);
        paintBanner(BAD_COLOR, title, message);
        revalidate();
        repaint();
    }

    private void paintBanner(Color color, String title, String message) {
        banner.setBackground(color);
        bannerTitle.setText(title);
        bannerMessage.setText(message);
        banner.setVisible(true);
    }

    private static String cardStatusText(String status) {
        if (status == null) return "";
        switch (status) {
            case "ACTIVE":  return "Còn hiệu lực";
            case "LOCKED":  return "Bị khóa";
            case "EXPIRED": return "Hết hạn";
            case "NONE":    return "Chưa có thẻ";
            default:        return status;
        }
    }

    // ==========================================================
    // TIỆN ÍCH GIAO DIỆN
    // ==========================================================
    private static JLabel valueLabel() {
        JLabel label = new JLabel(" ");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    /** Khung có viền tiêu đề, bên trong là các cặp "nhãn : giá trị" xếp thành hai cột. */
    private static JPanel section(String title, String[] names, JLabel[] values) {
        JPanel p = new JPanel(new GridBagLayout());
        TitledBorder border = BorderFactory.createTitledBorder(title);
        p.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(6, 10, 10, 10)));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 16);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < names.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            c.weightx = 0;
            p.add(new JLabel(names[i] + ":"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(values[i], c);
            c.fill = GridBagConstraints.NONE;
        }
        // Đẩy các dòng lên trên khi khung cao hơn nội dung (hai khung có số dòng khác nhau)
        c.gridx = 0;
        c.gridy = names.length;
        c.gridwidth = 2;
        c.weighty = 1;
        p.add(Box.createGlue(), c);
        return p;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
