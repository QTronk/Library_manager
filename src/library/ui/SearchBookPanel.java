package library.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import library.model.Book;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SearchBookPanel extends JPanel {

    private JTextField searchField;
    private JTable table;
    private DefaultTableModel model;
    private List<Book> books;

    public SearchBookPanel() {
        setLayout(new BorderLayout(10, 10));
        createFakeData();
        createUI();
    }

    private void createFakeData() {
        books = new ArrayList<>();

        books.add(new Book("B01", "Java Core", "James Gosling", "Lập trình", 5));
        books.add(new Book("B02", "Clean Code", "Robert C. Martin", "Lập trình", 3));
        books.add(new Book("B03", "C++ Programming", "Bjarne Stroustrup", "Lập trình", 2));
        books.add(new Book("B04", "Doraemon", "Fujiko F. Fujio", "Truyện", 0));
    }

    private void createUI() {

        JLabel title = new JLabel("Tra cứu sách");
        title.setFont(new Font("Arial", Font.BOLD, 22));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchField = new JTextField();
        JButton searchButton = new JButton("Tìm kiếm");
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.add(title, BorderLayout.NORTH);
        top.add(searchPanel, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);

        String[] columns = {
                "Mã sách",
                "Tên sách",
                "Tác giả",
                "Thể loại",
                "Số lượng"
        };

        model = new DefaultTableModel(columns, 0);
        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);
        loadBooks(books);

        searchButton.addActionListener(e -> searchBooks());
        searchField.addActionListener(e -> searchBooks()); //cho nhấn Enter để tìm

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showBookDetail();
            }
        });
    }

    private void loadBooks(List<Book> list) {
        model.setRowCount(0);
        for (Book book : list) {
            Object[] row = {
                    book.getMaSach(),
                    book.getTenSach(),
                    book.getTacGia(),
                    book.getTheLoai(),
                    book.getSoLuong()
            };
            model.addRow(row);
        }
    }

    private void searchBooks() {

        String keyword = searchField.getText()
                .trim() //bỏ khoảng trắng đầu cuối
                .toLowerCase();
        List<Book> result = new ArrayList<>();

        for (Book book : books) {
            if (book.getTenSach().toLowerCase().contains(keyword)
                    || book.getTacGia().toLowerCase().contains(keyword)
                    || book.getTheLoai().toLowerCase().contains(keyword)) {
                result.add(book);
            }
        }

        loadBooks(result);

        if (result.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy sách phù hợp."
            );
        }
    }

    private void showBookDetail() {

        int row = table.getSelectedRow();
        if (row == -1) {
            return;
        }

        String maSach = model.getValueAt(row, 0).toString();

        for (Book book : books) {

            if (book.getMaSach().equals(maSach)) {
                String message =
                        "Tên sách: " + book.getTenSach()
                        + "\nTác giả: " + book.getTacGia()
                        + "\nThể loại: " + book.getTheLoai()
                        + "\nSố lượng còn: " + book.getSoLuong();

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