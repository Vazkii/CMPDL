package vazkii.cmpdl;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;

public class Interface {

	private static Frame frame;
	public static Thread operatorThread;

	public static void openInterface() {
		frame = new Frame();
	}

	public static void addLogLine(String s) {
		if(frame != null) {
			String currText = frame.logArea.getText();
			frame.logArea.setText(currText.isEmpty() ? s : (currText + "\n" + s));
		}
	}

	public static void setStatus(String status) {
		if(frame != null)
			frame.currentStatus.setText(status);
	}

	static final class Frame extends JFrame implements ActionListener, KeyListener {

		private static final long serialVersionUID = -2280547253170432552L;

		JPanel panel;
		JButton downloadButton;
		JLabel label;
		JScrollPane scrollPane;
		JTextField urlField;
		JTextArea logArea;
		JLabel currentStatus;

		public Frame() {
			setSize(800, 640);
			setTitle("Vazkii's Curse Modpack Downloader (CMPDL)");

			panel = new JPanel();
			downloadButton = new JButton("Download");
			label = new JLabel("Modpack URL");
			urlField = new JTextField("", 54);

			logArea = new JTextArea(34, 68);
			logArea.setBackground(Color.WHITE);
			logArea.setEditable(false);
			logArea.setLineWrap(true);
			scrollPane = new JScrollPane(logArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			DefaultCaret caret = (DefaultCaret) logArea.getCaret();
			caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

			currentStatus = new JLabel("Idle", SwingConstants.LEFT);

			panel.add(label);
			panel.add(urlField);
			panel.add(downloadButton);
			panel.add(scrollPane);
			panel.add(currentStatus);
			add(panel);

			downloadButton.requestFocus();
			downloadButton.addActionListener(this);
			urlField.addKeyListener(this);

			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					System.exit(1);
				}
			});

			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch(Exception ex) {
				ex.printStackTrace();
			}

			setResizable(false);
			setVisible(true);
		}

		@Override
		public void keyTyped(KeyEvent e) {
			if(e.getKeyChar() == '\n')
				actionPerformed(null);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			String url = urlField.getText();
			if(url != null && !url.isEmpty() && !CMPDL.downloading)
				operatorThread = new OperatorThread(url);
		}

		@Override public void keyPressed(KeyEvent e) {  }
		@Override public void keyReleased(KeyEvent e) { }
	}

}
