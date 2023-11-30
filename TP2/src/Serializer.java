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






}
