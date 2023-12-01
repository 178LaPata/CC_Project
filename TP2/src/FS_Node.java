import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FS_Node {

    public static void main(String[] args) throws IOException {

        try {
            String server_ip = args[1];
            int server_port = Integer.parseInt(args[2]);

            Socket socket = new Socket(server_ip, server_port);

            System.out.println("Conexão FS Track Protocol com servidor " + server_ip + " porta " + server_port);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());


            List<Integer> fds = new ArrayList<>();


            byte[] register_message = TPManager.registerMessage(args[0]);

            //System.out.println(new String(Requests.create_request(fds),StandardCharsets.UTF_8));

            if (register_message != null) {
                out.write(register_message);
                out.flush();
            }


            BufferedReader inStdin = new BufferedReader(new InputStreamReader(System.in));



            boolean loop = true;

            while(loop){

                String input = inStdin.readLine();

                String[] option = input.split("\\s+");

                switch (option[0]){

                    case "GET":{
                        out.write(TPManager.getFileMessage(option[1]));
                        out.flush();

                        byte[] nodosTotaisBytes = new byte[2];
                        in.readFully(nodosTotaisBytes,0,2);
                        int nodosTotais = Serializer.twoBytesToInt(nodosTotaisBytes);

                        if (nodosTotais == 0){
                            System.out.println("Ficheiro não existe!");
                            break;
                        }

                        byte[] blocosTotaisBytes = new byte[4];
                        in.readFully(blocosTotaisBytes,0,4);
                        int blocosTotais = Serializer.fourBytesToInt(blocosTotaisBytes);

                        for (int i = 0; i<nodosTotais; i++){

                            byte[] ipBytes = new byte[4];
                            in.readFully(ipBytes,0,4);
                            String ip = InetAddress.getByAddress(ipBytes).getHostAddress();

                            System.out.println(ip);

                            byte[] qtBlocosDisponiveisBytes = new byte[4];
                            in.readFully(qtBlocosDisponiveisBytes,0,4);
                            int qtBlocosDisponiveis = Serializer.fourBytesToInt(qtBlocosDisponiveisBytes);

                            if (qtBlocosDisponiveis == 0){
                                System.out.println("tem todos os blocos");
                            }
                            else {
                                for (int b = 0; i<qtBlocosDisponiveis; i++){
                                    byte[] blocoIDBytes = new byte[4];
                                    in.readFully(blocoIDBytes,0,4);
                                    int bloco = Serializer.fourBytesToInt(blocoIDBytes);
                                    System.out.println("tem o bloco " + bloco);
                                }

                            }

                        }



                        break;
                    }

                    case "QUIT":{
                        out.writeByte(0);
                        out.flush();
                        loop = false;
                        break;
                    }

                    case "FILES":{
                        out.writeByte(3);
                        out.flush();
                        byte[] numberFiles_bytes = new byte[2];
                        in.readFully(numberFiles_bytes,0,2);
                        int numberFiles = Serializer.twoBytesToInt(numberFiles_bytes);
                        if (numberFiles == 0){
                            System.out.println("Não existem ficheiros disponíveis para transferência");
                            break;
                        }
                        List<String> namesList = new ArrayList<>();
                        for (int i = 0; i<numberFiles; i++){
                            int nameSize = in.readByte();
                            byte[] name_byte = new byte[nameSize];
                            in.readFully(name_byte,0,nameSize);
                            String name = new String(name_byte, StandardCharsets.UTF_8);
                            namesList.add(name);
                        }
                        System.out.println(namesList);
                        break;
                    }

                    /*
                    case "GET":{


                    }


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
                            
                            Map<String,FileInfo> fileInfoMap = new HashMap<>();


                            for (int i = 0; i<size; i++){

                                int length = in.readInt();

                                byte[] received = new byte[length];
                                in.readFully(received,0,length);
                                FileInfo fileInfo = FileInfo.deserialize(received);

                                fileInfoMap.put(in.readUTF(),fileInfo);
                            }
                            
                            System.out.println(fileInfoMap);


                        }


                        break;

                    }



                    case "FILES":{

                        out.writeInt(1);
                        out.writeByte(2);
                        out.flush();

                        int length = in.readInt();

                        byte[] received = new byte[length];
                        in.readFully(received,0,length);

                        String answer = new String(received, StandardCharsets.UTF_8);

                        System.out.println(answer);

                        break;
                    }

                    case "QUIT":{
                        out.writeInt(1);
                        out.writeByte(0);
                        out.flush();
                        loop = false;

                        break;
                    }

                     */

                }




            }




            System.out.println("Terminando Programa...");

            socket.shutdownInput();
            socket.shutdownOutput();
            socket.close();
        }
            catch (ConnectException e) {
            System.err.println("Conexão ao servidor falhada!");
        }
            catch (IOException e) {
            e.printStackTrace();


        }

    }

}