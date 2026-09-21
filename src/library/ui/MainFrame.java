package library.ui;

import javax.swing.*;

import java.awt.*;

public class MainFrame extends JFrame {

    private JPanel contentPanel;

    public MainFrame() {
        setTitle("Library Management");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createUI();
    }

    private void createUI() {
        JPanel header = new JPanel(new BorderLayout());

        JLabel title = new JLabel("   Library Management");
        title.setFont(new Font("Arial", Font.BOLD, 20));

        JButton loginButton = new JButton("Đăng nhập");

        header.add(title, BorderLayout.WEST);
        header.add(loginButton, BorderLayout.EAST);


        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));

        JButton homeButton = new JButton("Trang chủ");
        JButton searchButton = new JButton("Tra cứu sách");

        menu.add(homeButton);
        menu.add(Box.createVerticalStrut(10));
        menu.add(searchButton);


        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(createHomePanel(), BorderLayout.CENTER);

        searchButton.addActionListener(e -> {
            showSearchPanel();
        });

        homeButton.addActionListener(e -> {
            contentPanel.removeAll();
            contentPanel.add(createHomePanel(), BorderLayout.CENTER);
            contentPanel.revalidate();
            contentPanel.repaint();
        });

        add(header, BorderLayout.NORTH);
        add(menu, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel  createHomePanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        JLabel label = new JLabel("Chào mừng đến với Thư viện");
        label.setFont(new Font("Arial", Font.BOLD, 24));

        panel.add(label);

        return panel;
    }

    private void showSearchPanel() {
        contentPanel.removeAll();
        contentPanel.add(new SearchBookPanel(), BorderLayout.CENTER);
        contentPanel.revalidate(); //tính toán lại layout
        contentPanel.repaint(); // vẽ lại giao diện
    }
}