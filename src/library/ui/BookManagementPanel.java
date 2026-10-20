package library.ui;

import library.model.Book;
import library.model.BookDetail;
import library.model.Categories;
import library.service.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình "Quản lý kho sách" của Thủ thư (UC-12, phần ĐẦU SÁCH): thêm, sửa, xóa đầu sách.
 * Phần cuốn sách vật lý (mã vạch) và danh mục Tác giả / NXB / Thể loại sẽ làm sau.
 *
 * - Mở ra là tự tải TOÀN BỘ đầu sách (SEARCH_BOOKS); ô tìm kiếm khớp theo từ khóa do Backend quyết định.
 *   Bấm tiêu đề cột để sắp xếp (làm ở Client).
 * - Bấm vào một dòng để xem chi tiết (BookDetailDialog), trong đó có "Cập nhật thông tin" và "Xóa đầu sách".
 * - Nút "+ Thêm đầu sách" mở hộp thoại thêm (AddBookDialog). Danh mục Tác giả / Thể loại / NXB cho các ô chọn
 *   được tải mới từ Backend mỗi lần mở hộp thoại.
 */
public class BookManagementPanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "Mã", "Tên sách", "Tác giả", "Thể loại", "Nhà xuất bản", "Giá tiền", "Vị trí", "Tổng SL", "Sẵn sàng"
    };
    private static final int COL_MA = 0;
    private static final int COL_TEN = 1;
    private static final int COL_TAC_GIA = 2;
    private static final int COL_THE_LOAI = 3;
    private static final int COL_NXB = 4;
    private static final int COL_GIA = 5;
    private static final int COL_VI_TRI = 6;
    private static final int COL_TONG = 7;
    private static final int COL_KHA_DUNG = 8;

    // Mỗi cột: {nhỏ nhất, ưu tiên, lớn nhất}. Cột "Tên sách" co giãn để nhận phần dư khi phóng to cửa sổ.
    private static final int[][] COLUMN_WIDTHS = {
            {55, 60, 80},
            {160, 240, UiTable.FLEX},
            {110, 150, 220},
            {110, 150, 220},
            {120, 160, 240},
            {85, 100, 130},
            {90, 110, 170},
            {65, 70, 90},
            {75, 80, 100}
    };
    private static final int[] ALIGNMENT = {
            SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.LEFT,
            SwingConstants.LEFT, SwingConstants.RIGHT, SwingConstants.LEFT,
            SwingConstants.CENTER, SwingConstants.CENTER
    };

    private static final DateTimeFormatter TIME_VN = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField keywordField = new JTextField(26);
    private final JButton searchButton = new JButton("Tìm kiếm");
    private final JButton addButton = new JButton("+ Thêm đầu sách");
    private final JButton refreshButton = new JButton("Làm mới");
    private JTable table;
    private DefaultTableModel model;
    private JLabel statusLabel;
    private HeaderSorter sorter; // Sắp xếp khi bấm tiêu đề cột (mặc định: Mã, tăng dần)

    private final List<Book> books = new ArrayList<>(); // Thứ tự = thứ tự dòng trên bảng
    private String lastKeyword = "";   // Từ khóa của lần tải gần nhất (để tải lại sau khi thêm / sửa / xóa)
    private boolean loading = false;   // Đang tải danh sách
    private boolean busy = false;      // Đang tải dữ liệu để mở hộp thoại thêm / chi tiết
    private int latestRequestId = 0;   // Để bỏ qua kết quả của request đã cũ (ví dụ đăng xuất giữa chừng)

    private final NumberFormat priceFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
    private final Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN")); // Sắp chữ theo tiếng Việt

    public BookManagementPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }

    private void createUI() {
        JLabel title = new JLabel("Quản lý kho sách");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        addButton.addActionListener(e -> openAddDialog());
        refreshButton.addActionListener(e -> loadAll());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(addButton);
        actions.add(refreshButton);

        JPanel titleRow = new JPanel(new BorderLayout(10, 10));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(actions, BorderLayout.EAST);

        keywordField.putClientProperty("JTextField.placeholderText", "Tìm theo tên sách, tác giả, thể loại, nhà xuất bản");
        searchButton.addActionListener(e -> search());
        keywordField.addActionListener(e -> search()); // Enter trong ô từ khóa cũng tìm

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterRow.add(keywordField);
        filterRow.add(searchButton);

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(titleRow, BorderLayout.NORTH);
        north.add(filterRow, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        // Bảng chỉ đọc: mọi thao tác sửa / xóa làm trong hộp thoại chi tiết
        model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new UiTable(model);
        UiTable.setColumnWidths(table, COLUMN_WIDTHS);
        UiTable.setColumnAlignments(table, ALIGNMENT);
        sorter = new HeaderSorter(table, COLUMN_NAMES, COL_MA, Set.of(), this::applySort);

        // Bấm vào một dòng -> xem chi tiết
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < books.size()) {
                    openDetail(books.get(row));
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // Mỗi lần mở màn hình: xóa từ khóa và tải toàn bộ danh sách
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                loadAll();
            }
        });
    }

    /** Xóa dữ liệu đang hiển thị (MainFrame gọi khi đăng xuất). */
    public void clear() {
        latestRequestId++; // Bỏ qua mọi request đang chạy dở
        loading = false;
        busy = false;
        books.clear();
        model.setRowCount(0);
        keywordField.setText("");
        lastKeyword = "";
        statusLabel.setText(" ");
        updateControls();
    }

    // ==========================================================
    // TẢI / TÌM KIẾM
    // ==========================================================
    private void loadAll() {
        keywordField.setText("");
        loadBooks("");
    }

    private void search() {
        loadBooks(keywordField.getText().trim());
    }

    private void reloadCurrent() {
        loadBooks(lastKeyword);
    }

    private void loadBooks(final String keyword) {
        if (loading) return;

        final int requestId = ++latestRequestId;
        lastKeyword = keyword;
        loading = true;
        updateControls();
        statusLabel.setText("Đang tải dữ liệu từ máy chủ...");

        SwingWorker<List<Book>, Void> worker = new SwingWorker<List<Book>, Void>() {
            @Override
            protected List<Book> doInBackground() throws Exception {
                return SocketClient.getInstance().searchBooks(keyword);
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất / có request mới hơn
                loading = false;
                updateControls();

                try {
                    books.clear();
                    books.addAll(get());
                    applySort(); // Sắp xếp theo cột đang chọn rồi đổ lên bảng

                    String time = " (Cập nhật lúc " + LocalTime.now().format(TIME_VN) + ")";
                    if (books.isEmpty()) {
                        statusLabel.setText((keyword.isEmpty() ? "Chưa có đầu sách nào." : "Không tìm thấy đầu sách phù hợp.") + time);
                    } else {
                        statusLabel.setText((keyword.isEmpty() ? "Có " : "Tìm thấy ") + books.size()
                                + " đầu sách. Bấm vào một dòng để xem chi tiết, sửa hoặc xóa." + time);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được danh sách đầu sách.");
                    JOptionPane.showMessageDialog(BookManagementPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải danh sách thất bại",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void updateControls() {
        boolean idle = !loading && !busy;
        searchButton.setEnabled(idle);
        addButton.setEnabled(idle);
        refreshButton.setEnabled(idle);
        refreshButton.setText(loading ? "Đang tải..." : "Làm mới");
    }

    // ==========================================================
    // THÊM / CHI TIẾT
    // ==========================================================
    private void openAddDialog() {
        if (loading || busy) return;
        busy = true;
        updateControls();
        statusLabel.setText("Đang tải danh mục...");
        final int requestId = latestRequestId;

        SwingWorker<Categories, Void> worker = new SwingWorker<Categories, Void>() {
            @Override
            protected Categories doInBackground() throws Exception {
                return SocketClient.getInstance().getCategories();
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất giữa chừng
                busy = false;
                updateControls();

                try {
                    Categories categories = get();
                    statusLabel.setText(" ");
                    if (categories.tacGiaList.isEmpty() || categories.theLoaiList.isEmpty()
                            || categories.nhaXuatBanList.isEmpty()) {
                        JOptionPane.showMessageDialog(BookManagementPanel.this,
                                "Chưa có đủ danh mục để thêm đầu sách: cần có ít nhất một Tác giả, một Thể loại và một Nhà xuất bản.",
                                "Thêm đầu sách", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    Window owner = SwingUtilities.getWindowAncestor(BookManagementPanel.this);
                    AddBookDialog dialog = new AddBookDialog(owner, categories);
                    dialog.setVisible(true); // Hộp thoại modal: dòng tiếp theo chạy khi hộp thoại đã đóng

                    Integer createdId = dialog.getCreatedId();
                    if (createdId == null) return; // Người dùng hủy hoặc chưa tạo được

                    JOptionPane.showMessageDialog(BookManagementPanel.this,
                            "Thêm đầu sách mới thành công!\nMã đầu sách: " + createdId
                                    + "\n\nĐầu sách mới chưa có cuốn sách nào (số lượng = 0).",
                            "Thêm đầu sách", JOptionPane.INFORMATION_MESSAGE);
                    loadAll(); // Tải lại để thấy ngay đầu sách vừa thêm
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được danh mục.");
                    JOptionPane.showMessageDialog(BookManagementPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải danh mục thất bại",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /** Dữ liệu cần để mở hộp thoại chi tiết: chi tiết đầu sách và danh mục cho các ô chọn. */
    private static class DetailData {
        BookDetail detail;
        Categories categories;
    }

    private void openDetail(final Book book) {
        if (loading || busy) return;
        busy = true;
        updateControls();
        statusLabel.setText("Đang tải chi tiết đầu sách...");
        final int requestId = latestRequestId;

        SwingWorker<DetailData, Void> worker = new SwingWorker<DetailData, Void>() {
            @Override
            protected DetailData doInBackground() throws Exception {
                DetailData data = new DetailData();
                data.detail = SocketClient.getInstance().getBookDetail(book.getMaDauSach());
                data.categories = SocketClient.getInstance().getCategories();
                return data;
            }

            @Override
            protected void done() {
                if (requestId != latestRequestId) return; // Đã đăng xuất giữa chừng
                busy = false;
                updateControls();

                try {
                    DetailData data = get();
                    statusLabel.setText(" ");

                    Window owner = SwingUtilities.getWindowAncestor(BookManagementPanel.this);
                    BookDetailDialog dialog = new BookDetailDialog(owner, data.detail, data.categories);
                    dialog.setVisible(true); // Hộp thoại modal: dòng tiếp theo chạy khi hộp thoại đã đóng

                    if (dialog.isModified()) {
                        reloadCurrent(); // Có sửa / xóa thành công -> tải lại theo từ khóa hiện tại
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = UiUtil.unwrap(ex);
                    statusLabel.setText("Không tải được chi tiết đầu sách.");
                    JOptionPane.showMessageDialog(BookManagementPanel.this, UiUtil.messageOf(cause),
                            UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối" : "Tải chi tiết thất bại",
                            JOptionPane.ERROR_MESSAGE);
                    reloadCurrent(); // Có thể đầu sách vừa bị xóa ở nơi khác: tải lại danh sách cho đúng
                }
            }
        };
        worker.execute();
    }

    // ==========================================================
    // SẮP XẾP (làm ở Client, không gọi lại Backend)
    // ==========================================================
    private void applySort() {
        books.sort(buildComparator(sorter.getColumn(), sorter.isAscending()));
        fillTable();
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
            case COL_VI_TRI:
                primary = (a, b) -> collator.compare(nz(a.getViTri()), nz(b.getViTri()));
                break;
            case COL_TONG:
                primary = (a, b) -> Integer.compare(a.getTongSoLuong(), b.getTongSoLuong());
                break;
            case COL_KHA_DUNG:
                primary = (a, b) -> Integer.compare(a.getSoLuongKhaDung(), b.getSoLuongKhaDung());
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

    private void fillTable() {
        model.setRowCount(0);
        for (Book b : books) {
            model.addRow(new Object[]{
                    b.getMaDauSach(),
                    b.getTenSach(),
                    b.getTacGia(),
                    b.getTheLoai(),
                    b.getNhaXuatBan(),
                    priceFormat.format(b.getGiaTien()) + " đ",
                    b.getViTri(),
                    b.getTongSoLuong(),
                    b.getSoLuongKhaDung()
            });
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
