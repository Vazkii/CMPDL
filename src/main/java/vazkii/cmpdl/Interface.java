package vazkii.cmpdl;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class Interface {

    private static Frame frame;
    public static OperatorThread operatorThread;

    private static String line1 = "";
    private static String line2 = "";

    public static void openInterface() {
        frame = new Frame();
        setStatus("Idle");
    }

    public static void addLogLine(String s) {
        if (frame != null) {
            String currText = frame.logArea.getText();
            frame.logArea.setText(currText.isEmpty() ? s : (currText + "\n" + s));
        }
    }

    public static void setStatus(String status) {
        setStatus(status, true);
    }

    public static void setStatus(String status, boolean clear) {
        line1 = status;
        if (clear)
            setStatus2("");
        else updateLabel();
    }

    public static void setStatus2(String status) {
        line2 = status;
        updateLabel();
    }

    private static void updateLabel() {
        if (frame != null)
            frame.currentStatus.setText(String.format("<html>%s<br>%s</html>", line1, line2));
    }

    @SuppressWarnings("deprecation")
    public static void finishDownload(boolean killThread) {
        if (operatorThread != null && operatorThread.isAlive() && killThread) {
            operatorThread.interrupt();
            operatorThread.stop();
            operatorThread = null;
        }

        if (frame != null)
            frame.setStopButtonVisibility(false);

        CMPDL.downloading = false;
    }

    public static void error() {
        finishDownload(false);
        setStatus("Errored");
    }

    static final class Frame extends JFrame implements ActionListener, KeyListener {

        private static final long serialVersionUID = -2280547253170432552L;

        JPanel panel;
        JPanel downloadPanel;
        JPanel urlPanel;
        JPanel versionPanel;
        JPanel statusPanel;
        JButton downloadButton;
        JLabel orLabel;
        JButton chooseFileButton;
        JFileChooser fileChooser;
        JLabel urlLabel;
        JLabel versionLabel;
        JScrollPane scrollPane;
        JTextField urlField;
        JTextField versionField;
        JTextArea logArea;
        JLabel currentStatus;
        JButton clearButton;


        public Frame() {
            setSize(800, 640);
            setTitle("Vazkii's Curse Modpack Downloader (CMPDL)");

            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
            downloadPanel = new JPanel();
            downloadPanel.setLayout(new BoxLayout(downloadPanel, BoxLayout.LINE_AXIS));
            downloadPanel.setMaximumSize(new Dimension(1000, 100));
            urlPanel = new JPanel();
            urlPanel.setLayout(new BoxLayout(urlPanel, BoxLayout.PAGE_AXIS));
            versionPanel = new JPanel();
            versionPanel.setLayout(new BoxLayout(versionPanel, BoxLayout.PAGE_AXIS));
            statusPanel = new JPanel();
            downloadButton = new JButton("Download");
            downloadButton.setAlignmentX(CENTER_ALIGNMENT);
            orLabel = new JLabel("or you can");
            orLabel.setAlignmentX(CENTER_ALIGNMENT);
            chooseFileButton = new JButton("Choose local modpack");
            chooseFileButton.setAlignmentX(CENTER_ALIGNMENT);
            fileChooser = new JFileChooser();
            urlLabel = new JLabel("Modpack URL :");
            urlField = new JTextField("", 54);
            versionLabel = new JLabel("Curse File ID :");
            versionField = new JTextField("", 20);

            logArea = new JTextArea(34, 68);
            logArea.setBackground(Color.WHITE);
            logArea.setEditable(false);
            logArea.setLineWrap(true);

            scrollPane = new JScrollPane(logArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

            Border scrollBorder = BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10),
                    BorderFactory.createLineBorder(Color.GRAY, 1));
            scrollPane.setBorder(scrollBorder);
            DefaultCaret caret = (DefaultCaret) logArea.getCaret();
            caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

            currentStatus = new JLabel("", SwingConstants.LEFT);

            clearButton = new JButton("Clear Output");
            clearButton.setAction(new AbstractAction("Clear Output") {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    logArea.setText("");
                }
            });

            urlPanel.add(urlLabel);
            urlPanel.add(Box.createRigidArea(new Dimension(20, 5)));
            urlPanel.add(urlField);
            urlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            versionPanel.add(versionLabel);
            versionPanel.add(Box.createRigidArea(new Dimension(26, 5)));
            versionPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            versionPanel.add(versionField);

            downloadPanel.add(urlPanel);
            downloadPanel.add(versionPanel);
            panel.add(downloadPanel);
            panel.add(downloadButton);
            panel.add(orLabel);
            panel.add(chooseFileButton);
            panel.add(scrollPane);
            statusPanel.setLayout(new BorderLayout());
            statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            statusPanel.setMaximumSize(new Dimension(1000, 200));
            statusPanel.add(clearButton, BorderLayout.WEST);
            statusPanel.add(currentStatus, BorderLayout.EAST);
            panel.add(statusPanel);
            add(panel);

            downloadButton.requestFocus();
            downloadButton.addActionListener(this);
            chooseFileButton.addActionListener(this);
            urlField.addKeyListener(this);
            versionField.addKeyListener(this);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(1);
                }
            });

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            setResizable(true);
            setVisible(true);
        }

        @Override
        public void keyTyped(KeyEvent e) {
            if (e.getKeyChar() == '\n')
                actionPerformed(null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == downloadButton) {
                boolean downloading = CMPDL.downloading;
                if (downloading && operatorThread != null) {
                    Interface.finishDownload(true);
                    Interface.setStatus("Stopped Manually");
                } else {
                    String url = urlField.getText();
                    String version = versionField.getText();
                    if (url != null && !url.isEmpty() && !downloading) {
                        operatorThread = new OperatorThread(url, version);
                        setStopButtonVisibility(true);
                    }
                }
            }
            if (e.getSource() == chooseFileButton) {
                int returnVal = fileChooser.showOpenDialog(this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File chosenFile = fileChooser.getSelectedFile();
                    operatorThread = new OperatorThread(chosenFile);
                    setStopButtonVisibility(true);
                }
            }
        }

        public void setStopButtonVisibility(boolean show) {
            chooseFileButton.setVisible(!show);
            orLabel.setVisible(!show);
            downloadButton.setText(show ? "Stop" : "Download");
        }

        @Override
        public void keyPressed(KeyEvent e) {
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }
    }

}
