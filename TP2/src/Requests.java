import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Requests {

    public static byte[] create_request(String[] input) {

        switch (input[0]) {

            case "REGISTER": {

                // Ler file names no folder escolhido para partilhar com tracker
                File folder = new File(input[1]);
                File[] listOfFiles = folder.listFiles();

                StringBuilder files_builder = new StringBuilder();

                if (listOfFiles != null) {

                    //byte[] ip = InetAddress.getLocalHost().toString().getBytes();

                    for (int i = 0; i < listOfFiles.length; i++) {

                        if (listOfFiles[i].isFile()) {

                            File file = listOfFiles[i];

                            long file_size = file.length();

                            int block_amount = (int) Math.ceil((double) file_size / 500);

                            FileInfo fileInfo = new FileInfo(file.getName(), block_amount);

                            files_builder.append(fileInfo);
                            files_builder.append(" ");
                        }

                    }
                /*
                else if (listOfFiles[i].isDirectory()) {
                    System.out.println("Directory " + listOfFiles[i].getName());
                }
                 */
                }
                else return null;

                String files_string = files_builder.deleteCharAt(files_builder.length()-1).toString();
                int files_string_size = files_string.getBytes().length;

                byte[] message = new byte[files_string_size+3];

                message[0] = 1;

                byte[] files_string_size_bytes = Serializer.intToTwoBytes(files_string_size);

                message[1] = files_string_size_bytes[0];
                message[2] = files_string_size_bytes[1];

                int i = 3;
                for (byte fileInfo : files_string.getBytes()){
                    message[i] = fileInfo;
                    i++;
                }

                return message;
            }

        }


        return new byte[0];
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
