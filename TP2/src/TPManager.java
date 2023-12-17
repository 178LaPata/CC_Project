import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class TPManager {

    public static byte[] registerMessage(File[] listOfFiles) throws IOException, NoSuchAlgorithmException {



        List<byte[]> fileInfos = new ArrayList<>();
        int size_fileInfos = 0;

        if (listOfFiles != null) {

            //byte[] ip = InetAddress.getLocalHost().toString().getBytes();

            for (int i = 0; i < listOfFiles.length; i++) {

                if (listOfFiles[i].isFile()) {

                    File file = listOfFiles[i];

                    long file_size = file.length();

                    int block_amount = (int) Math.ceil((double) file_size / 500);

                    FileInfo fileInfo = new FileInfo(file.getName(), block_amount);

                    System.out.println(fileInfo);

                    byte[] bytes_fileInfo = fileInfo.fileInfoToBytes();

                    byte[] sha1Encoding = calculateSHA1(file,500);

                    ByteBuffer byteBuffer = ByteBuffer.allocate(bytes_fileInfo.length+sha1Encoding.length);

                    byteBuffer.put(bytes_fileInfo);
                    byteBuffer.put(sha1Encoding);

                    byte[] bytes = byteBuffer.array();

                    size_fileInfos += bytes.length;

                    fileInfos.add(bytes);
                }

            }
                /*
                else if (listOfFiles[i].isDirectory()) {
                    System.out.println("Directory " + listOfFiles[i].getName());
                }
                 */
        } else return null;

        ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 1 + size_fileInfos);

        byte choice = 1;

        byteBuffer.put(choice);
        byteBuffer.put((byte) fileInfos.size());

        for (byte[] bytes : fileInfos) {
            byteBuffer.put(bytes);
        }

        System.out.println(Arrays.toString(byteBuffer.array()));

        return byteBuffer.array();
    }


    public static byte[] filesAvailableMessage(Set<String> fileNames) {
        byte[] numberOfFiles = Serializer.intToTwoBytes(fileNames.size());
        List<byte[]> name_bytes = new ArrayList<>();
        int sizeFileNames = 0;
        for (String s : fileNames) {
            ByteBuffer buffer = ByteBuffer.allocate(1+s.length());
            buffer.put((byte)s.length());
            buffer.put(s.getBytes());
            byte[] fileName = buffer.array();
            name_bytes.add(fileName);
            sizeFileNames += fileName.length;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(2 + sizeFileNames);
        byteBuffer.put(numberOfFiles);
        for (byte[] bytes : name_bytes) {
            byteBuffer.put(bytes);
        }
        return byteBuffer.array();
    }


    public static byte[] getFileMessage(String name){
        ByteBuffer byteBuffer = ByteBuffer.allocate(1+1+name.length());
        byteBuffer.put((byte)4);
        byteBuffer.put((byte)name.length());
        byteBuffer.put(name.getBytes());
        return byteBuffer.array();
    }


    public static byte[] updateMessage(String name, int blockNumber){
        ByteBuffer byteBuffer = ByteBuffer.allocate(1+1+name.length()+4);
        byteBuffer.put((byte)2);
        byteBuffer.put((byte)name.length());
        byteBuffer.put(name.getBytes());
        byteBuffer.put(Serializer.intToFourBytes(blockNumber));
        return byteBuffer.array();
    }






    private static byte[] calculateSHA1(File file, int chunkSize) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        int sizeEncoding = 0;
        List<byte[]> listSHA1 = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;


            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                listSHA1.add(digest.digest());
                sizeEncoding += 20;
            }
        }


        ByteBuffer byteBuffer = ByteBuffer.allocate(sizeEncoding);
        for (byte[] bytes : listSHA1) {
            byteBuffer.put(bytes);
            System.out.println(Arrays.toString(bytes));
        }


        return byteBuffer.array();
    }


    //the message should have byte 8 at the start and then the ip address of the node
    public static byte[] checkNodeMessage(String ip) throws UnknownHostException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1+ip.length());
        byteBuffer.put((byte) 8);
        byteBuffer.put(InetAddress.getByName(ip).getAddress());
        return byteBuffer.array();
    }






}



        /*
        case "GET": {

            byte choice = 3;

            byte[] combined = new byte[option[1].length() + 1];

            ByteBuffer buffer_message = ByteBuffer.wrap(combined);
            buffer_message.put(choice);
            buffer_message.put(option[1].getBytes());
            byte[] message = buffer_message.array();
            out.writeInt(message.length);
            out.write(message);
            out.flush();

            int size = in.readInt();


            if (size != 0) {

                Map<String, FileInfo> fileInfoMap = new HashMap<>();


                for (int i = 0; i < size; i++) {

                    int length = in.readInt();

                    byte[] received = new byte[length];
                    in.readFully(received, 0, length);
                    FileInfo fileInfo = FileInfo.deserialize(received);

                    fileInfoMap.put(in.readUTF(), fileInfo);
                }

                System.out.println(fileInfoMap);


            }


            break;

        }


        case "FILES": {

            out.writeInt(1);
            out.writeByte(2);
            out.flush();

            int length = in.readInt();

            byte[] received = new byte[length];
            in.readFully(received, 0, length);

            String answer = new String(received, StandardCharsets.UTF_8);

            System.out.println(answer);

            break;
        }

        case "QUIT": {
            out.writeInt(1);
            out.writeByte(0);
            out.flush();
            loop = false;

            break;
        }

    }


}




    }

public byte[]serialize(String input)throws IOException{

        String buffer;

        if(blocos_disponiveis!=null){
        buffer=this.nome+" "+this.blocos_quantidade;
        for(int i:blocos_disponiveis){
        buffer=buffer+" "+i;
        }
        }
        else
        buffer=this.nome+" "+this.blocos_quantidade;

        /*
        byte[] nome = this.nome.getBytes();

        byte[] blocos_quantidade = new byte[]{
                (byte) (this.blocos_quantidade >>> 24),
                (byte) (this.blocos_quantidade >>> 16),
                (byte) (this.blocos_quantidade >>> 8),
                (byte) this.blocos_quantidade
        };


         */
        /*
        ByteBuffer buffer = ByteBuffer.allocate(this.blocos_disponiveis.size());
        for (int i : blocos_disponiveis) {
            buffer.putInt(i);
        }
        byte[] blocos_disponiveis = buffer.array();

         */

/*
        byte[] combined = new byte[nome.length + blocos_quantidade.length];

        ByteBuffer info = ByteBuffer.wrap(combined);

        info.put(nome);
        info.put(blocos_quantidade);


        //info.put(blocos_disponiveis);

        return buffer.getBytes();
        }

public static FileInfo deserialize(byte[]info){

        String fds=new String(info,StandardCharsets.UTF_8).substring(1);

        String[]splited=fds.split("\\s+");

        String nome=splited[0];
        int blocos_quantidade=Integer.parseInt(splited[1]);
        List<Integer> blocos_disponiveis=new ArrayList<>();


        if(splited.length==2){
        for(int i=0;i<blocos_quantidade; i++){
        blocos_disponiveis.add(i);
        }
        }
        else{
        for(int i=2;i<splited.length;i++){
        blocos_disponiveis.add(Integer.parseInt(splited[i]));
        }
        }

        FileInfo fileInfo=new FileInfo(nome,blocos_quantidade,blocos_disponiveis);

        System.out.println(fileInfo);


        return fileInfo;
        }


        }

        */
