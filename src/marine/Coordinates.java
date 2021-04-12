package marine;

public class Coordinates {
    public Coordinates(double x, Double y) {
        this.x = x;
        this.y = y;
    }

    private double x;

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

    private Double y; //Поле не может быть null

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }
}