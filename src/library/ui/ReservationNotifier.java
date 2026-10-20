package library.ui;

import library.model.Reservation;
import library.service.SocketClient;

import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Thông báo sách đặt trước cho Độc giả (UC-10, phía Client).
 *
 * Backend không đẩy thông báo xuống Client (giao thức request / response), nên theo API.md mục 3.5.6 Client phải
 * chủ động hỏi định kỳ: gọi GET_MY_RESERVATIONS ngay khi đăng nhập rồi lặp lại mỗi vài phút. Lượt đặt trước nào có
 * trạng thái NOTIFIED (sách đã về quầy, đang giữ cho mình) chính là một thông báo; nội dung lấy từ trường "thongBao".
 *
 * - start(): bắt đầu cho một phiên đăng nhập (xóa dữ liệu cũ, tải ngay, rồi hẹn giờ tải lại).
 * - stop(): dừng và xóa dữ liệu (gọi khi đăng xuất); mọi kết quả đang chờ của phiên cũ bị bỏ qua.
 * - Lỗi khi tự tải định kỳ được bỏ qua im lặng (không làm phiền người dùng); chỉ báo lỗi khi người dùng bấm làm mới.
 * - "Mới" = lượt NOTIFIED lần đầu xuất hiện trong phiên đăng nhập này; dùng để chỉ hiện hộp thoại báo một lần.
 *
 * Mọi hàm gọi và mọi Listener chạy trên luồng giao diện (EDT).
 */
public class ReservationNotifier {

    public interface Listener {
        /**
         * Mỗi lần tải xong danh sách.
         *
         * @param notified      toàn bộ thông báo hiện có (lượt NOTIFIED), có thể rỗng
         * @param newlyNotified những lượt lần đầu xuất hiện trong phiên này (chưa từng báo)
         */
        void onChanged(List<Reservation> notified, List<Reservation> newlyNotified);

        /** Tải thất bại khi người dùng chủ động làm mới (tải định kỳ thì không gọi). */
        default void onError(String message) { }
    }

    private static final String NOTIFIED = "NOTIFIED";
    private static final int POLL_INTERVAL_MS = 2 * 60 * 1000; // "mỗi vài phút" theo API.md

    private final List<Listener> listeners = new ArrayList<>();
    private final Set<Integer> seen = new HashSet<>();   // Mã đặt trước đã từng được báo trong phiên này
    private List<Reservation> notified = new ArrayList<>();
    private final javax.swing.Timer timer;

    private boolean running = false;
    private boolean fetching = false;
    private boolean manualPending = false; // Có người bấm làm mới trong lúc một lần tải đang chạy
    private int session = 0;               // Tăng mỗi lần start/stop để bỏ qua kết quả của phiên cũ

    public ReservationNotifier() {
        timer = new javax.swing.Timer(POLL_INTERVAL_MS, e -> fetch(false));
        timer.setRepeats(true);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Thông báo hiện có (bản sao, chỉ đọc). */
    public List<Reservation> getNotified() {
        return new ArrayList<>(notified);
    }

    /** Bắt đầu cho một phiên đăng nhập mới của Độc giả: tải ngay rồi tải lại định kỳ. */
    public void start() {
        stop();
        running = true;
        session++;
        timer.restart();
        fetch(false);
    }

    /** Dừng và xóa mọi dữ liệu (gọi khi đăng xuất để người đăng nhập sau không thấy thông báo của người trước). */
    public void stop() {
        running = false;
        session++;
        timer.stop();
        fetching = false;
        manualPending = false;
        seen.clear();
        notified = new ArrayList<>();
    }

    /**
     * Người dùng chủ động làm mới. Kết quả (hoặc lỗi) được báo qua Listener.
     *
     * @return false nếu chưa bắt đầu (sẽ không có kết quả nào được báo)
     */
    public boolean refreshNow() {
        if (!running) return false;
        fetch(true);
        return true;
    }

    private void fetch(boolean manual) {
        if (!running) return;
        if (fetching) {
            if (manual) manualPending = true; // Lần tải đang chạy sẽ báo kết quả (hoặc lỗi) cho người bấm
            return;
        }
        fetching = true;
        manualPending = manual;
        final int mySession = session;

        SwingWorker<List<Reservation>, Void> worker = new SwingWorker<List<Reservation>, Void>() {
            @Override
            protected List<Reservation> doInBackground() throws Exception {
                return SocketClient.getInstance().getMyReservations();
            }

            @Override
            protected void done() {
                if (mySession != session) return; // Đã đăng xuất / bắt đầu phiên mới
                fetching = false;
                boolean reportError = manualPending;
                manualPending = false;

                try {
                    apply(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    if (reportError) {
                        Throwable cause = UiUtil.unwrap(ex);
                        String message = (UiUtil.isConnectionProblem(cause) ? "Lỗi kết nối: " : "")
                                + UiUtil.messageOf(cause);
                        for (Listener l : new ArrayList<>(listeners)) {
                            l.onError(message);
                        }
                    }
                }
            }
        };
        worker.execute();
    }

    private void apply(List<Reservation> reservations) {
        List<Reservation> now = new ArrayList<>();
        List<Reservation> fresh = new ArrayList<>();
        for (Reservation r : reservations) {
            if (!NOTIFIED.equals(r.trangThai)) continue;
            now.add(r);
            if (seen.add(r.maDatTruoc)) {
                fresh.add(r);
            }
        }
        notified = now;

        for (Listener l : new ArrayList<>(listeners)) {
            l.onChanged(getNotified(), new ArrayList<>(fresh));
        }
    }
}
