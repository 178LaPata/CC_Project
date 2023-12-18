import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


public class TPManager {

    private static final int chunkSize = 500;


    public static byte[] registerMessage(File[] listOfFiles) throws IOException, NoSuchAlgorithmException {


        List<byte[]> fileInfos = new ArrayList<>();
        int sizeFileInfo = 0;

        if (listOfFiles != null) {

            for (File file : listOfFiles) {

                if (file.isFile()) {

                    long file_size = file.length();

                    int block_amount = (int) Math.ceil((double) file_size / chunkSize);

                    FileInfo fileInfo = new FileInfo(file.getName(), block_amount);

                    byte[] fileInfoBytes = fileInfo.fileInfoToBytes();

                    byte[] sha1Encoding = calculateSHA1(file);

                    ByteBuffer byteBuffer = ByteBuffer.allocate(fileInfoBytes.length + sha1Encoding.length);

                    byteBuffer.put(fileInfoBytes);
                    byteBuffer.put(sha1Encoding);

                    byte[] info = byteBuffer.array();

                    sizeFileInfo += info.length;

                    fileInfos.add(info);
                }

            }
        } else return null;

        ByteBuffer messageBuffer = ByteBuffer.allocate(1 + 1 + sizeFileInfo);

        byte choice = 1;

        messageBuffer.put(choice);
        messageBuffer.put((byte) fileInfos.size());

        for (byte[] fileInfo : fileInfos) {
            messageBuffer.put(fileInfo);
        }


        return messageBuffer.array();
    }


    public static byte[] filesAvailableMessage(Set<String> fileNames) {
        List<byte[]> fileNameBytesList = new ArrayList<>();
        int sizeFileNames = 0;
        for (String fileName : fileNames) {
            ByteBuffer fileNameBuffer = ByteBuffer.allocate(1 + fileName.length());
            fileNameBuffer.put((byte) fileName.length());
            fileNameBuffer.put(fileName.getBytes());
            byte[] fileNameBytes = fileNameBuffer.array();
            fileNameBytesList.add(fileNameBytes);
            sizeFileNames += fileNameBytes.length;
        }
        byte[] numberOfFiles = Serializer.intToTwoBytes(fileNames.size());
        ByteBuffer messageBuffer = ByteBuffer.allocate(2 + sizeFileNames);
        messageBuffer.put(numberOfFiles);
        for (byte[] fileNameBytes : fileNameBytesList) {
            messageBuffer.put(fileNameBytes);
        }
        return messageBuffer.array();
    }


    public static byte[] getFileMessage(String name) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 1 + name.length());
        byteBuffer.put((byte) 4);
        byteBuffer.put((byte) name.length());
        byteBuffer.put(name.getBytes());
        return byteBuffer.array();
    }


    public static byte[] updateMessage(String name, int blockNumber) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 1 + name.length() + 4);
        byteBuffer.put((byte) 2);
        byteBuffer.put((byte) name.length());
        byteBuffer.put(name.getBytes());
        byteBuffer.put(Serializer.intToFourBytes(blockNumber));
        return byteBuffer.array();
    }


    //the message should have byte 8 at the start and then the ip address of the node
    public static byte[] checkNodeMessage(String ip) throws UnknownHostException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1 + ip.length());
        byteBuffer.put((byte) 8);
        byteBuffer.put(InetAddress.getByName(ip).getAddress());
        return byteBuffer.array();
    }


    private static byte[] calculateSHA1(File file) throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        int sizeEncoding = 0;
        List<byte[]> hashesList = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;


            while ((bytesRead = fis.read(buffer)) != -1) {

                System.out.println(Arrays.toString(buffer));

                digest.update(buffer, 0, bytesRead);
                hashesList.add(digest.digest());
                sizeEncoding += 20;
            }
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(sizeEncoding);
        for (byte[] hash : hashesList) {
            byteBuffer.put(hash);
        }

        return byteBuffer.array();
    }


}
