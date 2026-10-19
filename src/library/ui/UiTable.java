package library.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

/**
 * Bảng dùng chung cho các màn hình danh sách, để giao diện đồng nhất và gọn gàng:
 *  - Cố định vị trí cột (không kéo đổi chỗ được), dòng cao hơn, chỉ kẻ đường ngang.
 *  - Cửa sổ rộng: các cột hẹp giữ nguyên, phần dư dồn cho cột "co giãn" (cột có max = Integer.MAX_VALUE).
 *  - Cửa sổ hẹp: hiện thanh cuộn ngang thay vì ép các cột nhỏ lại làm chữ bị cắt.
 *  - Rê chuột vào ô bị cắt chữ thì hiện nội dung đầy đủ (tooltip).
 */
public class UiTable extends JTable {

    public static final int FLEX = Integer.MAX_VALUE; // Dùng làm chiều rộng tối đa của cột co giãn

    public UiTable(TableModel model) {
        super(model);
        setRowHeight(30);
        setShowVerticalLines(false);
        setShowHorizontalLines(true);
        setIntercellSpacing(new Dimension(0, 1));
        setFillsViewportHeight(true);
        setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
        getTableHeader().setReorderingAllowed(false); // Không cho kéo cột đi chỗ khác
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** Đủ chỗ thì bảng giãn vừa khung nhìn; thiếu chỗ thì giữ nguyên bề rộng ưu tiên và cuộn ngang. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport && getPreferredSize().width <= getParent().getWidth();
    }

    /** Chỉ hiện tooltip khi nội dung ô bị cắt bớt. */
    @Override
    public String getToolTipText(MouseEvent e) {
        Point p = e.getPoint();
        int row = rowAtPoint(p);
        int column = columnAtPoint(p);
        if (row < 0 || column < 0) return null;

        Object value = getValueAt(row, column);
        if (value == null) return null;

        Component renderer = prepareRenderer(getCellRenderer(row, column), row, column);
        Rectangle cell = getCellRect(row, column, false);
        if (renderer.getPreferredSize().width > cell.width) {
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * Đặt chiều rộng cho từng cột theo chỉ số cột: widths[i] = {nhỏ nhất, ưu tiên, lớn nhất}.
     * Cột nào cho lớn nhất = UiTable.FLEX sẽ nhận phần chiều rộng dư khi cửa sổ được phóng to.
     * Phải gọi lại sau mỗi lần đổi cấu trúc cột của model.
     */
    public static void setColumnWidths(JTable table, int[][] widths) {
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            TableColumn column = columns.getColumn(i);
            column.setMinWidth(widths[i][0]);
            column.setMaxWidth(widths[i][2]);
            column.setPreferredWidth(widths[i][1]);
        }
    }

    /** Đặt căn lề (SwingConstants.LEFT / CENTER / RIGHT) cho từng cột theo chỉ số cột. */
    public static void setColumnAlignments(JTable table, int[] alignments) {
        CellRenderer renderer = new CellRenderer(alignments);
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setCellRenderer(renderer);
        }
    }

    /** Renderer có căn lề theo cột và đệm 8px hai bên để chữ không dính sát đường kẻ. */
    public static class CellRenderer extends DefaultTableCellRenderer {
        private static final Border CELL_BORDER = BorderFactory.createEmptyBorder(0, 8, 0, 8);
        private final int[] alignments;

        public CellRenderer(int[] alignments) {
            this.alignments = alignments;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelColumn = table.convertColumnIndexToModel(column);
            setHorizontalAlignment(modelColumn >= 0 && modelColumn < alignments.length
                    ? alignments[modelColumn] : SwingConstants.LEFT);
            setBorder(CELL_BORDER);
            return c;
        }
    }
}
