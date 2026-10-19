package library.ui;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

/**
 * Sắp xếp khi bấm vào tiêu đề cột (làm hoàn toàn ở phía Client, không gọi Backend).
 *
 * - Bấm một cột: sắp xếp theo cột đó, tăng dần, tiêu đề hiện mũi tên ▼.
 * - Bấm lại đúng cột đang sắp xếp: đảo thành giảm dần, mũi tên ▲.
 *
 * Lớp này chỉ ghi nhớ cột và chiều sắp xếp, cập nhật mũi tên trên tiêu đề rồi gọi onChange;
 * việc sắp xếp dữ liệu thật do màn hình tự làm trong onChange (dùng getColumn() và isAscending()).
 * Bảng phải không cho kéo đổi chỗ cột (UiTable đã tắt) để chỉ số cột luôn cố định.
 */
public class HeaderSorter {

    private final JTable table;
    private final String[] baseNames;
    private final Set<Integer> unsortable;
    private final Runnable onChange;

    private int column;
    private boolean ascending = true;

    /**
     * @param baseNames         tên gốc của từng cột (không kèm mũi tên)
     * @param defaultColumn     cột sắp xếp mặc định (tăng dần)
     * @param unsortableColumns các cột không cho sắp xếp (ví dụ cột nút bấm)
     * @param onChange          được gọi mỗi khi người dùng đổi cột/chiều sắp xếp
     */
    public HeaderSorter(JTable table, String[] baseNames, int defaultColumn,
                        Set<Integer> unsortableColumns, Runnable onChange) {
        this.table = table;
        this.baseNames = baseNames;
        this.column = defaultColumn;
        this.unsortable = unsortableColumns;
        this.onChange = onChange;

        final JTableHeader header = table.getTableHeader();
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || header.getResizingColumn() != null) return;
                int viewColumn = header.columnAtPoint(e.getPoint());
                if (viewColumn < 0) return;
                onHeaderClicked(HeaderSorter.this.table.convertColumnIndexToModel(viewColumn));
            }
        });

        refreshHeaders();
    }

    public int getColumn() {
        return column;
    }

    public boolean isAscending() {
        return ascending;
    }

    private void onHeaderClicked(int clicked) {
        if (unsortable.contains(clicked)) return;

        if (clicked == column) {
            ascending = !ascending;   // Bấm lại cùng cột -> đảo chiều
        } else {
            column = clicked;         // Cột mới -> bắt đầu từ tăng dần
            ascending = true;
        }
        refreshHeaders();
        onChange.run();
    }

    /** Gắn mũi tên vào tiêu đề cột đang sắp xếp: ▼ = tăng dần, ▲ = giảm dần. */
    public void refreshHeaders() {
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < columns.getColumnCount(); i++) {
            TableColumn tableColumn = columns.getColumn(i);
            int modelIndex = tableColumn.getModelIndex();
            if (modelIndex < 0 || modelIndex >= baseNames.length) continue;

            String text = baseNames[modelIndex];
            if (modelIndex == column) {
                text += ascending ? "  \u25BC" : "  \u25B2";
            }
            tableColumn.setHeaderValue(text);
        }
        table.getTableHeader().repaint();
    }
}
