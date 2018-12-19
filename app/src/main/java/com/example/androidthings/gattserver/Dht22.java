package com.example.androidthings.gattserver;

public class Dht22 {

    private static final String TAG = Dht22.class.getSimpleName();

    public static final double temp(byte[] value) {
        short rawTemp;
        double temp;

        rawTemp = (short) (value[2] & 0x00FF);
        rawTemp <<= 8;
        rawTemp = (short) (rawTemp | (value[3] & 0x00FF));

        // degrees centigrade
        temp = (double) rawTemp / 10;

        return temp;
    }

    public static final double humidity(byte[] value) {
        short rawHmdt;
        double hmdt;

        rawHmdt = (short) (value[0] & 0x00FF);
        rawHmdt <<= 8;
        rawHmdt = (short) (rawHmdt | (value[1] & 0x00FF));

        // relative humidity [%RH]
        hmdt = (double) rawHmdt / 10;

        return hmdt;
    }

    /**
     * Check DHT22 checksum which is equal to sum of first for bytes.
     *
     * @param value 5 byte array
     * @return true if checksum is ok.
     */
    public static boolean chechSum(byte[] value) {

        if (value.length != 5) {
            return false;
        }
        short sum = 0;
        for (int i = 0; i < 4; i++ ) {
            sum += 0xff & value[i];
        }
        sum = (short) (0xff & sum);
        if ((byte) (sum) != value[4]) {
            return false;
        }

        return true;
    }

}
