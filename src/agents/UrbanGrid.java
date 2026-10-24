package agents;

/**
 * MEMBRE B - Classe utilitaire : Grille Urbaine 20x20
 * Calcul de distance Manhattan et déplacement pas à pas
 */
public class UrbanGrid {

    public static final int WIDTH  = 20;
    public static final int HEIGHT = 20;

    /** Distance Euclidienne entre deux points de la grille */
    public static double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    /** Distance Manhattan (plus utile pour les déplacements grille) */
    public static int distanceManhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x2 - x1) + Math.abs(y2 - y1);
    }

    /** Un pas vers la destination — retourne {newX, newY} */
    public static int[] moveToward(int currentX, int currentY, int targetX, int targetY) {
        int newX = currentX;
        int newY = currentY;

        if (currentX < targetX) newX++;
        else if (currentX > targetX) newX--;

        if (currentY < targetY) newY++;
        else if (currentY > targetY) newY--;

        // Rester dans les limites
        newX = Math.max(0, Math.min(WIDTH  - 1, newX));
        newY = Math.max(0, Math.min(HEIGHT - 1, newY));

        return new int[]{newX, newY};
    }

    /** Vérifie si les coordonnées sont dans la grille */
    public static boolean isValid(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    /** Position aléatoire sur la grille */
    public static int[] randomPosition() {
        return new int[]{
            (int)(Math.random() * WIDTH),
            (int)(Math.random() * HEIGHT)
        };
    }
    
 // Méthode de rendu pour la démo
    public static void dessinerVille(int ax, int ay, int px, int py, int fx, int fy, int mx, int my) {
        StringBuilder map = new StringBuilder();
        map.append("\n--- CARTE URBAINE (20x20) ---\n");
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (x == ax && y == ay) map.append(" A ");      // Ambulance
                else if (x == px && y == py) map.append(" P "); // Pompier
                else if (x == fx && y == fy) map.append("🔥 "); // Incident FIRE
                else if (x == mx && y == my) map.append("🏥 "); // Incident MEDICAL
                else map.append(" . ");
            }
            map.append("\n");
        }
        System.out.println(map.toString());
    }

    public static int distanceManhattan1(int x1, int y1, int x2, int y2) {
        return Math.abs(x2 - x1) + Math.abs(y2 - y1);
    }

    public static int[] moveToward1(int cx, int cy, int tx, int ty) {
        int nx = cx, ny = cy;
        if (cx < tx) nx++; else if (cx > tx) nx--;
        if (cy < ty) ny++; else if (cy > ty) ny--;
        return new int[]{nx, ny};
    }
    
}
