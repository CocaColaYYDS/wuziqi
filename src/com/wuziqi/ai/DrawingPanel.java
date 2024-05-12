package com.wuziqi.ai;


/**
 *  �������ҵ���߼�
*/

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.Exception;
import java.lang.Math;
import java.lang.SecurityException;
import java.lang.String;
import java.lang.System;
import java.lang.Thread;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.MouseInputListener;
import javax.swing.filechooser.FileFilter;

public final class DrawingPanel extends FileFilter
    implements ActionListener, MouseMotionListener,  WindowListener {   
    // ����
    public static final String HEADLESS_PROPERTY   = "my.headless";
    public static final String MULTIPLE_PROPERTY   =  "my.multiple";
    public static final String SAVE_PROPERTY       = "my.save";
    private static final String TITLE              = "������";
    private static final Color GRID_LINE_COLOR     = new Color(64, 64, 64, 128);
    private static final int GRID_SIZE             = 10;      // 10px ������
    private static final int DELAY                 = 100;     // ��ʱdelay between repaints in millis
    private static final int MAX_SIZE              = 10000;   // max width/height
    private static final boolean DEBUG             = true; 	  // DeBug ����
    private static final boolean SAVE_SCALED_IMAGES = true;   // true panel�Ŵ���Сʱ, �����Ŵ�״̬�µ�ͼƬ
    private static int instances = 0;
    private static Thread shutdownThread = null;
    

    private static boolean hasProperty(String name) {
        try {
            return System.getProperty(name) != null;
        } catch (SecurityException e) {
        	// ��ֵ�쳣
            if (DEBUG) System.out.println("Security exception when trying to read " + name);
            return false;
        }
    }
    
    // �������߳��Ƿ������� main is active
    private static boolean mainIsActive() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        int activeCount = group.activeCount();
        
        // ���߳�����Ѱ�����߳�
        Thread[] threads = new Thread[activeCount];
        group.enumerate(threads);
        for (int i = 0; i < threads.length; i++) {
            Thread thread = threads[i];
            String name = ("" + thread.getName()).toLowerCase();
            if (name.indexOf("main") >= 0 || 
                name.indexOf("testrunner-assignmentrunner") >= 0) {
                // �ҵ����߳�
                // (TestRunnerApplet's main runner also counts as "main" thread)
                return thread.isAlive();
            }
        }
        
        // û���ҵ����߳�
        return false;
    }
    
    // �Զ���һ��ImagePanel
    private class ImagePanel extends JPanel {
        private static final long serialVersionUID = 0;
        private Image image;
        
        public ImagePanel(Image image) {
            setImage(image);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(image.getWidth(this), image.getHeight(this)));
            setAlignmentX(0.0f);
        }
        
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            if (currentZoom != 1) {
                g2.scale(currentZoom, currentZoom);
            }
            g2.drawImage(image, 0, 0, this);
            
            // Ϊ�˵��Է�������������
            if (gridLines) {
                g2.setPaint(GRID_LINE_COLOR);
                for (int row = 1; row <= getHeight() / GRID_SIZE; row++) {
                    g2.drawLine(0, row * GRID_SIZE, getWidth(), row * GRID_SIZE);
                }
                for (int col = 1; col <= getWidth() / GRID_SIZE; col++) {
                    g2.drawLine(col * GRID_SIZE, 0, col * GRID_SIZE, getHeight());
                }
            }
        }
        
        public void setImage(Image image) {
            this.image = image;
            repaint();
        }
    }

    // �ؼ�
    private int width, height;             // ���� frame �Ĵ�С
    private JFrame frame;                  // �ܴ��ڵ� frame
    private JPanel panel;                  // �ܵĻ������
    private ImagePanel imagePanel;         // �����Ļ滭���
    private BufferedImage image;           // ��¼��ͼ�����
    private Graphics2D g2;                 // 2D��ͼ graphics context
    private JLabel statusBar;              // ״̬����ʾ����ƶ���λ��
    private JFileChooser chooser;          // ����ѡ�� file chooser
    private Timer timer;                   // ���ƵĶ���ʱ��
    private Color backgroundColor = Color.WHITE;
    private boolean PRETTY = true;         // ������ݲ���true to anti-alias
    private boolean gridLines = false;		//�Ƿ�������
    private int currentZoom = 1;
    private int initialPixel;              // ��ʼ��ÿ�����ص�
    
    // ����width��height����һ��panel
    public DrawingPanel(int width, int height) {
        if (width < 0 || width > MAX_SIZE || height < 0 || height > MAX_SIZE) {
            throw new IllegalArgumentException("Illegal width/height: " + width + " x " + height);
        }
        //synchronized��֤��ͬһʱ�����ֻ��һ���߳�ִ�иöδ���       
        synchronized (getClass()) {
            instances++;
            if (shutdownThread == null) {
                shutdownThread = new Thread(new Runnable() {
                    public void run() {
                        try {
                            while (true) {
                                //���ִ�����߳��Ѿ��ҵ�
                                if ((instances == 0 || shouldSave()) && !mainIsActive()) {
                                    try {
                                        System.exit(0);
                                    } catch (SecurityException sex) {}
                                }

                                Thread.sleep(250);
                            }
                        } catch (Exception e) {}
                    }
                });
                shutdownThread.setPriority(Thread.MIN_PRIORITY);
                shutdownThread.start();
            }
        }
        this.width = width;
        this.height = height;
        
        if (DEBUG) System.out.println("w=" + width + ",h=" + height +  ",graph=" + isGraphical() + ",save=" + shouldSave());
        
        if (shouldSave()) {
            // ͼ���ܳ���256����ɫ
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
            PRETTY = false;   // �رտ���ݣ���ʡ��ɫ����ɫ
            
            // �ó�ʼ���ı���ɫ���frame����Ϊ������͸����ʾARGBͼ��
            Graphics g = image.getGraphics();
            g.setColor(backgroundColor);
            // ����1����ֹwidth��heightΪ0
            g.fillRect(0, 0, width + 1, height + 1);
        } else {
        	//ARGB
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        initialPixel = image.getRGB(0, 0);        
        g2 = (Graphics2D) image.getGraphics();
        g2.setColor(Color.BLACK);
        if (PRETTY) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
            
        if (isGraphical()) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {}
            
            statusBar = new JLabel(" ");
            statusBar.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            
            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            panel.setBackground(backgroundColor);
            panel.setPreferredSize(new Dimension(width, height));
            imagePanel = new ImagePanel(image);
            imagePanel.setBackground(backgroundColor);
            panel.add(imagePanel);
            
            // ��������¼�
            panel.addMouseMotionListener(this);
            
            // �����洰��
            frame = new JFrame(TITLE);
            frame.addWindowListener(this);
            JScrollPane center = new JScrollPane(panel);
            frame.getContentPane().add(center);
            frame.getContentPane().add(statusBar, "South");
            frame.setBackground(Color.WHITE);

            // �˵���
            setupMenuBar();
            
            frame.pack();
            center(frame);
            frame.setVisible(true);
            if (!shouldSave()) {
                toFront(frame);
            }        
            // �ػ�update
            timer = new Timer(DELAY, this);
            timer.start();
        }
    }
    
    // �ļ������ʽ����Ϊpng��gif
    public boolean accept(File file) {
        return file.isDirectory() ||
            (file.getName().toLowerCase().endsWith(".png") || 
             file.getName().toLowerCase().endsWith(".gif"));
    }
    
    //��ʼ��UI���
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof Timer) {
            // �ػ�
            panel.repaint();
        } else if (e.getActionCommand().equals("�˳�")) {
            exit();
        } else if (e.getActionCommand().equals("������ͼ")) {
            saveAs();
        } else if (e.getActionCommand().equals("�Ŵ�")) {
            zoom(currentZoom + 1);
        } else if (e.getActionCommand().equals("��С")) {
            zoom(currentZoom - 1);
        } else if (e.getActionCommand().equals("������С (100%)")) {
            zoom(1);
        } else if (e.getActionCommand().equals("����������")) {
            setGridLines(((JCheckBoxMenuItem) e.getSource()).isSelected());
        } else if (e.getActionCommand().equals("����")) {
            JOptionPane.showMessageDialog(frame,
                    "������\n" + 
                    "�����漰��\n" +
                    "Alpha-Beta��֦�㷨\n" +
                    "������\n" +
                    "Swingҵ���߼�ʵ��\n" +
                    "\n"+"--��լ��http��//imzhai.com)",
                   
                    "����\n",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    public void addKeyListener(KeyListener listener) {
        frame.addKeyListener(listener);
    }
    
    public void addMouseListener(MouseListener listener) {
        panel.addMouseListener(listener);
    }
    
    public void addMouseListener(MouseMotionListener listener) {
        panel.addMouseMotionListener(listener);
    }
    
    public void addMouseMotionListener(MouseMotionListener listener) {
        panel.addMouseMotionListener(listener);
    }
    
    public void addMouseListener(MouseInputListener listener) {
        panel.addMouseListener(listener);
        panel.addMouseMotionListener(listener);
    }
    
    // ������е���/��ɫ
    public void clear() {
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = initialPixel;
        }
        image.setRGB(0, 0, width, height, pixels, 0, 1);
    }
  
    // �ļ���ʽpng����gif
    public String getDescription() {
        return "Image files (*.png; *.gif)";
    }
    
    // ���Graphics2D����
    public Graphics2D getGraphics() {
        return g2;
    }
    
    // ����Height
    public int getHeight() {
        return height;
    }
     
    // ��Dimension���󷵻�width��height
    public Dimension getSize() {
        return new Dimension(width, height);
    }
    
    // ����Width
    public int getWidth() {
        return width;
    }
    
    // ����Ŀǰ�����ű���
    public int getZoom() {
        return currentZoom;
    }
    
    // ���������Ϊ����������ʾ��statusbar��
    public void mouseDragged(MouseEvent e) {}
    public void mouseMoved(MouseEvent e) {
        int x = e.getX() / currentZoom;
        int y = e.getY() / currentZoom;
        setStatusBarText("(" + x + ", " + y + ")");
    }
    
   
    // �����ļ�image
    public void save(String filename) throws IOException {
        BufferedImage image2 = getImage();
        
        // ��������ˣ��ָ��ٱ���
        if (SAVE_SCALED_IMAGES && currentZoom != 1) {
            BufferedImage zoomedImage = new BufferedImage(width * currentZoom, height * currentZoom, image.getType());
            Graphics2D g = (Graphics2D) zoomedImage.getGraphics();
            g.setColor(Color.BLACK);
            if (PRETTY) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g.scale(currentZoom, currentZoom);
            g.drawImage(image2, 0, 0, imagePanel);
            image2 = zoomedImage;
        }
        int lastDot = filename.lastIndexOf(".");
        String extension = filename.substring(lastDot + 1);
        ImageIO.write(image2, extension, new File(filename));
    }  
    // ���ñ�����ɫ
    public void setBackground(Color c) {
        backgroundColor = c;
        if (isGraphical()) {
            panel.setBackground(c);
            imagePanel.setBackground(c);
        }
    }
    
    // ͼ��Ķ��������ߵĻ�ͼ����
    // ʹ�õ��Գߴ������
    public void setGridLines(boolean gridLines) {
        this.gridLines = gridLines;
        imagePanel.repaint();
    }
    
    // ͨ������ֵheight ��������ٴε���getGraphics()�����»�ȡ�����������л�ͼ
    public void setHeight(int height) {
        setSize(getWidth(), height);
    }
     
    public void setSize(int width, int height) {
        // �滻��ͼ��BufferedImage
        BufferedImage newImage = new BufferedImage(width, height, image.getType());
        imagePanel.setImage(newImage);
        newImage.getGraphics().drawImage(image, 0, 0, imagePanel);
        this.width = width;
        this.height = height;
        image = newImage;
        g2 = (Graphics2D) newImage.getGraphics();
        g2.setColor(Color.BLACK);
        if (PRETTY) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        zoom(currentZoom);
        if (isGraphical()) {
            frame.pack();
        }
    }
    
    // frame�ɼ����ɼ�
    public void setVisible(boolean visible) {
        if (isGraphical()) {
            frame.setVisible(visible);
        }
    }
    
    // ���ô�����ǰ��ǿ��֢��=-=..
    public void toFront() {
        toFront(frame);
    }
    
    // �رգ��˳�
    public void windowClosing(WindowEvent event) {
        frame.setVisible(false);
        synchronized (getClass()) {
            instances--;
        }
        frame.dispose();
    }
    
    // ʵ��WindowListener����ķ�������Щ����Ŀǰδʹ�ã�
    public void windowActivated(WindowEvent event) {}
    public void windowClosed(WindowEvent event) {}
    public void windowDeactivated(WindowEvent event) {}
    public void windowDeiconified(WindowEvent event) {}
    public void windowIconified(WindowEvent event) {}
    public void windowOpened(WindowEvent event) {}

    // ����factor���зŴ���С
    // factor >= 1
    public void zoom(int zoomFactor) {
        currentZoom = Math.max(1, zoomFactor);
        if (isGraphical()) {
            Dimension size = new Dimension(width * currentZoom, height * currentZoom);
            imagePanel.setPreferredSize(size);
            panel.setPreferredSize(size);
            imagePanel.validate();
            imagePanel.revalidate();
            panel.validate();
            panel.revalidate();
            frame.getContentPane().validate();
            imagePanel.repaint();
            setStatusBarText(" ");
            // resize
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            if (size.width <= screen.width || size.height <= screen.height) {
                frame.pack();
            }
        }
    }
    
    // �������ڷŵ���Ļ�м�
    private void center(Window frame) {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screen = tk.getScreenSize();
        
        int x = Math.max(0, (screen.width - frame.getWidth()) / 2);
        int y = Math.max(0, (screen.height - frame.getHeight()) / 2);
        frame.setLocation(x, y);
    }   
    // ����б�Ҫ�����첢��ʼ��JFileChooser����
    private void checkChooser() {
        if (chooser == null) {
            // TODO: fix security on applet mode
            chooser = new JFileChooser(System.getProperty("user.dir"));
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(this);
        }
    }
        
    // �˳�����
    private void exit() {
        if (isGraphical()) {
            frame.setVisible(false);
            frame.dispose();
        }
        try {
            System.exit(0);
        } catch (SecurityException e) {
        }
    }
  
    //��ȡimage
    private BufferedImage getImage() {
        BufferedImage image2;    
            image2 = new BufferedImage(width, height, image.getType());
        Graphics g = image2.getGraphics();
        if (DEBUG) System.out.println("getImage setting background to " + backgroundColor);
        g.setColor(backgroundColor);
        g.fillRect(0, 0, width, height);
        g.drawImage(image, 0, 0, panel);
        return image2;
    }
    
    private boolean isGraphical() {
        return !hasProperty(SAVE_PROPERTY) && !hasProperty(HEADLESS_PROPERTY);
    }
    
    // �������ͼƬ��ʱ�����
    private void saveAs() {
        String filename = saveAsHelper("png");
        if (filename != null) {
            try {
                save(filename);  // ����
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to save image:\n" + ex);
            }
        }
    }
    
    private String saveAsHelper(String extension) {
        // ʹ���ļ�ѡ��Ի��򣬻���ļ����ͱ����ʽ
        checkChooser();
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return null;
        }     
        File selectedFile = chooser.getSelectedFile();
        String filename = selectedFile.toString();
        if (!filename.toLowerCase().endsWith(extension)) {
            // =-=..Ѿ�Ĳ���.�����У������컹��Ϊ��bug�ˣ�windows̫ɵ���ˣ�����
            filename += "." + extension;
        }

        // ����У��Ƿ񸲸�
        if (new File(filename).exists() && JOptionPane.showConfirmDialog(
                frame, "�ļ�����.  �Ƿ�Overwrite?", "Overwrite?",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }

        return filename;
    }
    
    // �ײ���״̬����ʾ�Ŵ���
    private void setStatusBarText(String text) {
        if (currentZoom != 1) {
            text += " (current zoom: " + currentZoom + "x" + ")";
        }
        statusBar.setText(text);
    }
    
    // ��ʼ��UI�ؼ�
    private void setupMenuBar() {
        boolean secure = (System.getSecurityManager() != null);
        
        JMenuItem saveAs = new JMenuItem("������ͼ", 'A');
        saveAs.addActionListener(this);
        saveAs.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        saveAs.setEnabled(!secure);
        
        
        JMenuItem zoomIn = new JMenuItem("�Ŵ�", 'I');
        zoomIn.addActionListener(this);
        zoomIn.setAccelerator(KeyStroke.getKeyStroke("ctrl EQUALS"));
        
        JMenuItem zoomOut = new JMenuItem("��С", 'O');
        zoomOut.addActionListener(this);
        zoomOut.setAccelerator(KeyStroke.getKeyStroke("ctrl MINUS"));
        
        JMenuItem zoomNormal = new JMenuItem("������С (100%)", 'N');
        zoomNormal.addActionListener(this);
        zoomNormal.setAccelerator(KeyStroke.getKeyStroke("ctrl 0"));
        
        JMenuItem gridLinesItem = new JCheckBoxMenuItem("����������");
        gridLinesItem.setMnemonic('G');
        gridLinesItem.addActionListener(this);
        gridLinesItem.setAccelerator(KeyStroke.getKeyStroke("ctrl G"));
        
        JMenuItem exit = new JMenuItem("�˳�", 'x');
        exit.addActionListener(this);
        
        JMenuItem about = new JMenuItem("����", 'A');
        about.addActionListener(this);
        
        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.addSeparator();
        file.add(saveAs);
        file.addSeparator();
        file.add(exit);
        
        JMenu view = new JMenu("View");
        view.setMnemonic('V');
        view.add(zoomIn);
        view.add(zoomOut);
        view.add(zoomNormal);
        view.addSeparator();
        view.add(gridLinesItem);
        
        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        help.add(about);
        
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(view);
        bar.add(help);
        frame.setJMenuBar(bar);
    }  
    private boolean shouldSave() {
        return hasProperty(SAVE_PROPERTY);
    }
    
    // ���ڷŵ���ǰ���ö���
    private void toFront(final Window window) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                if (window != null) {
                    window.toFront();
                    window.repaint();
                }
            }
        });
    }

}

