package com.example.androidthings.gattserver;

import java.util.UUID;

public class OpticalSensor extends Sensor {

    /* Optical Sensor Service UUID */
    public static UUID OPTICAL_SERVICE = UUID.fromString("f000aa70-0451-4000-b000-000000000000");
    /* Optical Sensor Data */
    public static UUID OPTICAL_DATA    = UUID.fromString("f000aa71-0451-4000-b000-000000000000");
    /* Optical Sensor Configuration */
    public static UUID OPTICAL_CONF = UUID.fromString("f000aa72-0451-4000-b000-000000000000");
    /* Optical Sensor refresh periode */
    public static UUID OPTICAL_REFRESH = UUID.fromString("f000aa73-0451-4000-b000-000000000000");

    public static final short lux(byte[] value) {
        short e, m, rawData;

        rawData = (short) (value[1] & 0x00FF);
        rawData <<= 8;
        rawData = (short) (rawData | (value[0] & 0x00FF));

        m = (short) (rawData & 0x0FFF);
        e = (short) ((rawData & 0xF000) >> 12);

        e = (short) ((e == 0) ? 1 : 2 << (e - 1));

        return (short) (m * (0.01 * e));
    }
}
