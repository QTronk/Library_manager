package library.ui;


import library.model.Response;
import library.service.SocketClient;

import com.fasterxml.jackson.databind.ObjectMapper;


import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ReportPanel extends JPanel {
    private JComboBox<String> cbxReportType;
    private JTextField txtTuNgay, txtDenNgay;
    private JButton btnThongKe;
    private UiTable tableReport;
    private DefaultTableModel tableModel;


    public ReportPanel() {
        setLayout(new BorderLayout(10, 10));
        createUI();
    }


    private void createUI() {
        // VÙNG TÌM KIẾM (NORTH)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        
        topPanel.add(new JLabel("Loại báo cáo:"));
        cbxReportType = new JComboBox<>(new String[]{
            "Top đầu sách mượn nhiều (TOP_BOOKS)", 
            "Lượt mượn/trả theo kỳ (BORROW_STATS)", 
            "Độc giả trễ hạn/nợ phạt (OVERDUE_READERS)", 
            "Doanh thu tiền phạt (FINE_REVENUE)"
        });
        topPanel.add(cbxReportType);


        topPanel.add(new JLabel("Từ ngày:"));
        txtTuNgay = new JTextField("2026-01-01", 10);
        topPanel.add(txtTuNgay);


        topPanel.add(new JLabel("Đến ngày:"));
        txtDenNgay = new JTextField("2026-12-31", 10);
        topPanel.add(txtDenNgay);


        btnThongKe = new JButton("Xem Báo Cáo");
        btnThongKe.addActionListener(e -> loadReport());
        topPanel.add(btnThongKe);


        add(topPanel, BorderLayout.NORTH);


        // VÙNG BẢNG DỮ LIỆU (CENTER)
        tableModel = new DefaultTableModel();
        tableReport = new UiTable(tableModel); // Dùng component UiTable đã có của bạn
        add(new JScrollPane(tableReport), BorderLayout.CENTER);
    }


    private void loadReport() {
        btnThongKe.setEnabled(false);
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);


        // Parse loại báo cáo (lấy keyword trong ngoặc)
        String selectedType = cbxReportType.getSelectedItem().toString();
        String loaiBaoCao = selectedType.substring(selectedType.indexOf("(") + 1, selectedType.indexOf(")"));


        SwingWorker<Response, Void> worker = new SwingWorker<>() {
            @Override
            protected Response doInBackground() throws Exception {
                // Xây dựng Payload để bắn thẳng sang Backend thật
                Map<String, Object> payload = new HashMap<>();
                payload.put("loaiBaoCao", loaiBaoCao);
                
                String tuNgay = txtTuNgay.getText().trim();
                String denNgay = txtDenNgay.getText().trim();
                if (!tuNgay.isEmpty()) payload.put("tuNgay", tuNgay);
                if (!denNgay.isEmpty()) payload.put("denNgay", denNgay);
                
                payload.put("limit", 100);
                payload.put("groupBy", "MONTH"); // Dùng cho BORROW_STATS, FINE_REVENUE


                // GỌI THẲNG API BACKEND
                return SocketClient.getInstance().sendRequest("GET_REPORTS", payload);
            }


            @Override
            protected void done() {
                try {
                    Response res = get();
                    if ("SUCCESS".equals(res.getStatus())) {
                        renderTableData(loaiBaoCao, res.getData());
                    } else {
                        JOptionPane.showMessageDialog(ReportPanel.this, res.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ReportPanel.this, "Lỗi kết nối Backend: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnThongKe.setEnabled(true);
                }
            }
        };
        worker.execute();
    }


    @SuppressWarnings("unchecked")
    private void renderTableData(String loaiBaoCao, Object rawData) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            if (rawData == null) {
                JOptionPane.showMessageDialog(this, "Không có dữ liệu báo cáo.");
                return;
            }
            
            Map<String, Object> dataMap = mapper.convertValue(rawData, Map.class);
            
            switch (loaiBaoCao) {
                case "TOP_BOOKS":
                    tableModel.setColumnIdentifiers(new Object[]{"Mã Đầu Sách", "Tên Sách", "Số Lượt Mượn"});
                    List<Map<String, Object>> topSach = (List<Map<String, Object>>) dataMap.get("topSach");
                    if (topSach != null) {
                        for (Map<String, Object> sach : topSach) {
                            tableModel.addRow(new Object[]{sach.get("maDauSach"), sach.get("tenSach"), sach.get("soLuotMuon")});
                        }
                    }
                    break;
                    
                case "BORROW_STATS":
                    tableModel.setColumnIdentifiers(new Object[]{"Kỳ", "Số Lượt Mượn", "Số Lượt Trả"});
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) dataMap.get("rows");
                    if (rows != null) {
                        for (Map<String, Object> row : rows) {
                            tableModel.addRow(new Object[]{row.get("ky"), row.get("soLuotMuon"), row.get("soLuotTra")});
                        }
                    }
                    break;
                    
                case "OVERDUE_READERS":
                    tableModel.setColumnIdentifiers(new Object[]{"Mã Thẻ", "Họ Tên", "Email", "Số ĐT", "Sách Quá Hạn", "Tiền Phạt Chưa Nộp"});
                    List<Map<String, Object>> danhSach = (List<Map<String, Object>>) dataMap.get("danhSach");
                    if (danhSach != null) {
                        for (Map<String, Object> docgia : danhSach) {
                            tableModel.addRow(new Object[]{
                                docgia.get("maThe"), docgia.get("hoTen"), docgia.get("email"), 
                                docgia.get("soDienThoai"), docgia.get("soSachQuaHan"), docgia.get("tienPhatChuaNop")
                            });
                        }
                    }
                    break;
                    
                case "FINE_REVENUE":
                    tableModel.setColumnIdentifiers(new Object[]{"Kỳ", "Số Phiếu Phạt", "Tổng Tiền Phạt"});
                    // Thêm 1 dòng tổng quan
                    tableModel.addRow(new Object[]{
                        "TỔNG CỘNG", 
                        dataMap.get("tongSoPhieu"), 
                        dataMap.get("tongTienPhat") + " (Đã thu: " + dataMap.get("daThu") + ")"
                    });
                    List<Map<String, Object>> theoKy = (List<Map<String, Object>>) dataMap.get("theoKy");
                    if (theoKy != null) {
                        for (Map<String, Object> ky : theoKy) {
                            tableModel.addRow(new Object[]{ky.get("ky"), ky.get("soPhieu"), ky.get("tongTienPhat")});
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi phân tích dữ liệu: " + e.getMessage());
        }
    }
}