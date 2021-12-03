package uk.ac.ed.inf;

import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A LongLat class represents a point or the position of a drone with longitude and latitude.
 */
public class LongLat {

    // variables
    private double longitude;
    private double latitude;
    private int angle;

    /**
     * Constructs a LongLat object to represent a point with two parameters.
     *
     * @param longitude longitude of the point.
     * @param latitude  latitude of the point.
     */
    public LongLat(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.angle = 0;
    }

    /**
     * Lower bound for the latitude -- Buccleuch St bus stop
     * <br>Upper bound for the latitude -- KFC
     *
     * <br>Lower bound for the longitude -- Forrest hill
     * <br>Upper bound for the longitude -- KFC
     *
     * @return {@code true} if the point within the drone confinement area
     */
    public boolean isConfined() {
        if (this.latitude > Constants.BUCCLEUCH_ST_BUS_STOP_LATITUDE
                && this.latitude < Constants.KFC_LATITUDE
                && this.longitude > Constants.FORREST_HILL_LONGITUDE
                && this.longitude < Constants.KFC_LONGITUDE) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param longLat a LongLat object represents a point
     * @return a double represents the pythagorean distance in degrees between two points
     */
    public double distanceTo(LongLat longLat) {
        double longitudeDifference = this.longitude - longLat.longitude;
        double latitudeDifference = this.latitude - longLat.latitude;
        return Math.sqrt(longitudeDifference * longitudeDifference + latitudeDifference * latitudeDifference);
    }

    /**
     * @param longLat a LongLat object represents a point
     * @return {@code true} if the distance between current point and the input parameter point
     * are less than a tolerant distance.
     */
    public boolean closeTo(LongLat longLat) {
        if (distanceTo(longLat) < Constants.DISTANCE_TOLERANCE) {
            return true;
        }
        return false;
    }

    /**
     * @param angle angle of the next movement of the drone
     * @return a LongLat object represents the next position of the drone
     */
    public LongLat nextPosition(int angle) {
        if (angle == Constants.HOVERING_ANGLE) {
            return this;
        } else if (angle < 0 || angle > 350 || angle % 10 != 0) {
            System.err.println(angle + "is invalid for the angle");
            System.exit(1);
        } else {
            // using Trigonometric function could get the next position easily
            this.longitude += Constants.LENGTH_OF_DRONE_MOVE * Math.cos(Math.toRadians(angle));
            this.latitude += Constants.LENGTH_OF_DRONE_MOVE * Math.sin(Math.toRadians(angle));
        }
        return this;
    }

    public boolean isValidMove(LongLat endPos, Map map) {
        boolean isValid = true;
        boolean crossConfinement = false;
        boolean crossNoFlyZone = false;

        Line2D curr2End = new Line2D.Double(this.latitude, this.longitude, endPos.latitude, endPos.longitude);

        // check if cross confinement area
        ArrayList<Point> confinementArea = map.getConfinementArea();

        for (int i = 0; i < confinementArea.size(); i++) {
            int j = (i + 1) % confinementArea.size();
            Line2D confinementLine = new Line2D.Double(
                    confinementArea.get(i).coordinates().get(1),
                    confinementArea.get(i).coordinates().get(0),
                    confinementArea.get(j).coordinates().get(1),
                    confinementArea.get(j).coordinates().get(0));

            if (curr2End.intersectsLine(confinementLine)) {
                crossConfinement = true;
                break;
            }
        }

        // check if cross no fly zone
        ArrayList<Geometry> noFlyZones = map.getNoFlyZones();
        for (int i = 0; i < noFlyZones.size(); i++) {
            Polygon noFlyZonePoly = (Polygon) noFlyZones.get(i);
            List<Point> polyPoints = noFlyZonePoly.coordinates().get(0);

            for (int j = 0; j < polyPoints.size(); j++) {
                int k = (j + 1) % polyPoints.size();
                Line2D noFlyZoneLine = new Line2D.Double(
                        polyPoints.get(j).coordinates().get(1),
                        polyPoints.get(j).coordinates().get(0),
                        polyPoints.get(k).coordinates().get(1),
                        polyPoints.get(k).coordinates().get(0));

                if (curr2End.intersectsLine(noFlyZoneLine)) {
                    crossNoFlyZone = true;
                    break;
                }
            }
        }

        if (crossConfinement & crossNoFlyZone) {
            isValid = false;
        }

        return isValid;
    }


    public LongLat moveDrone(Map map, LongLat targetPos) {
        int lastMoveAngle = this.angle;
        this.angle = estimateCurrTargetAngle(this, targetPos);
        boolean moveSuccess = false;
        boolean increaseAngle = true;
        LongLat nextPos = null;
        int incrementCount = 1;

        while (!moveSuccess) {
            nextPos = nextPosition(this.angle);
            if (isValidMove(nextPos, map)) {
                moveSuccess = true;
                lastMoveAngle = this.angle;
            } else {
                if (increaseAngle) {
                    this.angle = validAngle(this.angle + 10 * incrementCount);
                    if (Math.abs(this.angle - lastMoveAngle) == 180) {
                        this.angle = validAngle(this.angle - 10);
                    }
                    increaseAngle = false;
                } else {
                    this.angle = validAngle(this.angle - 10 * incrementCount);
                    if (Math.abs(this.angle - lastMoveAngle) == 180) {
                        this.angle = validAngle(this.angle + 10);
                    }
                    increaseAngle = true;
                }
            }
            incrementCount++;
        }

        return nextPos;

    }

    public int estimateCurrTargetAngle(LongLat currentPos, LongLat targetPos) {
        double lngDifference = targetPos.getLongitude() - currentPos.getLongitude();
        double latDifference = targetPos.getLatitude() - currentPos.getLatitude();

        int angle = (int) Math.toDegrees(Math.atan2(latDifference, lngDifference));

        // angle only between 0 - 350
        if (angle < 0) {
            angle += 360;
        }

        angle = Math.round(angle / 10) * 10;
        return angle;
    }

    public int validAngle(int angle) {
        if (angle < 0) {
            angle += 360;
        } else if (angle >= 360) {
            angle -= 360;
        }
        return angle;
    }


    public static LongLat convertW3wCoordinateToLongLat(W3wCoordinate w3wCoordinate) {
        return new LongLat(w3wCoordinate.coordinates.lng, w3wCoordinate.coordinates.lat);
    }


    // getter and setter
    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public int getAngle() {
        return angle;
    }

    public void setAngle(int angle) {
        this.angle = angle;
    }

    public Point getPoint() {
        return Point.fromLngLat(longitude, latitude);
    }
}