package green_green_avk.anotherterm.ui;

import android.graphics.Point;

public class ConsoleScreenSelection {
    public final Point first = new Point();
    public final Point last = new Point();
    public boolean isRectangular = false;

    public ConsoleScreenSelection getDirect() {
        final ConsoleScreenSelection r = new ConsoleScreenSelection();
        if (last.y < first.y || last.y == first.y && last.x < first.x) {
            r.first.set(last.x, last.y);
            r.last.set(first.x, first.y);
        } else {
            r.first.set(first.x, first.y);
            r.last.set(last.x, last.y);
        }
        r.isRectangular = isRectangular;
        return r;
    }
}
