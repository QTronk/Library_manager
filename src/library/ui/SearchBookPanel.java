package library.ui;

import library.model.Book;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SearchBookPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã đầu sách", "Tên sách", "Tác giả", "Thể loại", "Nhà xuất bản", "Giá tiền"
    };
    private static final int COL_MA = 0;
    private static final int COL_TEN = 1;
    private static final int COL_TAC_GIA = 2;
    private static final int COL_THE_LOAI = 3;
    private static final int COL_NXB = 4;
    private static final int COL_GIA = 5;

    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột "Tên sách" co giãn để nhận phần dư khi phóng to cửa sổ.
    private static final int[][] COLUMN_WIDTHS = {
            {90, 100, 120},
            {160, 240, UiTable.FLEX},
            {120, 170, 260},
            {130, 180, 260},
            {150, 200, 300},
            {90, 110, 150}
    };
    private static final int[] ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.LEFT,
            SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.RIGHT
    };

    private JTextField searchField;
    private JButton searchButton;
    private JLabel statusLabel;
    private JTable table;
    private DefaultTableModel model;
    private HeaderSorter sorter; // Sắp xếp khi bấm tiêu đề cột (mặc định: Mã đầu sách, tăng dần)

    private List<Book> books = new ArrayList<>(); // Danh sách đang hiển thị trên bảng
    private boolean loading = false;              // Đang chờ Backend trả lời?
    private boolean loadedOnce = false;           // Đã tải dữ liệu thành công ít nhất 1 lần chưa?

    private final NumberFormat priceFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
    private final Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN")); // Sắp chữ theo tiếng Việt

    public SearchBookPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {

        JLabel title = new JLabel("Tra cứu sách");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchField = new JTextField();
        searchButton = new JButton("Tìm kiếm");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.NORTH);
        top.add(searchPanel, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);

        // Không cho sửa trực tiếp ô trong bảng (double-click)
        model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new UiTable(model); // Cột cố định (không kéo đổi chỗ được), dòng cao, tooltip khi chữ bị cắt
        UiTable.setColumnWidths(table, COLUMN_WIDTHS);
        UiTable.setColumnAlignments(table, ALIGNMENT);
        sorter = new HeaderSorter(table, COLUMN_NAMES, COL_MA, Set.of(), this::applySort);
        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Lần đầu panel được hiển thị (CardLayout.show) thì tự tải tất cả đầu sách.
        // Nếu lần đầu lỗi (vd. Backend chưa bật) thì lần mở sau sẽ tự thử lại.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (!loadedOnce) {
                    loadAllBooks();
                }
            }
        });

        searchButton.addActionListener(e -> fetchBooks(searchField.getText()));
        searchField.addActionListener(e -> fetchBooks(searchField.getText())); // Nhấn Enter để tìm

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showBookDetail();
            }
        });
    }

    /**
     * MainFrame gọi hàm này mỗi khi người dùng bấm nút "Tra cứu sách" trên sidebar:
     * xóa ô tìm kiếm và tải TẤT CẢ đầu sách từ Backend.
     */
    public void loadAllBooks() {
        searchField.setText("");
        fetchBooks("");
    }

    /**
     * Gửi request SEARCH_BOOKS ở luồng nền, xong thì đổ kết quả lên bảng ở luồng giao diện.
     * keyword rỗng = lấy tất cả.
     */
    private void fetchBooks(String rawKeyword) {
        if (loading) return; // Đang có 1 request chạy rồi, bỏ qua để khỏi chồng chéo

        final String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        setLoading(true);

        SwingWorker<List<Book>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Book> doInBackground() throws Exception {
                return SocketClient.getInstance().searchBooks(keyword);
            }

            @Override
            protected void done() {
                try {
                    books = new ArrayList<>(get());
                    applySort(); // Sắp xếp theo cột đang chọn rồi đổ lên bảng
                    loadedOnce = true;

                    if (keyword.isEmpty()) {
                        statusLabel.setText("Có " + books.size() + " đầu sách. Bấm vào một dòng để xem chi tiết.");
                    } else {
                        statusLabel.setText("Kết quả cho \"" + keyword + "\": " + books.size() + " đầu sách");
                    }

                    if (books.isEmpty() && !keyword.isEmpty()) {
                        JOptionPane.showMessageDialog(SearchBookPanel.this,
                                "Không tìm thấy sách phù hợp.");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    boolean connectionProblem = cause instanceof IOException;
                    String message = cause.getMessage() != null && !cause.getMessage().trim().isEmpty()
                            ? cause.getMessage()
                            : "Đã xảy ra lỗi không xác định.";

                    statusLabel.setText("Không tải được danh sách sách.");
                    JOptionPane.showMessageDialog(SearchBookPanel.this, message,
                            connectionProblem ? "Lỗi kết nối" : "Tra cứu thất bại",
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
        searchButton.setEnabled(!value);
        searchField.setEnabled(!value);
        if (value) {
            statusLabel.setText("Đang tải dữ liệu từ máy chủ...");
        }
    }

    // ==========================================================
    // SẮP XẾP (làm ở Client, không gọi lại Backend)
    // ==========================================================
    private void applySort() {
        books.sort(buildComparator(sorter.getColumn(), sorter.isAscending()));
        fillTable(books);
    }

    private Comparator<Book> buildComparator(int column, boolean asc) {
        Comparator<Book> primary;
        switch (column) {
            case COL_TEN:
                primary = (a, b) -> collator.compare(nz(a.getTenSach()), nz(b.getTenSach()));
                break;
            case COL_TAC_GIA:
                primary = (a, b) -> collator.compare(nz(a.getTacGia()), nz(b.getTacGia()));
                break;
            case COL_THE_LOAI:
                primary = (a, b) -> collator.compare(nz(a.getTheLoai()), nz(b.getTheLoai()));
                break;
            case COL_NXB:
                primary = (a, b) -> collator.compare(nz(a.getNhaXuatBan()), nz(b.getNhaXuatBan()));
                break;
            case COL_GIA:
                primary = (a, b) -> Double.compare(a.getGiaTien(), b.getGiaTien());
                break;
            case COL_MA:
            default:
                primary = (a, b) -> Integer.compare(a.getMaDauSach(), b.getMaDauSach());
                break;
        }

        final Comparator<Book> directed = asc ? primary : primary.reversed();
        // Bằng nhau thì xếp theo mã đầu sách để thứ tự luôn ổn định
        final Comparator<Book> tieBreak = (a, b) -> Integer.compare(a.getMaDauSach(), b.getMaDauSach());
        return directed.thenComparing(tieBreak);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void fillTable(List<Book> list) {
        model.setRowCount(0);
        for (Book book : list) {
            Object[] row = {
                    book.getMaDauSach(),
                    book.getTenSach(),
                    book.getTacGia(),
                    book.getTheLoai(),
                    book.getNhaXuatBan(),
                    priceFormat.format(book.getGiaTien()) + " đ"
            };
            model.addRow(row);
        }
    }

    // Popup chi tiết: tạm dùng dữ liệu đã có sẵn trong bảng (chưa gọi thêm API)
    private void showBookDetail() {

        int row = table.getSelectedRow();
        if (row == -1) {
            return;
        }

        String maDauSach = model.getValueAt(row, 0).toString();

        for (Book book : books) {

            if (String.valueOf(book.getMaDauSach()).equals(maDauSach)) {
                String message =
                        "Tên sách: " + book.getTenSach()
                        + "\nTác giả: " + book.getTacGia()
                        + "\nThể loại: " + book.getTheLoai()
                        + "\nNhà xuất bản: " + book.getNhaXuatBan()
                        + "\nGiá tiền: " + priceFormat.format(book.getGiaTien()) + " đ";

                JOptionPane.showMessageDialog(
                        this,
                        message,
                        "Thông tin sách",
                        JOptionPane.INFORMATION_MESSAGE
                );

                break;
            }
        }
    }
}
