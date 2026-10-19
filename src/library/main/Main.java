package library.main;

import com.formdev.flatlaf.FlatLightLaf;
import library.ui.MainFrame;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        // Màu xanh lá ngọc lục bảo
        Color emeraldGreen = new Color(16, 185, 129); 

        // Cấu hình đổi màu viền Focus của tất cả ô nhập liệu sang màu xanh lá
        UIManager.put("Component.focusColor", emeraldGreen);
        UIManager.put("TextComponent.focusedBorderColor", emeraldGreen);
        UIManager.put("Component.accentColor", emeraldGreen);

        FlatLightLaf.setup();

        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}