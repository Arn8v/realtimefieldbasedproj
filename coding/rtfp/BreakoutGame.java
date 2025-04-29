import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class BreakoutGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Easy", "Medium", "Hard", "Extreme"};
            int choice = JOptionPane.showOptionDialog(
                null, "Select Difficulty", "Breakout Game",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]
            );
            if (choice < 0) System.exit(0);
            int ballSpeed, paddleWidth;
            switch (choice) {
                case 0: ballSpeed = 3; paddleWidth = 120; break;
                case 1: ballSpeed = 5; paddleWidth = 100; break;
                case 2: ballSpeed = 7; paddleWidth = 80; break;
                default: ballSpeed = 9; paddleWidth = 60; break;
            }
            JFrame frame = new JFrame("Breakout Ball - " + options[choice]);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            GamePanel panel = new GamePanel(ballSpeed, paddleWidth, 3);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.startGame();
        });
    }
}

class GamePanel extends JPanel implements Runnable, KeyListener {
    public static final int WIDTH = 700, HEIGHT = 600;
    private Thread thread;
    private boolean running = false;
    private Paddle paddle;
    private java.util.List<Ball> balls;
    private java.util.List<Brick> bricks;
    private java.util.List<PowerUp> powerUps;
    private java.util.List<Particle> particles;
    private int score = 0;
    private int lives;
    private final int initialBallSpeed;
    private final int normalPaddleWidth;
    private int enlargeTimer = -1;
    private boolean doublePts = false, multiBall = false;
    private int doublePtsTimer = 0, multiBallTimer = 0;
    private String powerMsg = "";
    private int powerMsgTimer = 0;

    public GamePanel(int ballSpeed, int paddleWidth, int lives) {
        this.lives = lives;
        this.initialBallSpeed = ballSpeed;
        this.normalPaddleWidth = paddleWidth;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        init(ballSpeed, paddleWidth);
    }

    private void init(int ballSpeed, int paddleWidth) {
        paddle = new Paddle((WIDTH - paddleWidth) / 2, HEIGHT - 50, paddleWidth, 10, 7);
        balls = new ArrayList<>();
        balls.add(new Ball(WIDTH / 2, HEIGHT / 2, 10, initialBallSpeed));
        bricks = new ArrayList<>();
        powerUps = new ArrayList<>();
        particles = new ArrayList<>();
        initBricks();
    }

    private void initBricks() {
        Random rand = new Random();
        Color gold = Brick.GOLD;
        Color pcolor = Brick.POWER_COLOR;
        Color[] basic = {Color.RED, Color.GREEN, Color.YELLOW, Color.ORANGE, Color.CYAN};
        int rows = 6, cols = 12;
        int bw = WIDTH / cols, bh = 20;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int pick = rand.nextInt(100);
                Color col = pick < 5 ? gold : pick < 10 ? pcolor : basic[rand.nextInt(basic.length)];
                bricks.add(new Brick(c * bw, 50 + r * bh, bw - 2, bh - 2, col));
            }
        }
    }

    public void startGame() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    @Override public void run() {
        long last = System.nanoTime();
        double fps = 60, ns = 1e9 / fps, delta = 0;
        while (running) {
            long now = System.nanoTime();
            delta += (now - last) / ns;
            last = now;
            if (delta >= 1) {
                update();
                repaint();
                delta--;
            }
        }
    }

    private void update() {
        paddle.move();
        Iterator<Ball> it = balls.iterator();
        while (it.hasNext()) {
            Ball b = it.next();
            b.move();
            if (b.y > HEIGHT) it.remove(); else { b.checkWallCollision(WIDTH, HEIGHT); b.checkPaddleCollision(paddle); }
        }
        if (balls.isEmpty()) {
            lives--;
            if (lives > 0) resetRound(); else gameOver();
            return;
        }
        if (enlargeTimer > 0) enlargeTimer--; else if (enlargeTimer == 0) { paddle.width = normalPaddleWidth; enlargeTimer = -1; }
        if (doublePtsTimer > 0) doublePtsTimer--; else doublePts = false;
        if (multiBallTimer > 0) multiBallTimer--; else if (multiBall) { multiBall = false; resetRound(); }
        if (powerMsgTimer > 0) powerMsgTimer--;

        Iterator<Brick> brIt = bricks.iterator();
        while (brIt.hasNext()) {
            Brick br = brIt.next();
            for (Ball b : balls) {
                if (!br.destroyed && b.getRect().intersects(br.getRect())) {
                    Rectangle r1 = b.getRect(), r2 = br.getRect();
                    Rectangle inter = r1.intersection(r2);
                    if (inter.width < inter.height) b.dx = -b.dx; else b.dy = -b.dy;
                    b.x += b.dx; b.y += b.dy;
                    spawnParticles(br);
                    score += doublePts ? 20 : 10;
                    if (br.color.equals(Brick.GOLD) || br.color.equals(Brick.POWER_COLOR))
                        powerUps.add(new PowerUp(br.x + br.width / 2 - 10, br.y, 20, 20));
                    br.destroyed = true;
                    break;
                }
            }
        }

        boolean allCleared = true;
        for (Brick br : bricks) if (!br.destroyed) { allCleared = false; break; }
        if (allCleared) {
            running = false;
            JFrame top = (JFrame) SwingUtilities.getWindowAncestor(this);
            int choice = JOptionPane.showOptionDialog(this, "🎉 You cleared the level! Score: " + score,
                "Level Cleared", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, new String[]{"Try Again", "Quit"}, "Try Again");
            top.dispose();
            if (choice == JOptionPane.YES_OPTION) BreakoutGame.main(new String[0]); else System.exit(0);
            return;
        }

        Iterator<PowerUp> puIt = powerUps.iterator();
        while (puIt.hasNext()) {
            PowerUp pu = puIt.next(); pu.move();
            if (pu.y > HEIGHT) puIt.remove();
            else if (pu.getRect().intersects(paddle.getRect())) { applyPower(pu); puIt.remove(); }
        }
        Iterator<Particle> pit = particles.iterator(); while (pit.hasNext()) { Particle p = pit.next(); p.update(); if (!p.isAlive()) pit.remove(); }
    }

    private void resetRound() {
        balls.clear();
        balls.add(new Ball(WIDTH / 2, HEIGHT / 2, 10, initialBallSpeed));
        paddle.width = normalPaddleWidth;
        paddle.x = (WIDTH - paddle.width) / 2;
        paddle.resetPosition();
        enlargeTimer = -1;
    }

    private void spawnParticles(Brick br) {
        Random rand = new Random();
        for (int i = 0; i < 20; i++) particles.add(new Particle(
            br.x + rand.nextFloat() * br.width,
            br.y + rand.nextFloat() * br.height,
            br.color));
    }

    private void applyPower(PowerUp pu) {
        Random rand = new Random(); int type = rand.nextInt(4), dur = 20 * 60;
        switch (type) {
            case 0:
                paddle.width += 30;
                enlargeTimer = dur;
                powerMsg = "Paddle Size Up!";
                break;
            case 1:
                for (Ball b : balls) { b.speed = initialBallSpeed + 2; b.updateVelocity(); }
                powerMsg = "Speed Up!";
                break;
            case 2:
                doublePts = true;
                doublePtsTimer = dur;
                powerMsg = "Double Points!";
                break;
            case 3:
                if (!multiBall) {
                    multiBall = true;
                    multiBallTimer = dur;
                    Ball b0 = balls.get(0);
                    balls.add(new Ball(b0.x, b0.y, b0.radius, b0.speed));
                }
                powerMsg = "Multi-Ball!";
                break;
        }
        powerMsgTimer = 120;
    }

    private void gameOver() {
        running = false;
        JFrame top = (JFrame) SwingUtilities.getWindowAncestor(this);
        int choice = JOptionPane.showOptionDialog(this, "Game Over! Score: " + score,
            "Game Over", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
            null, new String[]{"Try Again", "Quit"}, "Try Again");
        top.dispose();
        if (choice == JOptionPane.YES_OPTION) BreakoutGame.main(new String[0]); else System.exit(0);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.BLACK); g.fillRect(0, 0, WIDTH, HEIGHT);
        for (Ball b : balls) b.draw(g);
        paddle.draw(g);
        for (Brick br : bricks) if (!br.destroyed) br.draw(g);
        for (PowerUp pu : powerUps) pu.draw(g);
        for (Particle p : particles) p.draw(g);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("Score: " + score, 10, 20);
        g.drawString("Lives: " + lives, WIDTH - 100, 20);
        if (enlargeTimer > 0) g.drawString("Paddle: " + (enlargeTimer/60) + "s", 10, 40);
        if (doublePtsTimer > 0) g.drawString("Double: " + (doublePtsTimer/60) + "s", 10, 60);
        if (multiBallTimer > 0) g.drawString("Multi: " + (multiBallTimer/60) + "s", 10, 80);
        if (powerMsgTimer > 0) { g.setFont(new Font("Arial", Font.PLAIN, 18)); g.drawString(powerMsg, WIDTH/2 - 80, 30); }
    }

    @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_LEFT) paddle.setDir(-1); if (e.getKeyCode() == KeyEvent.VK_RIGHT) paddle.setDir(1); }
    @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT) paddle.setDir(0); }
    @Override public void keyTyped(KeyEvent e) {}
}

class Ball {
    int x, y, radius, speed, dx = 1, dy = -1;
    public Ball(int x, int y, int r, int s) { this.x = x; this.y = y; this.radius = r; this.speed = s; updateVelocity(); }
    public void updateVelocity() { dx = speed * (dx < 0 ? -1 : 1); dy = speed * (dy < 0 ? -1 : 1); }
    public void move() { x += dx; y += dy; }
    public void checkWallCollision(int w, int h) { if (x < 0 || x + radius*2 > w) dx = -dx; if (y < 0) dy = -dy; }
    public void checkPaddleCollision(Paddle p) { if (getRect().intersects(p.getRect())) { dy = -dy; y += dy; int pc = p.x + p.width/2, bc = x + radius; dx = speed * ((bc < pc) ? -1 : 1); } }
    public void reverseY() { dy = -dy; }
    public Rectangle getRect() { return new Rectangle(x, y, radius*2, radius*2); }
    public void draw(Graphics g) { g.setColor(Color.WHITE); g.fillOval(x, y, radius*2, radius*2); }
}

class Paddle {
    int x, y, width, height, speed, dir = 0;
    public Paddle(int x, int y, int w, int h, int s) { this.x = x; this.y = y; this.width = w; this.height = h; this.speed = s; }
    public void setDir(int d) { dir = d; }
    public void move() { x += dir*speed; if (x < 0) x = 0; if (x + width > GamePanel.WIDTH) x = GamePanel.WIDTH - width; }
    public void resetPosition() { x = (GamePanel.WIDTH - width) / 2; }
    public Rectangle getRect() { return new Rectangle(x, y, width, height); }
    public void draw(Graphics g) { g.setColor(Color.WHITE); g.fillRect(x, y, width, height); }
}

class Brick {
    static final Color GOLD = new Color(212,175,55);
    static final Color POWER_COLOR = Color.MAGENTA;
    int x, y, width, height; Color color; boolean destroyed = false;
    public Brick(int x, int y, int w, int h, Color c) { this.x = x; this.y = y; this.width = w; this.height = h; this.color = c; }
    public Rectangle getRect() { return new Rectangle(x, y, width, height); }
    public void draw(Graphics g) {
        if (color.equals(GOLD) || color.equals(POWER_COLOR)) {
            g.setColor(color); g.fillRect(x, y, width, height);
            g.setColor(Color.WHITE); g.drawRect(x, y, width, height);
            g.drawRect(x+2, y+2, width-4, height-4);
        } else {
            g.setColor(color); g.fillRect(x, y, width, height);
        }
    }
}

class PowerUp {
    int x, y, width, height, dy = 2;
    public PowerUp(int x, int y, int w, int h) { this.x = x; this.y = y; this.width = w; this.height = h; }
    public void move() { y += dy; }
    public Rectangle getRect() { return new Rectangle(x, y, width, height); }
    public void draw(Graphics g) { g.setColor(Color.MAGENTA); g.fillOval(x, y, width, height); }
}

class Particle {
    float x, y, dx, dy; int life;
    Color color;
    public Particle(float x, float y, Color c) { this.x = x; this.y = y; this.color = c; Random r = new Random(); dx = (r.nextFloat()*2-1)*3; dy = (r.nextFloat()*2-1)*3; life = 30; }
    public void update() { x += dx; y += dy; life--; }
    public boolean isAlive() { return life > 0; }
    public void draw(Graphics g) { g.setColor(color); g.fillRect((int)x, (int)y, 4, 4); }
}
