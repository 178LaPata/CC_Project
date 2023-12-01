public class Serializer {

    public static byte[] intToTwoBytes(int value) {

        byte[] byteArray = new byte[2];
        // Using bitwise operations to extract individual bytes
        byteArray[0] = (byte) (value >> 8); // Most significant byte
        byteArray[1] = (byte) value;        // Least significant byte

        return byteArray;
    }



    public static byte[] intToFourBytes(int value) {

        byte[] byteArray = new byte[4];

        // Using bitwise operations to extract individual bytes
        byteArray[0] = (byte) (value >> 24); // Most significant byte
        byteArray[1] = (byte) (value >> 16);
        byteArray[2] = (byte) (value >> 8);
        byteArray[3] = (byte) value;          // Least significant byte

        return byteArray;
    }

    public static int twoBytesToInt(byte[] byteArray){

        /*
        // Making sure the byte array has at least 2 elements
        if (byteArray.length < 2) {
            throw new IllegalArgumentException("Byte array must have at least 2 elements");
        }

         */

        // Combining the bytes using bitwise operations
        return ((byteArray[0] & 0xFF) << 8) | (byteArray[1] & 0xFF);
    }


    public static int fourBytesToInt(byte[] byteArray) {
        /*
        // Making sure the byte array has at least 4 elements
        if (byteArray.length < 4) {
            throw new IllegalArgumentException("Byte array must have at least 4 elements");
        }

         */

        // Combining the bytes using bitwise operations
        int intValue = (byteArray[0] & 0xFF) << 24 |
                (byteArray[1] & 0xFF) << 16 |
                (byteArray[2] & 0xFF) << 8 |
                (byteArray[3] & 0xFF);

        return intValue;
    }






}
