package library.ui;

import java.awt.Dimension;
import java.awt.Window;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/** Các hàm tiện ích dùng chung cho giao diện (định dạng ngày/tiền, escape HTML, đọc lỗi từ SwingWorker). */
public final class UiUtil {

    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private UiUtil() { }

    /** "2026-10-29" hoặc "2026-10-29T09:30:00" -> "29/10/2026"; định dạng lạ thì trả nguyên văn. */
    public static String formatDate(String iso) {
        if (iso == null) return "";
        if (iso.length() >= 10) {
            try {
                return LocalDate.parse(iso.substring(0, 10)).format(DATE_VN);
            } catch (Exception ex) {
                // rơi xuống trả nguyên văn
            }
        }
        return iso;
    }

    /** 15000 -> "15.000 đ" */
    public static String formatMoney(double amount) {
        return NumberFormat.getNumberInstance(VI).format(amount) + " đ";
    }

    /** Escape tối thiểu để chèn text vào JLabel dạng HTML. */
    public static String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Lấy nguyên nhân thật sự từ ExecutionException của SwingWorker. */
    public static Throwable unwrap(ExecutionException ex) {
        return ex.getCause() != null ? ex.getCause() : ex;
    }

    /** Thông báo lỗi hiển thị cho người dùng (có giá trị mặc định nếu lỗi không có nội dung). */
    public static String messageOf(Throwable cause) {
        String message = cause.getMessage();
        return (message != null && !message.trim().isEmpty()) ? message : "Đã xảy ra lỗi không xác định.";
    }

    public static boolean isConnectionProblem(Throwable cause) {
        return cause instanceof IOException;
    }

    /**
     * Cho hộp thoại tự co/giãn lại nếu nội dung hiện tại cần chỗ lớn hơn kích thước đang có.
     *
     * Lý do: hộp thoại được pack() một lần lúc mở. Nếu sau đó nội dung đổi (dòng thông báo dài hơn,
     * ô nhập có viền dày hơn...) khiến kích thước cần thiết lớn hơn cửa sổ, GridBagLayout sẽ chuyển sang
     * dùng kích thước TỐI THIỂU của mọi thành phần: các ô nhập co lại chỉ còn vài pixel và không thấy chữ.
     * Gọi hàm này sau mỗi lần đổi nội dung để tránh lỗi đó.
     */
    public static void refit(Window window) {
        if (!window.isDisplayable()) return; // Chưa pack() lần đầu thì không cần
        Dimension preferred = window.getPreferredSize();
        Dimension current = window.getSize();
        if (preferred.width > current.width || preferred.height > current.height) {
            window.pack();
            window.setLocationRelativeTo(window.getOwner());
        }
    }
}
