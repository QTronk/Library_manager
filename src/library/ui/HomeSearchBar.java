package library.ui;

import library.model.Book;
import library.service.SocketClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Thanh tìm kiếm sách ở Trang chủ, dùng chung cho mọi vai trò (kể cả khách chưa đăng nhập: SEARCH_BOOKS là Public).
 *
 * - Gõ từ khóa rồi bấm Enter (hoặc bấm biểu tượng kính lúp) để tìm; kết quả hiện trong một danh sách thả xuống ngay dưới ô,
 *   không chuyển màn hình. Từ khóa khớp tên sách, tác giả, thể loại hoặc nhà xuất bản (do Backend quyết định).
 * - Bấm vào một sách trong danh sách thì hiện hộp thoại "Thông tin sách" giống màn hình Tra cứu sách.
 * - Bấm ra ngoài (hoặc bấm Esc, hoặc chuyển sang màn hình khác) thì danh sách thả xuống biến mất.
 * - Bàn phím: mũi tên lên / xuống chọn sách, Enter mở sách đang chọn (không chọn gì thì Enter là tìm kiếm).
 * - Không có kết quả, chưa nhập từ khóa hay lỗi kết nối đều báo ngay trong danh sách thả xuống, không bật hộp thoại.
 */
public class HomeSearchBar extends JPanel {

    private static final Color ACCENT = new Color(16, 185, 129);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final int BAR_WIDTH = 560;
    private static final int BAR_HEIGHT = 46;
    private static final int BOOK_ROW_HEIGHT = 64;
    private static final int NOTE_ROW_HEIGHT = 48;
    private static final int MAX_VISIBLE_ROWS = 6;

    /** Dòng thông báo trong danh sách thả xuống (không phải sách): chưa nhập từ khóa, đang tìm, không thấy, lỗi. */
    private static final class Note {
        final String text;
        final boolean error;

        Note(String text, boolean error) {
            this.text = text;
            this.error = error;
        }
    }

    private final JTextField field = new JTextField();
    private final JButton searchButton = new JButton(new SearchIcon());
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<Object> listModel = new DefaultListModel<>();
    // Luôn co theo bề rộng khung (không cuộn ngang): tên sách dài sẽ được cắt gọn bằng dấu "…"
    private final JList<Object> list = new JList<Object>(listModel) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final JScrollPane scroll = new JScrollPane(list);
    private final NumberFormat priceFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));

    private int latestRequestId = 0;   // Để bỏ qua kết quả của lần tìm đã cũ (tìm lại, đăng xuất giữa chừng)
    private boolean focused = false;

    public HomeSearchBar() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(BAR_WIDTH, BAR_HEIGHT));
        setMinimumSize(new Dimension(280, BAR_HEIGHT));
        setMaximumSize(new Dimension(BAR_WIDTH, BAR_HEIGHT));

        // Ô nhập: trong suốt và không viền, khung bo tròn do chính thanh này vẽ (giống nhau ở mọi giao diện sáng / tối)
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 4));
        field.setFont(field.getFont().deriveFont(15f));
        field.putClientProperty("JTextField.placeholderText", "Tìm sách theo tên, tác giả, thể loại, nhà xuất bản...");
        field.addActionListener(e -> onEnter());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                focused = false;
                repaint();
            }
        });
        bindKey("DOWN", "homeSearch.down", () -> move(1));
        bindKey("UP", "homeSearch.up", () -> move(-1));
        bindKey("ESCAPE", "homeSearch.esc", this::hidePopup);

        searchButton.setToolTipText("Tìm kiếm");
        searchButton.setFocusable(false);
        searchButton.setBorderPainted(false);
        searchButton.setContentAreaFilled(false);
        searchButton.setOpaque(false);
        searchButton.setMargin(new Insets(0, 0, 0, 0));
        searchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        searchButton.setPreferredSize(new Dimension(BAR_HEIGHT, BAR_HEIGHT));
        searchButton.addActionListener(e -> search());

        add(field, BorderLayout.CENTER);
        add(searchButton, BorderLayout.EAST);

        // Danh sách thả xuống. JPopupMenu tự biến mất khi bấm ra ngoài; không nhận tiêu điểm nên vẫn gõ tiếp được trong ô nhập.
        list.setCellRenderer(new RowRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = rowAt(e.getPoint());
                if (i >= 0 && listModel.get(i) instanceof Book) {
                    list.setSelectedIndex(i);
                    list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    list.clearSelection();
                    list.setCursor(Cursor.getDefaultCursor());
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                list.clearSelection();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int i = rowAt(e.getPoint());
                if (i >= 0 && listModel.get(i) instanceof Book) {
                    openDetail((Book) listModel.get(i));
                }
            }
        });
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(ACCENT));
        popup.add(scroll);

        // Chuyển sang màn hình khác (thanh này bị ẩn) thì đóng danh sách thả xuống
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
                popup.setVisible(false);
            }
        });
    }

    /** Xóa từ khóa và kết quả (MainFrame gọi khi đăng xuất). */
    public void clear() {
        latestRequestId++; // Bỏ qua lần tìm đang chạy dở
        popup.setVisible(false);
        listModel.clear();
        field.setText("");
    }

    // ==========================================================
    // TÌM KIẾM
    // ==========================================================
    private void onEnter() {
        int sel = list.getSelectedIndex();
        if (popup.isVisible() && sel >= 0 && listModel.get(sel) instanceof Book) {
            openDetail((Book) listModel.get(sel));
        } else {
            search();
        }
    }

    private void search() {
        final String keyword = field.getText().trim();
        if (keyword.isEmpty()) {
            showNote(new Note("Nhập từ khóa để tìm sách (tên sách, tác giả, thể loại hoặc nhà xuất bản).", false));
            return;
        }

        final int requestId = ++latestRequestId;
        showNote(new Note("Đang tìm kiếm...", false));

        SwingWorker<List<Book>, Void> worker = new SwingWorker<List<Book>, Void>() {
            @Override
            protected List<Book> doInBackground() throws Exception {
                return SocketClient.getInstance().searchBooks(keyword);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã tìm lại / đăng xuất
                try {
                    List<Book> books = get();
                    if (books.isEmpty()) {
                        showNote(new Note("Không tìm thấy sách phù hợp với \"" + keyword + "\".", false));
                    } else {
                        showBooks(books);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    showNote(new Note((UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "") + UiUtil.messageOf(cause), true));
                }
            }
        };
        worker.execute();
    }

    // ==========================================================
    // DANH SÁCH THẢ XUỐNG
    // ==========================================================
    private void showBooks(List<Book> books) {
        listModel.clear();
        for (Book b : books) listModel.addElement(b);
        list.setFixedCellHeight(BOOK_ROW_HEIGHT);
        display(Math.min(books.size(), MAX_VISIBLE_ROWS) * BOOK_ROW_HEIGHT);
    }

    private void showNote(Note note) {
        listModel.clear();
        listModel.addElement(note);
        list.setFixedCellHeight(NOTE_ROW_HEIGHT);
        display(NOTE_ROW_HEIGHT);
    }

    private void display(int height) {
        list.clearSelection();
        list.ensureIndexIsVisible(0);
        scroll.getVerticalScrollBar().setValue(0);
        scroll.setPreferredSize(new Dimension(Math.max(getWidth(), 200), height));
        if (!isShowing()) return;
        popup.setVisible(false); // Đặt lại kích thước theo nội dung mới
        popup.show(this, 0, getHeight() + 2);
    }

    private void hidePopup() {
        popup.setVisible(false);
    }

    private int rowAt(Point p) {
        int i = list.locationToIndex(p);
        if (i < 0) return -1;
        Rectangle r = list.getCellBounds(i, i);
        return r != null && r.contains(p) ? i : -1;
    }

    /** Mũi tên lên / xuống: chọn sách kế tiếp (đang đóng mà còn kết quả thì mở lại danh sách). */
    private void move(int delta) {
        if (listModel.isEmpty()) return;
        if (!popup.isVisible()) {
            display(popupHeight());
            return;
        }
        int n = listModel.size();
        int i = list.getSelectedIndex();
        i = i < 0 ? (delta > 0 ? 0 : n - 1) : Math.floorMod(i + delta, n);
        if (listModel.get(i) instanceof Book) {
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
        }
    }

    private int popupHeight() {
        boolean books = listModel.get(0) instanceof Book;
        return books ? Math.min(listModel.size(), MAX_VISIBLE_ROWS) * BOOK_ROW_HEIGHT : NOTE_ROW_HEIGHT;
    }

    private void bindKey(String key, String name, Runnable action) {
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        field.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ==========================================================
    // CHI TIẾT SÁCH (giống hộp thoại ở màn hình Tra cứu sách)
    // ==========================================================
    private void openDetail(Book book) {
        hidePopup();
        String message =
                "Tên sách: " + book.getTenSach()
                + "\nTác giả: " + book.getTacGia()
                + "\nThể loại: " + book.getTheLoai()
                + "\nNhà xuất bản: " + book.getNhaXuatBan()
                + "\nGiá tiền: " + priceFormat.format(book.getGiaTien()) + " đ";

        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), message,
                "Thông tin sách", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==========================================================
    // VẼ
    // ==========================================================
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = UIManager.getColor("TextField.background");
            Color line = UIManager.getColor("Component.borderColor");
            int w = getWidth(), h = getHeight();
            float arc = h; // bo tròn hoàn toàn hai đầu
            g2.setColor(bg != null ? bg : Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            g2.setColor(focused ? ACCENT : (line != null ? line : Color.LIGHT_GRAY));
            g2.setStroke(new BasicStroke(focused ? 2f : 1f));
            g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc - 1, arc - 1));
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /** Biểu tượng kính lúp vẽ bằng code (không phụ thuộc font hay ảnh). */
    private static final class SearchIcon implements Icon {
        @Override public int getIconWidth() { return 22; }
        @Override public int getIconHeight() { return 22; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Ellipse2D.Double(x + 3, y + 3, 11.5, 11.5));
                g2.draw(new Line2D.Double(x + 12.5, y + 12.5, x + 18.5, y + 18.5));
            } finally {
                g2.dispose();
            }
        }
    }

    /** Một dòng của danh sách: sách (tên đậm, tác giả · thể loại, NXB · số cuốn còn) hoặc dòng thông báo. */
    private final class RowRenderer implements ListCellRenderer<Object> {
        private final JPanel row = new JPanel(new BorderLayout());
        private final JPanel bar = new JPanel();
        private final JLabel title = new JLabel();
        private final JLabel line2 = new JLabel();
        private final JLabel line3 = new JLabel();
        private final JPanel texts = new JPanel(new GridLayout(0, 1, 0, 1));

        RowRenderer() {
            bar.setPreferredSize(new Dimension(4, 10));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
            texts.setOpaque(false);
            texts.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            texts.add(title);
            texts.add(line2);
            texts.add(line3);
            row.add(bar, BorderLayout.WEST);
            row.add(texts, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean hasFocus) {
            Color fg = UIManager.getColor("Label.foreground");
            Color muted = UIManager.getColor("Label.disabledForeground");
            Color bg = UIManager.getColor("TextField.background");
            Color hover = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 38);

            row.setOpaque(true);
            row.setBackground(selected ? blend(bg, hover) : (bg != null ? bg : Color.WHITE));
            bar.setBackground(selected ? ACCENT : row.getBackground());
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()));

            texts.removeAll();
            if (value instanceof Book) {
                Book b = (Book) value;
                title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
                title.setText(nz(b.getTenSach()));
                title.setForeground(fg);
                line2.setText(join(" · ", b.getTacGia(), b.getTheLoai()));
                line2.setForeground(fg);
                int ready = b.getSoLuongKhaDung();
                String stock = b.getTongSoLuong() <= 0 ? "Chưa có cuốn nào"
                        : ready > 0 ? "Còn " + ready + "/" + b.getTongSoLuong() + " cuốn" : "Đã được mượn hết";
                line3.setText(join(" · ", b.getNhaXuatBan(), stock));
                line3.setForeground(ready > 0 ? ACCENT.darker() : muted);
                texts.add(title);
                texts.add(line2);
                texts.add(line3);
            } else {
                Note n = (Note) value;
                title.setText(n.text);
                title.setFont(title.getFont().deriveFont(Font.PLAIN, 13f));
                title.setForeground(n.error ? ERROR_COLOR : muted);
                texts.add(title);
            }
            return row;
        }

        private Color borderColor() {
            Color c = UIManager.getColor("Component.borderColor");
            return c != null ? c : new Color(0, 0, 0, 40);
        }
    }

    private static Color blend(Color base, Color over) {
        if (base == null) base = Color.WHITE;
        float a = over.getAlpha() / 255f;
        return new Color(
                Math.round(base.getRed() * (1 - a) + over.getRed() * a),
                Math.round(base.getGreen() * (1 - a) + over.getGreen() * a),
                Math.round(base.getBlue() * (1 - a) + over.getBlue() * a));
    }

    /** Ghép các phần không rỗng, cách nhau bằng sep ("Nguyễn Văn A · Văn học"). */
    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.trim());
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
