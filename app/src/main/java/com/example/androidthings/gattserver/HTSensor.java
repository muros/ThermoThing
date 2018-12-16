package com.example.androidthings.gattserver;

import java.util.UUID;

public class HTSensor extends Sensor {

    /* Humidity Temperature Sensor Service UUID */
    public static UUID HT_SERVICE = UUID.fromString("f000aa20-0451-4000-b000-000000000000");
    /* Humidity Temperature Sensor Data */
    public static UUID HT_DATA    = UUID.fromString("f000aa21-0451-4000-b000-000000000000");
    /* Humidity Temperature Sensor Configuration */
    public static UUID HT_CONF = UUID.fromString("f000aa22-0451-4000-b000-000000000000");
    /* Humidity Temperature Sensor refresh periode */
    public static UUID HT_REFRESH = UUID.fromString("f000aa23-0451-4000-b000-000000000000");

    public static final double temp(byte[] value) {
        short rawTemp;
        double temp;

        rawTemp = (short) (value[1] & 0x00FF);
        rawTemp <<= 8;
        rawTemp = (short) (rawTemp | (value[0] & 0x00FF));

        // degrees centigrade
        temp = ((double)rawTemp / 65536)*165 - 40;

        return temp;
    }

    public static final double humidity(byte[] value) {
        short rawHmdt;
        double hmdt;

        rawHmdt = (short) (value[1] & 0x00FF);
        rawHmdt <<= 8;
        rawHmdt = (short) (rawHmdt | (value[0] & 0x00FF));

        // relative humidity [%RH]
        rawHmdt &= ~0x0003; // remove status bits
        hmdt = ((double)rawHmdt / 65536) * 100;

        return hmdt;
    }
}
