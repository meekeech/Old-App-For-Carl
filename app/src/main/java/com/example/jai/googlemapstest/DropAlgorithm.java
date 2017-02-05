package com.example.jai.googlemapstest;


public class DropAlgorithm {
    private final double approach_dist = 2000;
    private final double approach_angle = 30;
    private final double approach_turn_limit = 5;
    private final double approach_threshold = 10;
    private final double g = 9.81;
    private final double period = 0.0001;
    private final double latency = 1.7;

    private double target_lat, target_lon, target_z;
    private double K1, K2;

    private double pos_x, pos_y, pos_z;                  // Position of the aircraft
    private double vel_x, vel_y, vel_z;                  // Speed of probe over ground
    private double speed, heading;

    private double drag_x, drag_y, drag_z;
    private double wind_x, wind_y;

    double drop_dist;
    double drop_heading;
    double drop_time;
    double fall_time;
    boolean onApproach;
    boolean withinTurnLimit;

    public void setPosition(double lat, double lon, double z) {
        pos_y = K1 * (lat - target_lat);
        pos_x = K2 * (lon - target_lon);
        pos_z = z*0.3048 - target_z;
    }

    public void setTarget(double lat, double lon, double z) {
        target_lat = lat;
        target_lon = lon;
        target_z = z*0.3048;

        lat = Math.toRadians(lat);
        K1 = 111132.09 - 566.05 * Math.cos(2 * lat) + 1.20 * Math.cos(4 * lat);
        K2 = 111415.13 * Math.cos(lat) - 94.55 * Math.cos(3 * lat) + 0.12 * Math.cos(5 * lat);
    }

    public void setSpeed(double s, double t, double z) {
        speed = s/3.6;
        heading = Math.toRadians(t);
        vel_x = s * Math.sin(heading);
        vel_y = s * Math.cos(heading);
        vel_z = z*0.3048;
    }

    public void setDrag(double x, double y, double z) {
        drag_x = x;
        drag_y = y;
        drag_z = z;
    }

    public void setWind(double s, double t) {
        t = Math.toRadians(t);
        wind_x = s * Math.sin(t);
        wind_y = s * Math.cos(t);
    }

    public double[] simulateDrop() {
        fall_time = 0;

        // p stands for payload
        double p_pos_x = 0, p_pos_y = 0, p_pos_z = pos_z;
        double p_vel_x = vel_x, p_vel_y = vel_y, p_vel_z = vel_z;
        double p_acc_x, p_acc_y, p_acc_z;

        while (p_pos_z > 0) {
            p_acc_x = wind_x - p_vel_x;
            p_acc_x = drag_x * p_acc_x * Math.abs(p_acc_x);
            p_acc_y = wind_y - p_vel_y;
            p_acc_y = drag_y * p_acc_y * Math.abs(p_acc_y);
            p_acc_z = drag_z * p_vel_z * p_vel_z - g;

            p_vel_x += p_acc_x * period;
            p_vel_y += p_acc_y * period;
            p_vel_z += p_acc_z * period;

            p_pos_x += p_vel_x * period + p_acc_x * period * period / 2.0;
            p_pos_y += p_vel_y * period + p_acc_y * period * period / 2.0;
            p_pos_z += p_vel_z * period + p_acc_z * period * period / 2.0;

            fall_time += period;
        }

        p_pos_x += pos_x;
        p_pos_y += pos_y;

        drop_dist =  Math.sqrt(p_pos_x*p_pos_x + p_pos_y*p_pos_y);
        drop_heading = Math.toDegrees(Math.atan2(p_pos_y, p_pos_x));
        drop_time = drop_dist / (speed*Math.cos(Math.toRadians(drop_heading))) - latency;
        onApproach = drop_dist < approach_threshold || (drop_dist < approach_dist && Math.abs(drop_heading) < approach_angle);
        withinTurnLimit = onApproach && Math.abs(drop_heading)/drop_time < approach_turn_limit;

        return new double[] {p_pos_x, p_pos_y};
    }

    public static double[] getDrag(double lat, double lon, double pos_z, double hit_lat, double hit_lon, double hit_z, double vel_s, double vel_t, double vel_z, double wind_s, double wind_t, double fall_time, double threshold) {
        DropAlgorithm drop = new DropAlgorithm();
        drop.setPosition(lat, lon, pos_z);
        drop.setTarget(hit_lat, hit_lon, hit_z);
        drop.setSpeed(vel_s, vel_t, vel_z);
        drop.setWind(wind_s, wind_t);

        double drag[] = {0, 0, 0};
        double delta = 1;
        double last;

        do {
            do {
                drag[2] += delta;
                drop.setDrag(drag[0], drag[1], drag[2]);
                drop.simulateDrop();
            } while (drop.fall_time < fall_time);

            drag[2] -= delta;
            delta /= 2;
        } while(Math.abs(fall_time - drop.fall_time) > threshold);
        drag[2] += 2 * delta;

        delta = 1;
        do {
            do {
                last = drop.drop_dist;
                drag[1] += delta;
                drop.setDrag(drag[0], drag[1], drag[2]);
                drop.simulateDrop();
            } while (last - drop.drop_dist > 0);

            drag[1] -= delta;
            delta /= 2;
        } while(last - drop.drop_dist > threshold);
        drag[1] += 2 * delta;

        delta = 1;
        do {
            do {
                last = drop.drop_dist;
                drag[0] += delta;
                drop.setDrag(drag[0], drag[1], drag[2]);
                drop.simulateDrop();
            } while (last - drop.drop_dist > 0);

            drag[0] -= delta;
            delta /= 2;
        } while(last - drop.drop_dist > threshold);
        drag[0] += 2 * delta;

        System.out.println(drop.drop_dist);

        return drag;
    }

    public void calculateWindSpeed(double airSpeed, double trueHeading){
        double cos = Math.cos(Math.toRadians(trueHeading));
        double sin = Math.sin(Math.toRadians(trueHeading));

        double v1 = vel_y*cos + vel_x*sin - airSpeed;
        double v2 = vel_x*cos + vel_y*sin;

        wind_x = v1*sin + v2*cos;
        wind_y = v1*cos - v2*sin;



    }


}

