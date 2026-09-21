package library.main;

import javax.swing.SwingUtilities;

import library.ui.MainFrame;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}